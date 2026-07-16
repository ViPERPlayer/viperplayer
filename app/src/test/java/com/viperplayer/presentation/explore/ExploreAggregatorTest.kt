package com.viperplayer.presentation.explore

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.SectionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [ExploreAggregator] — the Android-free logic that merges, dedupes and orders
 * the discovery data (home sections, mood/genre filters, browse categories) that plugins expose.
 */
class ExploreAggregatorTest {

    private fun album(pluginId: String, id: String, name: String = id): Album =
        Album(id = MediaId(pluginId, id), name = name)

    private fun carousel(
        id: String,
        title: String,
        items: List<MediaItem>,
        filters: List<SectionFilter> = emptyList(),
    ): HomeSection = CarouselSection(id = id, title = title, items = items, filters = filters)

    private fun home(vararg sections: HomeSection) = HomeContent(sections = sections.toList())

    // --- Empty ---

    @Test
    fun emptyInput_producesEmptyContent() {
        val result = ExploreAggregator.aggregate(emptyList(), emptyList())
        assertTrue(result.isEmpty)
        assertTrue(result.sections.isEmpty())
        assertTrue(result.moods.isEmpty())
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun pluginsWithNoDiscoveryData_produceEmptyContent() {
        val result = ExploreAggregator.aggregate(
            homeContent = listOf("p1" to HomeContent(), "p2" to HomeContent()),
            categories = emptyList(),
        )
        assertTrue(result.isEmpty)
    }

    // --- Section merge / order / dedupe ---

    @Test
    fun mergeSections_preservesPluginThenInPluginOrder() {
        val home = listOf(
            "sixthsource" to home(
                carousel("s1", "a streaming service A", listOf(album("sixthsource", "1"))),
                carousel("s2", "a streaming service B", listOf(album("sixthsource", "2"))),
            ),
            "testsource" to home(
                carousel("t1", "a plugin A", listOf(album("testsource", "3"))),
            ),
        )

        val sections = ExploreAggregator.mergeSections(home)

        assertEquals(
            listOf("sixthsource:s1", "sixthsource:s2", "testsource:t1"),
            sections.map { it.id },
        )
        assertEquals(listOf("a streaming service A", "a streaming service B", "a plugin A"), sections.map { it.title })
    }

    @Test
    fun mergeSections_dropsEmptySections() {
        val home = listOf(
            "p" to home(
                carousel("empty", "Empty", emptyList()),
                carousel("full", "Full", listOf(album("p", "1"))),
            ),
        )

        val sections = ExploreAggregator.mergeSections(home)

        assertEquals(listOf("p:full"), sections.map { it.id })
    }

    @Test
    fun mergeSections_dedupesByGlobalId_firstWins() {
        val home = listOf(
            "p" to home(
                carousel("dup", "First", listOf(album("p", "1"))),
                carousel("dup", "Second", listOf(album("p", "2"))),
            ),
        )

        val sections = ExploreAggregator.mergeSections(home)

        assertEquals(1, sections.size)
        assertEquals("First", sections.single().title)
    }

    @Test
    fun mergeSections_sameSectionIdAcrossPlugins_bothKept() {
        // Two plugins can legitimately use the same section id; they must not collide.
        val home = listOf(
            "p1" to home(carousel("charts", "P1 Charts", listOf(album("p1", "1")))),
            "p2" to home(carousel("charts", "P2 Charts", listOf(album("p2", "1")))),
        )

        val sections = ExploreAggregator.mergeSections(home)

        assertEquals(listOf("p1:charts", "p2:charts"), sections.map { it.id })
    }

    @Test
    fun mergeSections_resolvesPluginDisplayName_fallingBackToId() {
        val home = listOf(
            "p1" to home(carousel("s", "Sec", listOf(album("p1", "1")))),
            "p2" to home(carousel("s", "Sec", listOf(album("p2", "1")))),
        )

        val sections = ExploreAggregator.mergeSections(home, pluginNames = mapOf("p1" to "a plugin"))

        assertEquals("a plugin", sections[0].pluginName)
        assertEquals("p2", sections[1].pluginName) // no mapping -> raw id
    }

    // --- Moods (section filters) ---

    @Test
    fun mergeMoods_collectsFilters_dedupedByLabelCaseInsensitive() {
        val home = listOf(
            "p1" to home(
                carousel(
                    "s1", "Sec", listOf(album("p1", "1")),
                    filters = listOf(SectionFilter("Chill", "chill"), SectionFilter("Focus", "focus")),
                ),
            ),
            "p2" to home(
                carousel(
                    "s2", "Sec", listOf(album("p2", "1")),
                    // "chill" duplicates p1's "Chill" (case-insensitive) -> dropped; "Party" is new.
                    filters = listOf(SectionFilter("chill", "c2"), SectionFilter("Party", "party")),
                ),
            ),
        )

        val moods = ExploreAggregator.mergeMoods(home)

        assertEquals(listOf("Chill", "Focus", "Party"), moods.map { it.label })
        // First occurrence wins: "Chill" keeps p1's routing.
        assertEquals("p1", moods.first().pluginId)
        assertEquals("chill", moods.first().key)
    }

    @Test
    fun mergeMoods_ignoresBlankLabels() {
        val home = listOf(
            "p" to home(
                carousel(
                    "s", "Sec", listOf(album("p", "1")),
                    filters = listOf(SectionFilter("   ", "blank"), SectionFilter("Rock", "rock")),
                ),
            ),
        )

        val moods = ExploreAggregator.mergeMoods(home)

        assertEquals(listOf("Rock"), moods.map { it.label })
    }

    // --- Categories dedupe ---

    @Test
    fun dedupeCategories_removesDuplicateByPluginAndId_preservesOrder() {
        val categories = listOf(
            BrowseCategory(id = "rock", pluginId = "p1", name = "Rock"),
            BrowseCategory(id = "rock", pluginId = "p1", name = "Rock (dup)"),
            BrowseCategory(id = "rock", pluginId = "p2", name = "Rock (other plugin)"),
            BrowseCategory(id = "pop", pluginId = "p1", name = "Pop"),
        )

        val result = ExploreAggregator.dedupeCategories(categories)

        assertEquals(
            listOf("Rock", "Rock (other plugin)", "Pop"),
            result.map { it.name },
        )
    }

    // --- Full aggregate + determinism ---

    @Test
    fun aggregate_combinesAllThreeStreams() {
        val home = listOf(
            "p" to home(
                carousel(
                    "s1", "Top Albums", listOf(album("p", "1")),
                    filters = listOf(SectionFilter("Chill", "chill")),
                ),
            ),
        )
        val categories = listOf(BrowseCategory(id = "charts", pluginId = "p", name = "Charts"))

        val result = ExploreAggregator.aggregate(home, categories, mapOf("p" to "Plugin"))

        assertFalse(result.isEmpty)
        assertEquals(listOf("Chill"), result.moods.map { it.label })
        assertEquals(listOf("Charts"), result.categories.map { it.name })
        assertEquals(listOf("p:s1"), result.sections.map { it.id })
        assertEquals("Plugin", result.sections.single().pluginName)
    }

    @Test
    fun aggregate_isDeterministic_sameInputSameOutput() {
        val home = listOf(
            "a" to home(carousel("s1", "A1", listOf(album("a", "1")))),
            "b" to home(carousel("s1", "B1", listOf(album("b", "1")))),
        )
        val categories = listOf(BrowseCategory(id = "c", pluginId = "a", name = "Cat"))

        val first = ExploreAggregator.aggregate(home, categories)
        val second = ExploreAggregator.aggregate(home, categories)

        assertEquals(first, second)
    }

    @Test
    fun aggregate_oneSourceMissing_stillSurfacesTheOther() {
        // Simulates a plugin whose load failed (absent from the list) — the other still shows.
        val home = listOf(
            "healthy" to home(carousel("s", "Healthy", listOf(album("healthy", "1")))),
        )

        val result = ExploreAggregator.aggregate(home, emptyList())

        assertEquals(listOf("healthy:s"), result.sections.map { it.id })
    }
}
