package com.viperplayer.alarm.domain

/**
 * Pure, Android-free fade-in volume curve. Factored out of the alarm playback path so the ramp can be
 * unit-tested without a player.
 */
object AlarmFade {

    /**
     * The player volume (0..[targetVolume]) at [elapsedMs] into a linear fade-in of length
     * [durationMs] toward [targetVolume].
     *
     *  - `elapsedMs <= 0`            -> 0f            (silent at the very start)
     *  - `elapsedMs >= durationMs`   -> [targetVolume] (fully faded in)
     *  - in between                  -> linear ramp, monotonically increasing
     *
     * A non-positive [durationMs] means "no fade": the target volume is returned immediately (except
     * at exactly t=0, which is still 0 so a fade of 0 is indistinguishable from starting at target).
     * [targetVolume] is clamped to 0..1.
     */
    fun volumeAt(elapsedMs: Long, durationMs: Long, targetVolume: Float): Float {
        val target = targetVolume.coerceIn(0f, 1f)
        if (elapsedMs <= 0L) return 0f
        if (durationMs <= 0L || elapsedMs >= durationMs) return target
        val fraction = elapsedMs.toFloat() / durationMs.toFloat()
        return (fraction * target).coerceIn(0f, target)
    }
}
