package com.viperplayer.domain.model

/**
 * Pure, framework-free logic for the per-song lyrics timing offset (feature parity with a manual
 * sync-nudge). The offset (in milliseconds, positive or negative) shifts the effective playback
 * position used to pick the active synced line: a positive offset makes lyrics appear *earlier*
 * (the highlight leads the audio), a negative one makes them appear *later*.
 *
 * Kept off the composable and out of the ViewModel wiring so the position→line mapping can be
 * unit-tested on the JVM without Compose, Android or coroutines.
 */
object LyricsOffset {

    /** Adjustment granularity of a single +/- step, in milliseconds. */
    const val STEP_MS = 100L

    /** Bound the offset to a sane range so the stepper can't wander arbitrarily far. */
    const val MAX_ABS_MS = 30_000L

    /** No offset — the default for a song the user has never nudged. */
    const val NONE_MS = 0L

    /**
     * The effective position (ms) the active-line selection should use: the real [positionMs]
     * shifted forward by [offsetMs]. May go negative (before the first line) when a large positive
     * offset is applied near the track start — callers already tolerate a pre-first-line position.
     */
    fun effectivePosition(positionMs: Long, offsetMs: Long): Long = positionMs + offsetMs

    /**
     * Clamp an arbitrary offset into the supported range so persistence and the UI never store or
     * show a runaway value.
     */
    fun clamp(offsetMs: Long): Long = offsetMs.coerceIn(-MAX_ABS_MS, MAX_ABS_MS)

    /** The offset after nudging [current] by [steps] +/- steps of [STEP_MS], clamped to range. */
    fun nudged(current: Long, steps: Int): Long = clamp(current + steps * STEP_MS)
}
