package com.viperplayer.domain.lastfm

/**
 * Pure, Android-free decision of whether a play qualifies as a Last.fm scrobble, per Last.fm's
 * published rules (https://www.last.fm/api/scrobbling):
 *
 *  - The track must be longer than [MIN_TRACK_LENGTH_MS] (30 seconds). Tracks with unknown/zero
 *    duration cannot be scrobbled.
 *  - The track must have been played for at least [halfFraction] of its length (50%) **or** for
 *    [ABSOLUTE_THRESHOLD_MS] (4 minutes), whichever comes first.
 *
 * Note this is deliberately STRICTER and different from the in-app listening-stats threshold
 * ([com.viperplayer.domain.stats.ListeningStatsAggregator.countsAsPlay], which counts at 30s / 50%):
 * Last.fm requires 4 minutes / 50% and a >30s track. Keeping this as its own function makes the rule
 * explicit and unit-testable.
 *
 * All inputs are milliseconds. This is the primary unit-test target for the scrobble decision.
 */
object ScrobbleEligibility {

    /** Tracks at or below this length (ms) are never scrobbled. Last.fm: "longer than 30 seconds". */
    const val MIN_TRACK_LENGTH_MS = 30_000L

    /** Absolute listened-time threshold (ms) that always qualifies: 4 minutes. */
    const val ABSOLUTE_THRESHOLD_MS = 240_000L

    /** Default fraction of the track that must be played to qualify: 50%. */
    const val DEFAULT_HALF_FRACTION = 0.5

    /**
     * Whether a play of a [durationMs]-long track that was actually listened to for [listenedMs]
     * qualifies as a scrobble.
     *
     * @param listenedMs actual play time in ms (excludes pauses/buffering/scrubbing). Zero/negative
     *   never qualifies.
     * @param durationMs full track length in ms. Must be strictly greater than [MIN_TRACK_LENGTH_MS];
     *   zero/unknown/short tracks never qualify.
     * @param halfFraction fraction of the track that qualifies (default 50%).
     */
    fun isEligible(
        listenedMs: Long,
        durationMs: Long,
        halfFraction: Double = DEFAULT_HALF_FRACTION,
    ): Boolean {
        if (listenedMs <= 0L) return false
        // Last.fm only accepts tracks strictly longer than 30 seconds.
        if (durationMs <= MIN_TRACK_LENGTH_MS) return false
        if (listenedMs >= ABSOLUTE_THRESHOLD_MS) return true
        return listenedMs.toDouble() >= durationMs.toDouble() * halfFraction
    }
}
