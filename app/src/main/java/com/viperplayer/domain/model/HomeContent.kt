package com.viperplayer.domain.model

/**
 * Container for home screen content provided by a plugin.
 */
data class HomeContent(
    val quickPicks: List<MediaItem>? = null,
    val sections: List<HomeSection> = emptyList()
)

/** Shape hint for the artwork of the items in a section. */
enum class ItemShape { SQUARE, CIRCLE, WIDE }

/** Optional "see all" / navigation affordance shown on a section header. */
data class SectionAction(val label: String, val targetId: String? = null)

/** An interactive chip on a section (e.g. a genre selector); tapping it re-filters the section. */
data class SectionFilter(val label: String, val key: String, val selected: Boolean = false)

/**
 * A home-screen section provided by a plugin. Sealed so each visual design carries exactly the fields
 * it needs; the host renders each design differently (see HomeScreen). Mirrors the plugin-SDK's
 * `HomeSection`; unknown designs are dropped during mapping.
 */
sealed interface HomeSection {
    val id: String
    val title: String
    val subtitle: String?
    val action: SectionAction?

    /** Id of the plugin that produced this section; the host routes filter taps back to it. */
    val pluginId: String

    /** Every media item in this section (a single-element list for [HeroSection], empty for [BannerSection]). */
    val items: List<MediaItem>
}

/** Horizontal scroller of cards — the most common design. [rows] > 1 makes it a multi-row shelf. */
/** Transient load state of a section's in-place filter refresh (host UI only, never a plugin field). */
enum class FilterState { Idle, Loading, Error }

data class CarouselSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
    val itemShape: ItemShape = ItemShape.SQUARE,
    val rows: Int = 1,
    val filters: List<SectionFilter> = emptyList(),
    val filterState: FilterState = FilterState.Idle,
) : HomeSection

/** Multi-column grid of cards. */
data class GridSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
    val columns: Int = 2,
    val itemShape: ItemShape = ItemShape.SQUARE,
) : HomeSection

/** Vertical list of rows — e.g. a chart or a track list. */
data class ListSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
) : HomeSection

/** A single, prominently featured item with an optional backdrop and blurb. */
data class HeroSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    val item: MediaItem,
    val backgroundImageUrl: String? = null,
    val description: String? = null,
) : HomeSection {
    override val items: List<MediaItem> get() = listOf(item)
}

/** A promotional banner: text / image + an action, with no media items of its own. */
data class BannerSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    val text: String? = null,
    val imageUrl: String? = null,
) : HomeSection {
    override val items: List<MediaItem> get() = emptyList()
}

/**
 * A big, full-bleed swipeable carousel of large artwork cards with a gradient scrim + overlaid
 * title/subtitle (a daily-discover shelf). Rendered with M3 `HorizontalMultiBrowseCarousel`.
 */
data class MultiBrowseCarouselSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
    val itemShape: ItemShape = ItemShape.WIDE,
    val showBackdrop: Boolean = true,
) : HomeSection

/**
 * A multi-row, horizontally-snapping grid of compact item rows (a quick-picks grid). [rows] is
 * the number of stacked rows per column.
 */
data class QuickPicksSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
    val rows: Int = 4,
) : HomeSection

/** A horizontal row of LARGE rich cards for featured playlists/albums (a community shelf). */
data class FeaturedCardsSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    override val items: List<MediaItem> = emptyList(),
    val itemShape: ItemShape = ItemShape.SQUARE,
) : HomeSection

/** A mood/genre chip on a [MoodGridSection]; tapping it opens the plugin's category for [targetId]. */
data class MoodChip(
    val label: String,
    val targetId: String,
    val artworkUrl: String? = null,
    /** Optional accent colour as an `#RRGGBB` / `#AARRGGBB` hex string. */
    val accentColor: String? = null,
)

/** A grid of mood/genre chip cards (a mood/genre grid); carries no media items of its own. */
data class MoodGridSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val pluginId: String = "",
    val chips: List<MoodChip> = emptyList(),
) : HomeSection {
    override val items: List<MediaItem> get() = emptyList()
}
