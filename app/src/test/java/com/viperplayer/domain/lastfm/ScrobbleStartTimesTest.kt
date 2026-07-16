package com.viperplayer.domain.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ScrobbleStartTimes]: the FIFO-per-mediaId correlation that stamps each session's
 * scrobble with its OWN start time — the fix for the REPEAT_MODE_ONE timestamp bug where a later
 * repeat's start would clobber the earlier repeat's before its session-end scrobble was submitted.
 */
class ScrobbleStartTimesTest {

    @Test
    fun commonCase_recordThenConsumeReturnsThatStart() {
        val starts = ScrobbleStartTimes()
        starts.record("song", 1000L)
        assertEquals(1000L, starts.consume("song"))
        assertEquals(0, starts.size)
    }

    @Test
    fun repeatOne_eachSessionGetsItsOwnStart_notTheLatest() {
        val starts = ScrobbleStartTimes()
        // media3 under repeat-one: session A starts, then session B (same mediaId) starts BEFORE
        // session A's onPlaybackStatsReady fires.
        starts.record("song", 1000L) // session A start
        starts.record("song", 2000L) // session B start (would clobber A with a bare-mediaId map)

        // Sessions end in start order: A ends first and MUST get A's start (1000), not B's (2000).
        assertEquals(1000L, starts.consume("song"))
        assertEquals(2000L, starts.consume("song"))
        assertEquals(0, starts.size)
    }

    @Test
    fun consume_withNoRecordedStart_returnsNull() {
        val starts = ScrobbleStartTimes()
        assertNull(starts.consume("never-started"))
    }

    @Test
    fun consume_isOnce_secondConsumeIsNull() {
        val starts = ScrobbleStartTimes()
        starts.record("song", 1000L)
        assertEquals(1000L, starts.consume("song"))
        // A stray/duplicate session-end for the same mediaId can't reuse a stale stamp.
        assertNull(starts.consume("song"))
    }

    @Test
    fun differentMediaIds_areIndependent() {
        val starts = ScrobbleStartTimes()
        starts.record("a", 100L)
        starts.record("b", 200L)
        assertEquals(200L, starts.consume("b"))
        assertEquals(100L, starts.consume("a"))
    }

    @Test
    fun overflow_evictsGloballyOldestStart() {
        val starts = ScrobbleStartTimes(maxTrackedStarts = 2)
        starts.record("a", 1L) // oldest
        starts.record("b", 2L)
        starts.record("c", 3L) // pushes over the bound -> evicts the oldest ("a"/1)
        assertEquals(2, starts.size)
        assertNull(starts.consume("a"))
        assertEquals(2L, starts.consume("b"))
        assertEquals(3L, starts.consume("c"))
    }

    @Test
    fun overflow_evictsFifoWithinAKeyBeforeMovingOn() {
        val starts = ScrobbleStartTimes(maxTrackedStarts = 2)
        starts.record("a", 1L) // oldest overall
        starts.record("a", 2L)
        starts.record("a", 3L) // over bound -> evicts the oldest start (1L)
        assertEquals(2, starts.size)
        // Remaining outstanding starts for "a" are the two most-recent, still oldest-first.
        assertEquals(2L, starts.consume("a"))
        assertEquals(3L, starts.consume("a"))
    }
}
