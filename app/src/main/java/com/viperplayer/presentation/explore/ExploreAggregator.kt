package com.viperplayer.presentation.explore

import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.SectionFilter
import com.viperplayer.domain.repository.PluginRepository

/**
 * A discovery ("Explore") surface assembled from the discovery data every enabled plugin exposes:
 * its home feed sections ([HomeContent.sections]) and its browse categories ([BrowseCategory]). The
 * section filter chips a plugin attaches to a [CarouselSection] are surfaced here as a mood / genre
 * chip rail.
 */
data class ExploreContent(
    /** Mood / genre chips gathered from plugin section filters, deduped by label. */
    val moods: List<ExploreMood> = emptyList(),
    /** Browse categories exposed by plugins (e.g. charts / new-release entry points). */
    val categories: List<BrowseCategory> = emptyList(),
    /** Carousels of albums / playlists / artists / songs, one per plugin home section. */
    val sections: List<ExploreSection> = emptyList(),
) {
    val isEmpty: Boolean
        get() = moods.isEmpty() && categories.isEmpty() && sections.isEmpty()
}

/** A single discovery carousel: a plugin home section reduced to its title + items + origin. */
data class ExploreSection(
    /** Globally-unique id: the owning plugin id combined with the plugin's own section id. */
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val title: String,
    val subtitle: String?,
    val items: List<MediaItem>,
)

/** A mood / genre chip surfaced from a plugin section filter. [pluginId]+[key] route a tap back. */
data class ExploreMood(
    val label: String,
    val pluginId: String,
    val sectionId: String,
    val key: String,
    /** True once tapped: the owning section has been re-filtered by this chip. Host UI only. */
    val selected: Boolean = false,
)

/**
 * Pure, Android-free assembly of the Explore surface from what plugins expose. Deterministic and
 * side-effect free so it is fully JVM unit-testable; the ViewModel does the plugin/network calls
 * and feeds the raw results here.
 */
object ExploreAggregator {

    /**
     * Merge the discovery data of every plugin into one ordered [ExploreContent].
     *
     * @param homeContent per-plugin home feeds as `(pluginId, HomeContent)` — exactly the shape
     *   [PluginRepository.getHomeContent] returns. A plugin that fails to load simply isn't in this
     *   list (its absence degrades gracefully to less content).
     * @param categories browse categories already aggregated across plugins.
     * @param pluginNames `pluginId -> display name`, for labelling a section's origin. Missing ids
     *   fall back to the raw plugin id.
     *
     * Ordering is deterministic: sections keep the order plugins are given in, and within a plugin
     * the order the plugin emitted them. Empty sections (no items) are dropped. Sections are deduped
     * by their global id so the same section can't appear twice.
     */
    fun aggregate(
        homeContent: List<Pair<String, HomeContent>>,
        categories: List<BrowseCategory>,
        pluginNames: Map<String, String> = emptyMap(),
    ): ExploreContent {
        val sections = mergeSections(homeContent, pluginNames)
        val moods = mergeMoods(homeContent)
        return ExploreContent(
            moods = moods,
            categories = dedupeCategories(categories),
            sections = sections,
        )
    }

    /**
     * Flatten every plugin's home sections into [ExploreSection]s, dropping empty ones, deduping by
     * global id, and preserving `(plugin order, then in-plugin order)`.
     */
    fun mergeSections(
        homeContent: List<Pair<String, HomeContent>>,
        pluginNames: Map<String, String> = emptyMap(),
    ): List<ExploreSection> {
        val seen = HashSet<String>()
        val out = ArrayList<ExploreSection>()
        for ((pluginId, content) in homeContent) {
            for (section in content.sections) {
                val items = section.items
                if (items.isEmpty()) continue
                val globalId = globalSectionId(pluginId, section)
                if (!seen.add(globalId)) continue
                out += ExploreSection(
                    id = globalId,
                    pluginId = pluginId,
                    pluginName = pluginNames[pluginId] ?: pluginId,
                    title = section.title,
                    subtitle = section.subtitle,
                    items = items,
                )
            }
        }
        return out
    }

    /**
     * Collect mood / genre chips from every section filter across all plugins, deduped case-
     * insensitively by label (first occurrence wins), in encounter order.
     */
    fun mergeMoods(homeContent: List<Pair<String, HomeContent>>): List<ExploreMood> {
        val seenLabels = HashSet<String>()
        val out = ArrayList<ExploreMood>()
        for ((pluginId, content) in homeContent) {
            for (section in content.sections) {
                for (filter in section.filters()) {
                    val norm = filter.label.trim().lowercase()
                    if (norm.isEmpty() || !seenLabels.add(norm)) continue
                    out += ExploreMood(
                        label = filter.label.trim(),
                        pluginId = pluginId,
                        sectionId = section.id,
                        key = filter.key,
                    )
                }
            }
        }
        return out
    }

    /** Dedupe browse categories by `pluginId + id` (first wins), preserving order. */
    fun dedupeCategories(categories: List<BrowseCategory>): List<BrowseCategory> {
        val seen = HashSet<String>()
        return categories.filter { seen.add("${it.pluginId}:${it.id}") }
    }

    private fun globalSectionId(pluginId: String, section: HomeSection): String =
        "$pluginId:${section.id}"

    /** Only [CarouselSection] carries filter chips today. */
    private fun HomeSection.filters(): List<SectionFilter> =
        (this as? CarouselSection)?.filters ?: emptyList()
}
