package com.viperplayer.domain.repository

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecResult

/**
 * Server-backed **Discovery** (P3): songs the user does NOT already own, ranked by the backend
 * recommender against the device's on-device CLAP taste vector. Where [RecommendationRepository]
 * surfaces the user's OWN library ("For You" / "More like this") from the local kNN index, this
 * surfaces genuinely NEW tracks from the catalog via the backend `/recommendations` endpoint.
 *
 * Every call degrades safely to a clear [RecResult.Empty] (disabled / not-indexed / nothing-new /
 * error) and never throws to the caller — all network/mapping/auth glue lives in the implementation,
 * per the app's MVVM rule.
 */
interface DiscoveryRepository {

    /**
     * A page of unowned discovery candidates, best-first.
     *
     * Gates on the smart-recommendations opt-in ([RecResult.Empty] with
     * [com.viperplayer.domain.model.RecEmptyReason.RECOMMENDATIONS_DISABLED] when off) and on a warm
     * taste vector ([com.viperplayer.domain.model.RecEmptyReason.NOT_INDEXED_YET] when the taste is
     * still cold). Builds `exclude_ids` from the user's owned library so results are always new,
     * POSTs to the backend, and maps each candidate to a playable [com.viperplayer.domain.model.Song]
     * with a plugin [com.viperplayer.domain.model.MediaId.Plugin] id (so tapping plays through the
     * normal plugin stream path). A model-version handshake mismatch or a network failure surfaces as
     * a graceful empty rather than an error.
     *
     * @param limit maximum number of songs to return.
     */
    suspend fun discover(limit: Int): RecResult

    /**
     * Fire-and-forget: if the anonymized-contribution opt-in is ON and the taste is warm, POSTs the
     * single L2-normalized taste vector (no ids, no audio, no identity) to `/discovery/contribute` to
     * improve global discovery. A no-op when opted out, cold, or the backend is unavailable; never
     * throws.
     */
    suspend fun contributeTasteIfEnabled()

    /**
     * Explicit user feedback (P4) on a server-Discovery candidate. Thumbs-**up** likes the song (reusing
     * the normal like path). Thumbs-**down** suppresses the id from FUTURE discovery feeds — because
     * server candidates have no on-device embedding, there's nothing to nudge the taste away from, so the
     * honest behavior is to stop resurfacing it. Idempotent and fire-safe (never throws).
     *
     * @param mediaId the discovered song (a [com.viperplayer.domain.model.MediaId.Plugin]).
     * @param positive true = thumbs up, false = thumbs down.
     */
    suspend fun sendFeedback(mediaId: MediaId, positive: Boolean)
}
