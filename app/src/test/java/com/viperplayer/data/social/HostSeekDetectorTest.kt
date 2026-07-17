package com.viperplayer.data.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HostSeekDetector.seekHappened] — the host-mirror heuristic that distinguishes a user
 * seek from normal playback advance between two ~1 Hz position samples.
 */
class HostSeekDetectorTest {

    @Test
    fun normalAdvance_whilePlaying_noSeek() {
        // 1s elapsed at 1x -> expected +1000ms; actual +1000ms.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 6_000,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = true,
        )
        assertFalse(seek)
    }

    @Test
    fun smallJitter_withinThreshold_noSeek() {
        // Expected 6000, actual 6400 -> 400ms < 750ms.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 6_400,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = true,
        )
        assertFalse(seek)
    }

    @Test
    fun forwardSeek_beyondThreshold_isSeek() {
        // Expected 6000, actual 30000 -> huge jump forward.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 30_000,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = true,
        )
        assertTrue(seek)
    }

    @Test
    fun backwardSeek_isSeek() {
        // Expected 6000, actual 1000 -> jumped back.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 1_000,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = true,
        )
        assertTrue(seek)
    }

    @Test
    fun pausedAndPositionUnchanged_noSeek() {
        // Paused: expected no advance; position stayed put.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 5_000,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = false,
        )
        assertFalse(seek)
    }

    @Test
    fun pausedButPositionMoved_isSeek() {
        // Paused but the playhead moved a lot -> a seek while paused.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 20_000,
            elapsedWallMs = 1_000, speed = 1f, isPlaying = false,
        )
        assertTrue(seek)
    }

    @Test
    fun doubleSpeed_advanceScaledBySpeed_noSeek() {
        // 2x for 1s -> expected +2000ms; actual +2000ms -> no seek.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 5_000, currentPositionMs = 7_000,
            elapsedWallMs = 1_000, speed = 2f, isPlaying = true,
        )
        assertFalse(seek)
    }

    @Test
    fun doubleSpeed_expectingSingle_wouldFalselyTrip_butSpeedAccountedFor() {
        // At 2x, a naive last+elapsed (ignoring speed) would flag +2000 as a seek; the detector must not.
        val seek = HostSeekDetector.seekHappened(
            lastPositionMs = 0, currentPositionMs = 2_000,
            elapsedWallMs = 1_000, speed = 2f, isPlaying = true,
        )
        assertFalse(seek)
    }
}
