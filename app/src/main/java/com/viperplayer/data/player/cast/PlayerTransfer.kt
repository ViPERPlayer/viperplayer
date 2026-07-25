package com.viperplayer.data.player.cast

/**
 * A pure snapshot of the transportable state that must move with the queue when switching between
 * the local ExoPlayer and the CastPlayer (and back). Holding it in a plain data class keeps the
 * index-clamping / empty-queue logic unit-testable, away from the framework glue that reads it off
 * one [androidx.media3.common.Player] and writes it onto another.
 */
data class PlayerTransfer(
    /** The queue as a list of media ids, in order. Empty when there is nothing to transfer. */
    val mediaIds: List<String>,
    /** The current item index within [mediaIds]. */
    val currentIndex: Int,
    /** The current playback position within the current item, in milliseconds. */
    val positionMs: Long,
    /** Whether the target player should resume playing once it has the queue. */
    val playWhenReady: Boolean,
) {
    /** Whether there is anything to transfer at all. */
    val isEmpty: Boolean get() = mediaIds.isEmpty()

    /** [currentIndex] clamped into the valid range for [mediaIds] (0 when empty). */
    val safeIndex: Int
        get() = if (mediaIds.isEmpty()) 0 else currentIndex.coerceIn(0, mediaIds.lastIndex)

    /** [positionMs] never negative. */
    val safePositionMs: Long get() = positionMs.coerceAtLeast(0L)

    companion object {
        val EMPTY = PlayerTransfer(emptyList(), 0, 0L, false)
    }
}
