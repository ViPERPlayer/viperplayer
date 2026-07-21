package com.viperplayer.presentation.player

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for [RadioQueueBuilder]: the "Song radio" queue is the seed followed by the
 * related songs, with the seed always first and duplicate ids de-duplicated (first occurrence wins),
 * preserving the related order. Android-free — [MediaId] equality is a plain data-class compare.
 */
class RadioQueueBuilderTest {

    private fun song(id: String, title: String = id) =
        Song(id = MediaId.Plugin("p", id), title = title, durationMs = 1000L)

    @Test
    fun buildSongs_seedFirst_thenRelated() {
        val seed = song("seed")
        val related = listOf(song("a"), song("b"), song("c"))

        val result = RadioQueueBuilder.buildSongs(seed, related)

        assertEquals(listOf("seed", "a", "b", "c"), result.map { it.id.sourceId })
    }

    @Test
    fun buildSongs_dropsSeedFromRelated() {
        val seed = song("seed")
        val related = listOf(song("a"), song("seed"), song("b"))

        val result = RadioQueueBuilder.buildSongs(seed, related)

        // The seed appears exactly once, at the front — its related copy is dropped.
        assertEquals(listOf("seed", "a", "b"), result.map { it.id.sourceId })
    }

    @Test
    fun buildSongs_dedupesRelated_byId_preservingFirstPosition() {
        val seed = song("seed")
        val related = listOf(song("a", "A1"), song("b"), song("a", "A2"), song("b"))

        val result = RadioQueueBuilder.buildSongs(seed, related)

        // A duplicate id appears once, at its first position (id math is first-occurrence-wins).
        assertEquals(listOf("seed", "a", "b"), result.map { it.id.sourceId })
    }

    @Test
    fun buildSongs_noRelated_isJustSeed() {
        val seed = song("seed")

        val result = RadioQueueBuilder.buildSongs(seed, emptyList())

        assertEquals(listOf(seed), result)
    }
}
