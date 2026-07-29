package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [HomeSectionOrder] weighted-shuffle logic:
 *  - randomize OFF keeps the input order verbatim,
 *  - the order is STABLE within a given seed (no jumping between recomputes),
 *  - a different seed can reorder,
 *  - the same set of sections is always returned (nothing dropped/duplicated).
 */
class HomeSectionOrderTest {

    private fun carousel(id: String) = CarouselSection(id = id, title = id, pluginId = "p")

    private val sections: List<HomeSection> = (1..12).map { carousel("s$it") }

    @Test
    fun randomizeOff_keepsInputOrder() {
        val result = HomeSectionOrder.order(sections, randomize = false, seed = 42L)
        assertEquals(sections, result)
    }

    @Test
    fun stableWithinASeed_recomputingGivesSameOrder() {
        val a = HomeSectionOrder.order(sections, randomize = true, seed = 123L)
        val b = HomeSectionOrder.order(sections, randomize = true, seed = 123L)
        assertEquals("same seed must yield the same order", a.map { it.id }, b.map { it.id })
    }

    @Test
    fun stableWithinASeed_evenWhenOtherSectionsChange() {
        // A given section keeps its weight for a given seed, so dropping others must not shuffle it
        // relative to the remaining ones (the anti-jumping property).
        val full = HomeSectionOrder.order(sections, randomize = true, seed = 7L).map { it.id }
        val subset = sections.filter { it.id != "s3" && it.id != "s9" }
        val subsetOrder = HomeSectionOrder.order(subset, randomize = true, seed = 7L).map { it.id }
        val fullMinusRemoved = full.filter { it != "s3" && it != "s9" }
        assertEquals(fullMinusRemoved, subsetOrder)
    }

    @Test
    fun differentSeeds_canReorder() {
        // Over a spread of seeds, at least one must differ from seed 0's order (the shuffle is real).
        val base = HomeSectionOrder.order(sections, randomize = true, seed = 0L).map { it.id }
        val anyDifferent = (1L..50L).any { seed ->
            HomeSectionOrder.order(sections, randomize = true, seed = seed).map { it.id } != base
        }
        assertTrue("a different seed should produce a different order for at least one seed", anyDifferent)
    }

    @Test
    fun preservesTheExactSameSet() {
        val result = HomeSectionOrder.order(sections, randomize = true, seed = 99L)
        assertEquals(sections.size, result.size)
        assertEquals(sections.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun singleSection_isReturnedUnchanged() {
        val one = listOf(carousel("only"))
        assertEquals(one, HomeSectionOrder.order(one, randomize = true, seed = 5L))
    }

    @Test
    fun heroTierTendsAboveBaseTier() {
        // A hero-tier section (weight range [300,900]) should out-weight a base-tier section
        // (range [50,150]) for every seed — they never overlap.
        val hero = QuickPicksSection(id = "hero", title = "Quick Picks", pluginId = "p")
        val base = BannerSection(id = "banner", title = "Banner", pluginId = "p")
        (0L..30L).forEach { seed ->
            val order = HomeSectionOrder.order(listOf(base, hero), randomize = true, seed = seed).map { it.id }
            assertEquals("hero must sort above base at seed=$seed", "hero", order.first())
        }
        // And a couple of hero sections do shuffle among themselves across seeds.
        val h1 = QuickPicksSection(id = "h1", title = "A", pluginId = "p")
        val h2 = MultiBrowseCarouselSection(id = "h2", title = "B", pluginId = "p")
        val orderings = (0L..40L).map {
            HomeSectionOrder.order(listOf(h1, h2), randomize = true, seed = it).first().id
        }.toSet()
        assertNotEquals("two hero sections should not always keep the same order", 1, orderings.size)
    }
}
