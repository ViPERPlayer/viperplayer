package com.viperplayer.data.mapper

import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.domain.model.FeaturedCardsSection
import com.viperplayer.domain.model.ItemShape
import com.viperplayer.domain.model.MoodGridSection
import com.viperplayer.domain.model.MultiBrowseCarouselSection
import com.viperplayer.domain.model.QuickPicksSection
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.model.FeaturedCardsSection as SdkFeaturedCardsSection
import com.viperplayer.plugin.model.ItemShape as SdkItemShape
import com.viperplayer.plugin.model.MoodChip as SdkMoodChip
import com.viperplayer.plugin.model.MoodGridSection as SdkMoodGridSection
import com.viperplayer.plugin.model.MultiBrowseCarouselSection as SdkMultiBrowseCarouselSection
import com.viperplayer.plugin.model.QuickPicksSection as SdkQuickPicksSection
import com.viperplayer.plugin.model.Song as SdkSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the SDK -> domain mapping for the Wave-1 home section designs: each new
 * SDK subtype maps to its domain counterpart, opaque item ids get namespaced with the producing
 * plugin, and section-specific fields (shape / rows / chips) survive the crossing.
 */
class PluginMapperHomeSectionTest {

    private val pluginId = "testsource"

    private fun sdkSong(id: String) = SdkSong(id = id, title = "Song $id")

    @Test
    fun multiBrowseCarousel_mapsFieldsAndItems() {
        val sdk = SdkMultiBrowseCarouselSection(
            id = "daily",
            title = "Daily Discover",
            subtitle = "For you",
            items = listOf(sdkSong("s1"), sdkSong("s2")),
            itemShape = SdkItemShape.WIDE,
            showBackdrop = false,
        )

        val domain = sdk.toDomain(pluginId)

        assertTrue(domain is MultiBrowseCarouselSection)
        domain as MultiBrowseCarouselSection
        assertEquals("daily", domain.id)
        assertEquals("Daily Discover", domain.title)
        assertEquals("For you", domain.subtitle)
        assertEquals(pluginId, domain.pluginId)
        assertEquals(ItemShape.WIDE, domain.itemShape)
        assertEquals(false, domain.showBackdrop)
        assertEquals(2, domain.items.size)
        assertTrue(domain.items[0] is Song)
    }

    @Test
    fun quickPicks_mapsRowsAndItems() {
        val sdk = SdkQuickPicksSection(
            id = "quick",
            title = "Quick Picks",
            items = listOf(sdkSong("s1")),
            rows = 3,
        )

        val domain = sdk.toDomain(pluginId)

        assertTrue(domain is QuickPicksSection)
        domain as QuickPicksSection
        assertEquals(3, domain.rows)
        assertEquals(pluginId, domain.pluginId)
        assertEquals(1, domain.items.size)
    }

    @Test
    fun featuredCards_mapsShapeAndItems() {
        val sdk = SdkFeaturedCardsSection(
            id = "featured",
            title = "From the community",
            items = listOf(sdkSong("s1")),
            itemShape = SdkItemShape.SQUARE,
        )

        val domain = sdk.toDomain(pluginId)

        assertTrue(domain is FeaturedCardsSection)
        domain as FeaturedCardsSection
        assertEquals(ItemShape.SQUARE, domain.itemShape)
        assertEquals(1, domain.items.size)
    }

    @Test
    fun moodGrid_mapsChipsAndHasNoItems() {
        val sdk = SdkMoodGridSection(
            id = "moods",
            title = "Mood and genres",
            chips = listOf(
                SdkMoodChip(label = "Chill", targetId = "c1", accentColor = "#FF8800"),
                SdkMoodChip(label = "Focus", targetId = "c2", artworkUrl = "http://art"),
            ),
        )

        val domain = sdk.toDomain(pluginId)

        assertTrue(domain is MoodGridSection)
        domain as MoodGridSection
        assertEquals(2, domain.chips.size)
        assertEquals("Chill", domain.chips[0].label)
        assertEquals("c1", domain.chips[0].targetId)
        assertEquals("#FF8800", domain.chips[0].accentColor)
        assertEquals("http://art", domain.chips[1].artworkUrl)
        // A mood grid carries chips, not media items.
        assertTrue(domain.items.isEmpty())
    }

    @Test
    fun multiBrowseCarousel_dropsUnmappableItems() {
        // A blank-id entity artist can't produce a MediaId and must be skipped, not crash the map.
        val sdk = SdkMultiBrowseCarouselSection(
            id = "daily",
            items = listOf(sdkSong("s1"), com.viperplayer.plugin.model.Artist(id = "", name = "Unlinked")),
        )

        val domain = sdk.toDomain(pluginId) as MultiBrowseCarouselSection

        assertEquals(1, domain.items.size)
        assertNotNull(domain.items.firstOrNull())
    }

    @Test
    fun unknownSection_mapsToNullAndIsDropped() {
        val sdk = com.viperplayer.plugin.model.UnknownSection(id = "x")
        assertNull(sdk.toDomain(pluginId))
    }
}
