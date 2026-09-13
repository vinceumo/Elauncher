package app.elauncher.data

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle

sealed class WidgetPickerItem {
    data class AppHeader(
        val appLabel: String,
        val appIcon: Drawable,
        val profile: UserHandle,
        val packageName: String,
    ) : WidgetPickerItem()

    data class WidgetEntry(
        val providerInfo: AppWidgetProviderInfo,
        val profile: UserHandle,
        val label: String,
        val packageName: String,
    ) : WidgetPickerItem()

    data class PrivateSpaceHeader(
        val isLocked: Boolean,
    ) : WidgetPickerItem()
}
