package com.viperplayer.domain.rec

/**
 * Owns the user's persisted, online-learned [TasteState] and the small "recently served" ring the
 * reranker uses for anti-repetition. The pure math lives in [TasteModel] / [Reranker]; this is the
 * stateful seam the data layer wires the learning signals into and the recommender reads taste from.
 *
 * All methods degrade gracefully — a failure to load/persist returns a cold taste rather than throwing,
 * so recommendations always keep working (they simply fall back to the non-personalized path).
 */
interface TasteRepository {

    /**
     * The current taste, rebuilding it from listening history first if it is cold or stale. Cheap on
     * repeat calls (the rebuilt taste is cached + persisted). Returns [TasteState.COLD] when there is
     * no signal yet — callers then fall back to the non-personalized recommender.
     */
    suspend fun taste(): TasteState

    /**
     * The taste for the CURRENT time-of-day bucket (P4), rebuilding first if cold/stale like [taste].
     * Falls back to the global [taste] when the current bucket has not warmed up yet, so a caller can
     * always prefer the time-aware taste without a separate cold-check. Returns [TasteState.COLD] only
     * when the global taste is also cold.
     */
    suspend fun currentBucketTaste(): TasteState

    /**
     * The user's **mood centroids** (P4): up to K distinct taste clusters (each L2-normalized), rebuilt
     * with the periodic rebuild. Empty while cold or when there aren't enough distinct directions to
     * cluster — callers then fall back to the single-vector [taste]/[currentBucketTaste] behavior.
     */
    suspend fun moodCentroids(): List<FloatArray>

    /**
     * Records that the user thumbed-down a server-Discovery candidate with no on-device embedding, so it
     * is suppressed from future discovery feeds. Idempotent; bounded. (For candidates that DO have a
     * local embedding, prefer the stronger [onInteraction] `DISLIKE` signal instead.)
     */
    suspend fun suppressDiscoveryId(id: String)

    /** The set of thumbed-down server-Discovery ids to exclude from the feed (see [suppressDiscoveryId]). */
    suspend fun suppressedDiscoveryIds(): Set<String>

    /**
     * Records the ids just served to the user so the reranker can down-weight them on the next pass
     * (anti-repetition). Decays existing entries and adds/refreshes [servedIds].
     */
    suspend fun recordServed(servedIds: List<Long>)

    /** id → recency weight in `(0,1]` (1 = most recently served), for the reranker's anti-repetition. */
    suspend fun servedRecency(): Map<Long, Float>

    /** id → total times served, for the reranker's exploration/uncertainty term. */
    suspend fun servedCounts(): Map<Long, Int>

    /**
     * Applies one online learning [interaction] to the current taste and persists the result. Seeds a
     * cold taste from a positive interaction; a no-op when the interaction can't move the taste.
     */
    suspend fun onInteraction(interaction: Interaction)

    /** Forces a full rebuild of the taste from history on the next [taste] call (e.g. after a reset). */
    suspend fun invalidate()
}
