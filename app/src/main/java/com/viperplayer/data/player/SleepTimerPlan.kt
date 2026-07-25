package com.viperplayer.data.player

/**
 * The armed sleep-timer mode, surfaced to the UI so the picker can tick the active choice.
 *
 * - [Off] — no timer armed.
 * - [Minutes] — pause after a fixed [count] of minutes from arming.
 * - [EndOfTrack] — pause when the track that is current at arm time finishes.
 */
sealed interface SleepTimerMode {
    data object Off : SleepTimerMode
    data class Minutes(val count: Int) : SleepTimerMode
    data object EndOfTrack : SleepTimerMode
}

/**
 * Pure, Android-free sleep-timer math. Factored out of [SleepTimerManager] so the fade-window and
 * remaining-time computations can be unit-tested without a player. All times are milliseconds.
 */
object SleepTimerPlan {

    /**
     * Milliseconds remaining until the pause boundary, given the current playback [positionMs] and the
     * track/timer end at [endMs]. Clamped to `>= 0` (never negative; a passed boundary reads as 0 =
     * "fire now").
     */
    fun remainingMs(positionMs: Long, endMs: Long): Long = (endMs - positionMs).coerceAtLeast(0L)

    /**
     * The fade-out volume multiplier (0..1) at [remainingMs] before the pause boundary, ramping linearly
     * from 1.0 down to 0.0 over the final [fadeMs].
     *
     *  - `remainingMs >= fadeMs`   -> 1f  (outside the fade window: full volume)
     *  - `remainingMs <= 0`        -> 0f  (at/after the boundary: silent)
     *  - in between                -> linear ramp `remainingMs / fadeMs`
     *
     * A non-positive [fadeMs] means "no fade": full volume until the boundary (returns 1f while any
     * time remains, 0f once it's gone).
     */
    fun fadeVolumeAt(remainingMs: Long, fadeMs: Long): Float {
        if (remainingMs <= 0L) return 0f
        if (fadeMs <= 0L || remainingMs >= fadeMs) return 1f
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * True when a fade should have begun: the fade window (last [fadeMs]) has been entered — i.e. the
     * boundary is [fadeMs] or less away. False when [fadeMs] is non-positive (no fade) or the boundary
     * is still further out than the window.
     */
    fun isInFadeWindow(remainingMs: Long, fadeMs: Long): Boolean =
        fadeMs > 0L && remainingMs <= fadeMs && remainingMs > 0L
}
