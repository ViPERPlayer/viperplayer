package com.viperplayer.domain.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PendingScrobbleQueue]: add-with-dedupe, oldest-first drain ordering, batching, and
 * removal after submission.
 */
class PendingScrobbleQueueTest {

    private fun scrobble(artist: String, title: String, ts: Long) =
        PendingScrobble(ScrobbleTrack(artist, title), ts)

    @Test
    fun add_appendsNewScrobble() {
        val start = listOf(scrobble("A", "1", 100))
        val result = PendingScrobbleQueue.add(start, scrobble("B", "2", 200))
        assertEquals(2, result.size)
        assertEquals("B", result.last().track.artist)
    }

    @Test
    fun add_deduplicatesSameArtistTitleTimestamp() {
        val start = listOf(scrobble("A", "1", 100))
        val dup = scrobble("A", "1", 100)
        val result = PendingScrobbleQueue.add(start, dup)
        // Unchanged list is returned (no duplicate enqueued).
        assertSame(start, result)
        assertEquals(1, result.size)
    }

    @Test
    fun add_sameTrackDifferentTimestampIsNotDuplicate() {
        val start = listOf(scrobble("A", "1", 100))
        val result = PendingScrobbleQueue.add(start, scrobble("A", "1", 101))
        assertEquals(2, result.size)
    }

    @Test
    fun drainOrder_isOldestFirst() {
        val unordered = listOf(
            scrobble("C", "3", 300),
            scrobble("A", "1", 100),
            scrobble("B", "2", 200),
        )
        val ordered = PendingScrobbleQueue.drainOrder(unordered)
        assertEquals(listOf(100L, 200L, 300L), ordered.map { it.timestampSeconds })
    }

    @Test
    fun batches_splitsByMaxBatchInDrainOrder() {
        val n = LastfmRequests.MAX_BATCH + 5
        // Insert in reverse to prove batching also sorts.
        val items = (n downTo 1).map { scrobble("A$it", "T$it", it.toLong()) }
        val batches = PendingScrobbleQueue.batches(items)
        assertEquals(2, batches.size)
        assertEquals(LastfmRequests.MAX_BATCH, batches[0].size)
        assertEquals(5, batches[1].size)
        // First batch starts at the smallest timestamp.
        assertEquals(1L, batches[0].first().timestampSeconds)
        // Batches remain globally ordered oldest-first.
        assertTrue(batches[0].last().timestampSeconds < batches[1].first().timestampSeconds)
    }

    @Test
    fun batches_emptyInputProducesNoBatches() {
        assertTrue(PendingScrobbleQueue.batches(emptyList()).isEmpty())
    }

    @Test
    fun remove_dropsSubmittedKeepsOrderOfRest() {
        val a = scrobble("A", "1", 100)
        val b = scrobble("B", "2", 200)
        val c = scrobble("C", "3", 300)
        val remaining = PendingScrobbleQueue.remove(listOf(a, b, c), listOf(a, c))
        assertEquals(listOf("B"), remaining.map { it.track.artist })
    }
}
