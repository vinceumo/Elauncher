package app.elauncher.helper

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import app.elauncher.data.WidgetPickerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.CollationKey
import java.text.Collator

/**
 * One app's header plus its widgets, kept together as a single unit while the cross-profile sort
 * in [buildGroupsForProfiles] runs - see [buildWidgetPickerItems]'s doc for why the sort must
 * operate on whole groups like this rather than per-row.
 */
private data class WidgetProviderGroup(
    val key: CollationKey,
    val header: WidgetPickerItem.AppHeader,
    val entries: List<WidgetPickerItem.WidgetEntry>,
)

/**
 * Builds the flat, adapter-ready row list for the in-app widget picker: every installed widget
 * provider across the primary profile, Work profile(s), and (if unlocked) Private Space, grouped
 * by owning app and sorted alphabetically by app label.
 *
 * Mirrors [getAppsList]'s shape (IO dispatcher, [Collator]-based sort, defensive try/catch per
 * section). The one subtlety: every non-private-space profile's groups are combined into a single
 * list *before* the sort runs (see [buildGroupsForProfiles]), so a Work-profile app's group lands
 * alphabetically among primary-profile groups rather than after all of them as one contiguous
 * block. Private Space is handled separately, after the main list is flattened, since it can never
 * interleave with anything (it's shown as its own section behind a lock header).
 */
suspend fun buildWidgetPickerItems(context: Context): List<WidgetPickerItem> {
    return withContext(Dispatchers.IO) {
        val items = mutableListOf<WidgetPickerItem>()
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val collator = Collator.getInstance()

        try {
            val mainProfiles = userManager.userProfiles.filterNot { isPrivateSpaceProfile(context, it) }
            items.addAll(flatten(buildGroupsForProfiles(context, mainProfiles, collator)))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val privateSpaceHandle = getPrivateSpaceUserHandle(context)
            if (privateSpaceHandle != null) {
                val locked = isPrivateSpaceLocked(context, privateSpaceHandle)
                items.add(WidgetPickerItem.PrivateSpaceHeader(isLocked = locked))
                if (!locked) {
                    items.addAll(flatten(buildGroupsForProfiles(context, listOf(privateSpaceHandle), collator)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }
}

/**
 * Builds one app-group per (profile, package) pair across [profiles], collecting every profile's
 * groups into a single list and sorting that combined list exactly once at the end - the step
 * that produces true cross-profile alphabetical interleaving. A package can legitimately appear
 * twice (once per profile) as two separate groups; these are never merged across profiles.
 */
private fun buildGroupsForProfiles(
    context: Context,
    profiles: List<UserHandle>,
    collator: Collator,
): List<WidgetProviderGroup> {
    val groups = mutableListOf<WidgetProviderGroup>()

    for (profile in profiles) {
        val providers = try {
            WidgetHostManager.installedProvidersForProfile(context, profile)
        } catch (e: Exception) {
            e.printStackTrace()
            continue
        }

        val byPackage = providers.groupBy { it.provider.packageName }
        for ((packageName, providersForPackage) in byPackage) {
            val group = buildGroup(context, profile, packageName, providersForPackage, collator) ?: continue
            groups.add(group)
        }
    }

    // Single sort pass over every group from every profile in `profiles` - this is what produces
    // alphabetical interleaving across profiles rather than one contiguous block per profile.
    return groups.sortedWith(compareBy { it.key })
}

/** Resolves one (profile, package) group's header/icon and sorts its widget entries by label. */
private fun buildGroup(
    context: Context,
    profile: UserHandle,
    packageName: String,
    providers: List<AppWidgetProviderInfo>,
    collator: Collator,
): WidgetProviderGroup? {
    val packageManager = context.packageManager
    val applicationInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }
        .getOrNull() ?: return null

    val appLabel = runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
        .getOrDefault(packageName)
    val rawIcon = runCatching { packageManager.getApplicationIcon(applicationInfo) }.getOrNull()
        ?: return null
    val appIcon = if (profile != android.os.Process.myUserHandle()) {
        runCatching { packageManager.getUserBadgedIcon(rawIcon, profile) }.getOrDefault(rawIcon)
    } else {
        rawIcon
    }

    val entries = providers.map { providerInfo ->
        val label = runCatching { providerInfo.loadLabel(packageManager) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: appLabel
        WidgetPickerItem.WidgetEntry(
            providerInfo = providerInfo,
            profile = profile,
            label = label,
            packageName = packageName,
        )
    }.sortedWith(compareBy(collator) { it.label })

    return WidgetProviderGroup(
        key = collator.getCollationKey(appLabel),
        header = WidgetPickerItem.AppHeader(
            appLabel = appLabel,
            appIcon = appIcon,
            profile = profile,
            packageName = packageName,
        ),
        entries = entries,
    )
}

private fun flatten(groups: List<WidgetProviderGroup>): List<WidgetPickerItem> =
    groups.flatMap { group -> listOf(group.header) + group.entries }
