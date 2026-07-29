package com.viperplayer.data.rec

import com.viperplayer.data.local.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the two indexer policy decisions in [SongEmbedCandidates]: which songs have local
 * bytes (streaming-only is skipped) and in what order they're embedded (downloaded > liked > recent >
 * other). No device, codec or model involved.
 */
class SongEmbedCandidatesTest {

    private fun song(
        id: Long,
        idType: String = "plugin",
        pluginId: String = "testsource",
        sourceId: String = "s$id",
        isDownloaded: Boolean = false,
        downloadPath: String? = null,
        isLiked: Boolean = false,
        lastPlayed: Long? = null,
    ) = SongEntity(
        id = id,
        idType = idType,
        pluginId = pluginId,
        sourceId = sourceId,
        title = "t$id",
        isDownloaded = isDownloaded,
        downloadPath = downloadPath,
        isLiked = isLiked,
        lastPlayed = lastPlayed,
    )

    // ---- hasLocalBytes ----

    @Test
    fun downloadedWithPathHasLocalBytes() {
        assertTrue(SongEmbedCandidates.hasLocalBytes(song(1, isDownloaded = true, downloadPath = "/x.mp3")))
    }

    @Test
    fun downloadedWithBlankPathHasNoLocalBytes() {
        assertFalse(SongEmbedCandidates.hasLocalBytes(song(1, isDownloaded = true, downloadPath = "")))
        assertFalse(SongEmbedCandidates.hasLocalBytes(song(2, isDownloaded = true, downloadPath = null)))
    }

    @Test
    fun localLibrarySongHasLocalBytes() {
        val local = song(3, idType = "local", pluginId = "", sourceId = "content://media/audio/1")
        assertTrue(SongEmbedCandidates.hasLocalBytes(local))
    }

    @Test
    fun streamingOnlyPluginSongHasNoLocalBytes() {
        // Not downloaded, not local -> streaming only -> skipped.
        assertFalse(SongEmbedCandidates.hasLocalBytes(song(4, isLiked = true, lastPlayed = 100L)))
    }

    // ---- priorityOf ----

    @Test
    fun priorityTiersRankDownloadedFirst() {
        assertEquals(
            SongEmbedCandidates.Priority.DOWNLOADED,
            SongEmbedCandidates.priorityOf(song(1, isDownloaded = true, downloadPath = "/x", isLiked = true, lastPlayed = 9L)),
        )
        assertEquals(
            SongEmbedCandidates.Priority.LIKED,
            SongEmbedCandidates.priorityOf(song(2, isLiked = true, lastPlayed = 9L)),
        )
        assertEquals(
            SongEmbedCandidates.Priority.RECENT,
            SongEmbedCandidates.priorityOf(song(3, lastPlayed = 9L)),
        )
        assertEquals(
            SongEmbedCandidates.Priority.OTHER,
            SongEmbedCandidates.priorityOf(song(4)),
        )
    }

    // ---- prioritize ----

    @Test
    fun prioritizeOrdersDownloadedThenLikedThenRecentThenOther() {
        val other = song(10)
        val recent = song(11, lastPlayed = 500L)
        val liked = song(12, isLiked = true)
        val downloaded = song(13, isDownloaded = true, downloadPath = "/d")
        val ordered = SongEmbedCandidates.prioritize(listOf(other, recent, liked, downloaded))
        assertEquals(listOf(13L, 12L, 11L, 10L), ordered.map { it.id })
    }

