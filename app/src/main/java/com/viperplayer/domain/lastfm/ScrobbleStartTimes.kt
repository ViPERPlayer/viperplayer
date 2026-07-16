package com.viperplayer.domain.lastfm

/**
 * Pure, Android-free registry that correlates a playback session's START timestamp with the
 * session-end signal that later scrobbles it. Kept side-effect free and testable without media3.
 *
 * WHY THIS EXISTS: media3 fires a track's `onPlaybackStatsReady` (session end) AFTER the NEXT
 * session's `onMediaItemTransition` (session start). Keying a single start time by bare mediaId is
 * therefore wrong under REPEAT_MODE_ONE: the second repeat's start overwrites the first's before the
 * first session ends, so the first repeat's scrobble would be stamped with the second play's start.
 *
 * FIX: hold a FIFO queue of start times PER mediaId. Each session start [record]s a start time; each
 * session end [consume]s the OLDEST outstanding start time for that mediaId. Because media3 ends
 * sessions in the same order it started them, session A's end pairs with session A's own start — even
 * when A and B are the same mediaId (repeat-one). In the common (non-repeat) case there is only ever
 * one outstanding start per mediaId, so behaviour is unchanged.
 *
 * The whole registry is bounded to [maxTrackedStarts] outstanding starts (oldest evicted first) so a
 * lost end signal can't leak memory. NOT thread-safe: the caller synchronizes access (the scrobbler
 * holds a monitor around record/consume).
 */
class ScrobbleStartTimes(private val maxTrackedStarts: Int = DEFAULT_MAX_TRACKED_STARTS) {

    // Insertion-ordered per-key FIFO queues. The LinkedHashMap preserves cross-key insertion order so
    // eviction of the globally-oldest outstanding start is cheap.
    private val pending = LinkedHashMap<String, ArrayDeque<Long>>()

    /** Total outstanding start times across all keys (for bounding + tests). */
    val size: Int get() = pending.values.sumOf { it.size }

    /**
     * Records that a session for [mediaId] started at [startSeconds] (UNIX seconds). Enqueued at the
     * tail of that mediaId's FIFO so it pairs with the matching session end. Evicts the globally-oldest
     * outstanding start when over [maxTrackedStarts].
     */
    fun record(mediaId: String, startSeconds: Long) {
        pending.getOrPut(mediaId) { ArrayDeque() }.addLast(startSeconds)
        evictOverflow()
    }

    /**
     * Consumes and returns the OLDEST outstanding start time for [mediaId] (the one that pairs with the
     * earliest-started, now-ending session), or null when none was recorded (e.g. the process restarted
     * mid-track). Removes the key entirely once its queue empties.
     */
    fun consume(mediaId: String): Long? {
        val queue = pending[mediaId] ?: return null
        val start = queue.removeFirstOrNull()
        if (queue.isEmpty()) pending.remove(mediaId)
        return start
    }

    private fun evictOverflow() {
        while (size > maxTrackedStarts) {
            val eldestKey = pending.keys.firstOrNull() ?: return
            val queue = pending[eldestKey] ?: return
            queue.removeFirstOrNull()
            if (queue.isEmpty()) pending.remove(eldestKey)
        }
    }

    companion object {
        /** Bounds outstanding starts; a handful covers the current + a few pending session-end signals. */
        const val DEFAULT_MAX_TRACKED_STARTS = 8
    }
}
