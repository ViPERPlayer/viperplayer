package com.viperplayer.domain.lastfm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScrobbleEligibility]: Last.fm scrobbles require a track longer than 30s that was
 * played for >=4 minutes OR >=50% of its length.
 */
class ScrobbleEligibilityTest {

    private val min = ScrobbleEligibility.MIN_TRACK_LENGTH_MS       // 30_000
    private val abs = ScrobbleEligibility.ABSOLUTE_THRESHOLD_MS     // 240_000

    @Test
    fun eligible_whenPlayedAtLeastHalf() {
        // 3-minute track, played 90s = exactly 50%.
        assertTrue(ScrobbleEligibility.isEligible(listenedMs = 90_000, durationMs = 180_000))
    }

    @Test
    fun eligible_whenPlayedFourMinutesOfLongTrack() {
        // 20-minute track: 4 minutes (<50%) still qualifies via the absolute threshold.
        assertTrue(ScrobbleEligibility.isEligible(listenedMs = abs, durationMs = 20 * 60_000))
    }

    @Test
    fun notEligible_belowHalfAndBelowFourMinutes() {
        // 6-minute track played 2 minutes: 33% and < 4 min.
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = 120_000, durationMs = 360_000))
    }

    @Test
    fun notEligible_whenTrackIsThirtySecondsOrShorter() {
        // A 30s track can never scrobble, even if fully played.
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = min, durationMs = min))
        // A 25s track fully played: still no.
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = 25_000, durationMs = 25_000))
    }

    @Test
    fun eligible_forTrackJustOverThirtySecondsPlayedFully() {
        // 31s track played fully (>50%) qualifies.
        assertTrue(ScrobbleEligibility.isEligible(listenedMs = 31_000, durationMs = 31_000))
    }

    @Test
    fun notEligible_forZeroOrUnknownDuration() {
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = abs, durationMs = 0))
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = abs, durationMs = -1))
    }

    @Test
    fun notEligible_forZeroOrNegativeListened() {
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = 0, durationMs = 180_000))
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = -5, durationMs = 180_000))
    }

    @Test
    fun boundary_exactlyHalfIsEligible_justUnderHalfIsNot() {
        // 200s track: 100s (50%) eligible, 99.999s not.
        assertTrue(ScrobbleEligibility.isEligible(listenedMs = 100_000, durationMs = 200_000))
        assertFalse(ScrobbleEligibility.isEligible(listenedMs = 99_999, durationMs = 200_000))
    }
}