    @Test
    fun prioritizeBreaksTiesByMostRecentThenId() {
        // Two RECENT songs: the more-recently-played first, then id for equal timestamps.
        val a = song(1, lastPlayed = 100L)
        val b = song(2, lastPlayed = 900L)
        val c = song(3, lastPlayed = 900L)
        val ordered = SongEmbedCandidates.prioritize(listOf(a, b, c))
        // b & c share 900L -> id asc (2 before 3); a (100L) last.
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun embeddableInPriorityOrderDropsStreamingOnlyAndOrders() {
        val streamOnly = song(1, isLiked = true) // no local bytes -> dropped
        val downloaded = song(2, isDownloaded = true, downloadPath = "/d")
        val local = song(3, idType = "local", pluginId = "", sourceId = "content://x", lastPlayed = 5L)
        val result = SongEmbedCandidates.embeddableInPriorityOrder(listOf(streamOnly, downloaded, local))
        // streamOnly dropped; downloaded (tier 0) before local (tier RECENT).
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    // ---- streaming partition (must be the exact complement of hasLocalBytes; no overlap) ----

    @Test
    fun streamingCandidateIsPluginAndNotDownloaded() {
        assertTrue(SongEmbedCandidates.isStreamingCandidate(song(1, isLiked = true)))
        assertTrue(SongEmbedCandidates.isStreamingCandidate(song(2, lastPlayed = 5L)))
    }

    @Test
    fun downloadedOrLocalIsNotAStreamingCandidate() {
        assertFalse(SongEmbedCandidates.isStreamingCandidate(song(1, isDownloaded = true, downloadPath = "/d")))
        assertFalse(SongEmbedCandidates.isStreamingCandidate(song(2, idType = "local", pluginId = "", sourceId = "content://x")))
        // A downloaded plugin track is the LOCAL worker's job, not the streaming worker's.
        assertFalse(SongEmbedCandidates.isStreamingCandidate(song(3, isDownloaded = true, downloadPath = "/x", isLiked = true)))
    }

    @Test
    fun localAndStreamingPartitionsAreAnExactComplementWithNoGap() {
        val songs = listOf(
            song(1, isDownloaded = true, downloadPath = "/d"),                  // local (downloaded)
            song(2, idType = "local", pluginId = "", sourceId = "content://x"), // local (library)
            song(3, isLiked = true),                                           // streaming
            song(4, lastPlayed = 9L),                                          // streaming
            song(5, isDownloaded = true, downloadPath = ""),                    // downloaded flag, blank path
        )
        // EXACTLY ONE partition claims each row (XOR): no overlap (no double-embed) and no gap (nothing
        // orphaned). Every plugin row is either hasLocalBytes or isStreamingCandidate; local-library rows
        // are always hasLocalBytes.
        for (s in songs) {
            val local = SongEmbedCandidates.hasLocalBytes(s)
            val streaming = SongEmbedCandidates.isStreamingCandidate(s)
            assertTrue("song ${s.id} must be in exactly one partition", local != streaming)
        }
        assertTrue(SongEmbedCandidates.hasLocalBytes(songs[0]))
        assertTrue(SongEmbedCandidates.hasLocalBytes(songs[1]))
        assertTrue(SongEmbedCandidates.isStreamingCandidate(songs[2]))
        assertTrue(SongEmbedCandidates.isStreamingCandidate(songs[3]))
        // Song 5 (downloaded flag but blank path) has NO local bytes, so the STREAMING worker claims it —
        // the previous "embeddable by neither worker" gap is closed.
        assertFalse(SongEmbedCandidates.hasLocalBytes(songs[4]))
        assertTrue(SongEmbedCandidates.isStreamingCandidate(songs[4]))
    }

    @Test
    fun streamingInPriorityOrderDropsLocalAndOrders() {
        val downloaded = song(1, isDownloaded = true, downloadPath = "/d")     // dropped (local)
        val local = song(2, idType = "local", pluginId = "", sourceId = "u")   // dropped (local)
        val liked = song(3, isLiked = true)                                    // streaming, LIKED
        val recent = song(4, lastPlayed = 500L)                                // streaming, RECENT
        val result = SongEmbedCandidates.streamingInPriorityOrder(listOf(downloaded, local, recent, liked))
        assertEquals(listOf(3L, 4L), result.map { it.id }) // liked before recent
    }
}
