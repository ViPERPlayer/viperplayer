package com.viperplayer.data.social

import kotlin.math.abs

/**
 * Pure heuristic for the host mirror (layer 2, part B): decide whether a *seek* happened between two
 * ~1 Hz position samples of the LOCAL player, so the host can broadcast it to followers.
 *
 * Between samples the position is expected to advance by `elapsedWallMs * speed` while playing (and
 * not at all while paused). If the actual position deviates from that expectation by more than
 * [SEEK_THRESHOLD_MS], the user (or an auto-advance) moved the playhead — treat it as a seek. Track
 * changes are handled separately by observing the current song, so callers should skip this check on
 * the sample where the track id changed.
 */
object HostSeekDetector {

    /** Deviation beyond which a sample is treated as a user seek rather than normal playback drift. */
    const val SEEK_THRESHOLD_MS = 750L

    /**
     * @param lastPositionMs the previous sample's position.
     * @param currentPositionMs this sample's position.
     * @param elapsedWallMs wall-clock milliseconds between the two samples.
     * @param speed the player's tempo at the time (1.0 = normal); 0 or negative is treated as paused.
     * @param isPlaying whether the player was playing across the interval (paused → no expected advance).
     * @return true when the position moved unexpectedly far (a seek).
     */
    fun seekHappened(
        lastPositionMs: Long,
        currentPositionMs: Long,
        elapsedWallMs: Long,
        speed: Float,
        isPlaying: Boolean,
    ): Boolean {
        val expectedAdvance = if (isPlaying && speed > 0f) (elapsedWallMs * speed).toLong() else 0L
        val expected = lastPositionMs + expectedAdvance
        return abs(currentPositionMs - expected) > SEEK_THRESHOLD_MS
    }
}
