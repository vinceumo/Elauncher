package app.elauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.elauncher.data.WidgetPickerItem
import app.elauncher.databinding.ItemWidgetPickerEntryBinding
import app.elauncher.databinding.ItemWidgetPickerHeaderBinding
import app.elauncher.databinding.ItemWidgetPickerPrivateHeaderBinding
import app.elauncher.helper.FontManager
import java.text.Normalizer

/**
 * Renders the flattened, already-sorted [WidgetPickerItem] rows built by
 * `WidgetProviders.buildWidgetPickerItems()` - grouping/ordering is that builder's job, this
 * adapter just draws the three row shapes and (during search) keeps groups consistent: an
 * [WidgetPickerItem.AppHeader] survives filtering only if at least one of its own entries does.
 */
class WidgetPickerAdapter(
    private val onWidgetSelected: (AppWidgetProviderInfo, UserHandle) -> Unit,
) : ListAdapter<WidgetPickerItem, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ENTRY = 1
        const val VIEW_TYPE_PRIVATE_HEADER = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WidgetPickerItem>() {
            override fun areItemsTheSame(oldItem: WidgetPickerItem, newItem: WidgetPickerItem): Boolean = when {
                oldItem is WidgetPickerItem.AppHeader && newItem is WidgetPickerItem.AppHeader ->
                    oldItem.packageName == newItem.packageName && oldItem.profile == newItem.profile

                oldItem is WidgetPickerItem.WidgetEntry && newItem is WidgetPickerItem.WidgetEntry ->
                    oldItem.providerInfo.provider == newItem.providerInfo.provider && oldItem.profile == newItem.profile

                oldItem is WidgetPickerItem.PrivateSpaceHeader && newItem is WidgetPickerItem.PrivateSpaceHeader -> true

                else -> false
            }

            override fun areContentsTheSame(oldItem: WidgetPickerItem, newItem: WidgetPickerItem): Boolean =
                oldItem == newItem
        }
    }

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")
    private val widgetFilter = createWidgetFilter()

    private var fullList: MutableList<WidgetPickerItem> = mutableListOf()
    private var filteredList: MutableList<WidgetPickerItem> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (filteredList.getOrNull(position)) {
            is WidgetPickerItem.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is WidgetPickerItem.AppHeader -> VIEW_TYPE_HEADER
            else -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemWidgetPickerHeaderBinding.inflate(inflater, parent, false)
            )

            VIEW_TYPE_PRIVATE_HEADER -> PrivateHeaderViewHolder(
                ItemWidgetPickerPrivateHeaderBinding.inflate(inflater, parent, false)
            )

            else -> EntryViewHolder(
                ItemWidgetPickerEntryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            if (filteredList.isEmpty() || position == RecyclerView.NO_POSITION) return
            // Rows are inflated on demand while scrolling, long after any fragment/activity-level
            // custom typeface walk ran, so each row applies it here. No-op without a custom font.
            FontManager.applyCustomTypeface(holder.itemView)
            when (val item = filteredList[holder.bindingAdapterPosition]) {
                is WidgetPickerItem.AppHeader ->
                    (holder as? HeaderViewHolder)?.bind(item)

                is WidgetPickerItem.WidgetEntry ->
                    (holder as? EntryViewHolder)?.bind(item, onWidgetSelected)

                is WidgetPickerItem.PrivateSpaceHeader ->
                    (holder as? PrivateHeaderViewHolder)?.bind(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = widgetFilter

    private fun createWidgetFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                val results = if (charSearch.isNullOrBlank()) fullList else filterItems(charSearch)
                val filterResults = FilterResults()
                filterResults.values = results
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    filteredList = it as MutableList<WidgetPickerItem>
                    submitList(filteredList)
                }
            }
        }
    }

    /**
     * Filters the grouped structure rather than each row independently: a [WidgetPickerItem.WidgetEntry]
     * survives if its own label or its owning header's label matches; an [WidgetPickerItem.AppHeader]
     * survives only if at least one of its own entries does (see [appendMatchingGroup]); the
     * [WidgetPickerItem.PrivateSpaceHeader] banner is dropped outright while locked (mirroring
     * [AppDrawerAdapter]'s blanket header drop during search), or kept, unlocked, only if at least
     * one entry in the private-space groups beneath it survives.
     */
    private fun filterItems(query: CharSequence): MutableList<WidgetPickerItem> {
        val filtered = mutableListOf<WidgetPickerItem>()
        var index = 0
        while (index < fullList.size) {
            when (val item = fullList[index]) {
                is WidgetPickerItem.AppHeader -> {
                    index = appendMatchingGroup(item, index, query, filtered)
                }

                is WidgetPickerItem.PrivateSpaceHeader -> {
                    index++
                    if (item.isLocked) continue // Locked: drop the banner; nothing else to skip.

                    // Unlocked: gather every AppHeader group in this section into a scratch buffer
                    // first, so the banner itself is only kept if something beneath it survived.
                    val sectionBuffer = mutableListOf<WidgetPickerItem>()
                    while (index < fullList.size && fullList[index] is WidgetPickerItem.AppHeader) {
                        index = appendMatchingGroup(
                            fullList[index] as WidgetPickerItem.AppHeader,
                            index,
                            query,
                            sectionBuffer,
                        )
                    }
                    if (sectionBuffer.isNotEmpty()) {
                        filtered.add(item)
                        filtered.addAll(sectionBuffer)
                    }
                }

                // An entry with no preceding header shouldn't occur given the builder's flattened
                // format, but skip it defensively rather than rendering an unowned row.
                is WidgetPickerItem.WidgetEntry -> index++
            }
        }
        return filtered
    }

    /**
     * Appends [header] plus the [WidgetPickerItem.WidgetEntry] rows that immediately follow it
     * (up to the next header-like item) to [into], keeping only entries whose own label or
     * [header]'s label matches [query] - and keeping the header at all only if at least one did.
     * Returns the index of the first item past this group.
     */
    private fun appendMatchingGroup(
        header: WidgetPickerItem.AppHeader,
        headerIndex: Int,
        query: CharSequence,
        into: MutableList<WidgetPickerItem>,
    ): Int {
        var i = headerIndex + 1
        val matches = mutableListOf<WidgetPickerItem.WidgetEntry>()
        while (i < fullList.size) {
            val entry = fullList[i] as? WidgetPickerItem.WidgetEntry ?: break
            if (appLabelMatches(entry.label, query) || appLabelMatches(header.appLabel, query)) {
                matches.add(entry)
            }
            i++
        }
        if (matches.isNotEmpty()) {
            into.add(header)
            into.addAll(matches)
        }
        return i
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        if (appLabel.contains(charSearch.trim(), true)) return true
        val query = charSearch.normalizeForSearch()
        return query.isNotEmpty() && appLabel.normalizeForSearch().contains(query, true)
    }

    private fun CharSequence.normalizeForSearch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")

    /** Stores the unfiltered, already-grouped-and-sorted row list and displays it as-is. */
    fun submitFullList(items: List<WidgetPickerItem>) {
        fullList = items.toMutableList()
        filteredList = fullList
        submitList(fullList)
    }

    class HeaderViewHolder(private val binding: ItemWidgetPickerHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: WidgetPickerItem.AppHeader) = with(binding) {
            appIcon.setImageDrawable(header.appIcon)
            appLabel.text = header.appLabel
        }
    }

    class EntryViewHolder(private val binding: ItemWidgetPickerEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            entry: WidgetPickerItem.WidgetEntry,
            onWidgetSelected: (AppWidgetProviderInfo, UserHandle) -> Unit,
        ) = with(binding) {
            val context = root.context
            val preview = runCatching { entry.providerInfo.loadPreviewImage(context, 0) }.getOrNull()
                ?: runCatching { entry.providerInfo.loadIcon(context, 0) }.getOrNull()
            if (preview != null) {
                widgetPreview.setImageDrawable(preview)
            } else {
                widgetPreview.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            widgetLabel.text = entry.label
            root.setOnClickListener { onWidgetSelected(entry.providerInfo, entry.profile) }
        }
    }

    class PrivateHeaderViewHolder(private val binding: ItemWidgetPickerPrivateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: WidgetPickerItem.PrivateSpaceHeader) = with(binding) {
            privateSpaceLockIndicator.isVisible = header.isLocked
        }
    }
}
