package com.viperplayer.follows

import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistOrdering
import com.viperplayer.follows.domain.FollowedArtistSort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for [FollowedArtistOrdering] — dedupe by id and the two display orders. No
 * Android, no I/O.
 */
class FollowedArtistOrderingTest {

    private fun artist(
        pluginId: String = "p",
        sourceId: String,
        name: String,
        followedAt: Long,
    ) = FollowedArtist(
        mediaId = MediaId.Plugin(pluginId, sourceId),
        name = name,
        artworkUrl = null,
        followedAt = followedAt,
    )

    @Test
    fun dedupe_collapsesSameMediaId_keepingNewest() {
        val older = artist(sourceId = "1", name = "Old name", followedAt = 100)
        val newer = artist(sourceId = "1", name = "New name", followedAt = 200)

        val result = FollowedArtistOrdering.dedupe(listOf(older, newer))

        assertEquals(1, result.size)
        assertEquals("New name", result.first().name)
        assertEquals(200L, result.first().followedAt)
    }

    @Test
    fun dedupe_treatsDifferentPluginsAsDistinct() {
        val a = artist(pluginId = "sixthsource", sourceId = "1", name = "A", followedAt = 100)
        val b = artist(pluginId = "testsource", sourceId = "1", name = "A", followedAt = 100)

        val result = FollowedArtistOrdering.dedupe(listOf(a, b))

        assertEquals(2, result.size)
    }

    @Test
    fun sortedByName_isCaseInsensitiveAlphabetical() {
        val list = listOf(
            artist(sourceId = "1", name = "banana", followedAt = 1),
            artist(sourceId = "2", name = "Apple", followedAt = 2),
            artist(sourceId = "3", name = "cherry", followedAt = 3),
        )

        val names = FollowedArtistOrdering.sorted(list, FollowedArtistSort.NAME).map { it.name }

        assertEquals(listOf("Apple", "banana", "cherry"), names)
    }

    @Test
    fun sortedByName_breaksTiesByMostRecent() {
        val list = listOf(
            artist(sourceId = "1", name = "Same", followedAt = 100),
            artist(sourceId = "2", name = "Same", followedAt = 300),
            artist(sourceId = "3", name = "Same", followedAt = 200),
        )

        val order = FollowedArtistOrdering.sorted(list, FollowedArtistSort.NAME).map { it.mediaId.sourceId }

        assertEquals(listOf("2", "3", "1"), order)
    }

    @Test
    fun sortedByRecentlyFollowed_isNewestFirst() {
        val list = listOf(
            artist(sourceId = "1", name = "A", followedAt = 100),
            artist(sourceId = "2", name = "B", followedAt = 300),
            artist(sourceId = "3", name = "C", followedAt = 200),
        )

        val order =
            FollowedArtistOrdering.sorted(list, FollowedArtistSort.RECENTLY_FOLLOWED).map { it.mediaId.sourceId }

        assertEquals(listOf("2", "3", "1"), order)
    }

    @Test
    fun sorted_dedupesBeforeOrdering() {
        val list = listOf(
            artist(sourceId = "1", name = "Zed", followedAt = 100),
            artist(sourceId = "1", name = "Zed", followedAt = 500),
            artist(sourceId = "2", name = "Amy", followedAt = 200),
        )

        val result = FollowedArtistOrdering.sorted(list, FollowedArtistSort.NAME)

        assertEquals(2, result.size)
        assertEquals(listOf("Amy", "Zed"), result.map { it.name })
    }
}
