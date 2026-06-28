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

    /** Every media item in this section (a single-element list for [HeroSection], empty for [BannerSection]). */
    val items: List<MediaItem>
}

/** Horizontal scroller of cards — the most common design. */
data class CarouselSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
    override val items: List<MediaItem> = emptyList(),
    val itemShape: ItemShape = ItemShape.SQUARE,
) : HomeSection

/** Multi-column grid of cards. */
data class GridSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
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
    override val items: List<MediaItem> = emptyList(),
) : HomeSection

/** A single, prominently featured item with an optional backdrop and blurb. */
data class HeroSection(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val action: SectionAction? = null,
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
    val text: String? = null,
    val imageUrl: String? = null,
) : HomeSection {
    override val items: List<MediaItem> get() = emptyList()
}
