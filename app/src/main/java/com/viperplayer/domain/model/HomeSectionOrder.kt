package com.viperplayer.domain.model

import kotlin.random.Random

/**
 * Pure, Android-free logic for the randomized Home section order — modelled on a weighted
 * shuffle (HomeScreen ~lines 1048-1120). Kept out of the view + ViewModel so it can be unit-tested
 * directly.
 *
 * The order is STABLE within a session: each section's sort weight is drawn from a `Random` seeded by
 * `seed + section.id.hashCode()`, so a given section keeps its weight for a given [seed] even as other
 * sections appear/disappear (no jumping). Re-rolling requires a NEW [seed] (an explicit Home refresh).
 *
 * A section's weight is `base + modifier` where higher-value ("hero") section types get a higher base
 * and a wider variance so they tend to sit near the top but can shuffle among themselves, and plain
 * shelves cluster below with small variance. This uses a tiered scheme, adapted to
 * ViPER's [HomeSection] subtypes (which carry no such enum, so tiering is by design + id).
 */
object HomeSectionOrder {

    /**
     * Returns [sections] in the order to render. When [randomize] is false the input order is kept
     * verbatim. When true, sections are sorted by descending weight using [seed] for stable,
     * per-session randomness. The sort is stable, so equal weights preserve the input order.
     */
    fun order(sections: List<HomeSection>, randomize: Boolean, seed: Long): List<HomeSection> {
        if (!randomize || sections.size < 2) return sections
        // sortedByDescending is stable in Kotlin, so ties fall back to the original plugin order.
        return sections.sortedByDescending { section -> weightOf(section, seed) }
    }

    /**
     * The sort weight for [section] under [seed]: a tier [base] plus a per-section [modifier] drawn
     * from a `Random(seed + id.hashCode())` — so it's deterministic for a (section, seed) pair.
     */
    private fun weightOf(section: HomeSection, seed: Long): Int {
        val sectionRandom = Random(seed + section.id.hashCode())
        return when (section.tier()) {
            Tier.HERO -> 500 + sectionRandom.nextInt(-200, 400) // [300, 900]
            Tier.MID -> 300 + sectionRandom.nextInt(-100, 400) // [200, 700]
            Tier.BASE -> 100 + sectionRandom.nextInt(-50, 50) // [50, 150]
        }
    }

    private enum class Tier { HERO, MID, BASE }

    /** Coarse importance tier of a section design, used to bias where it lands in the shuffle. */
    private fun HomeSection.tier(): Tier = when (this) {
        // Hero designs: big, feature-forward — Daily Discover / Quick Picks / From the community.
        is MultiBrowseCarouselSection,
        is QuickPicksSection,
        is FeaturedCardsSection,
        is HeroSection,
        -> Tier.HERO

        // Mid designs: standard shelves and grids of content.
        is CarouselSection,
        is GridSection,
        is ListSection,
        -> Tier.MID

        // Base designs: banners / mood chips — supporting content, tends to sit lower.
        is BannerSection,
        is MoodGridSection,
        -> Tier.BASE
    }
}
