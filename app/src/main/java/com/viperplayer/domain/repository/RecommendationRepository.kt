package com.viperplayer.domain.repository

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecResult
import kotlinx.coroutines.flow.Flow

/**
 * On-device recommendations from the local CLAP audio-embedding kNN index (P1d), with a graceful
 * fallback to the source plugin's related-songs feed.
 *
 * Every method degrades safely: when embeddings/the model are absent it falls back to
 * [PluginRepository.getRelatedSongs] or returns a clear [RecResult.Empty] — it never throws to the
 * caller. All DB/index/plugin glue lives in the implementation, per the app's MVVM rule.
 */
interface RecommendationRepository {

    /**
     * "More like this": songs similar to the [seed] track.
     *
     * If the seed has a usable local embedding, runs kNN over the embedded library (excluding the seed
     * and its trivial same-album duplicates) → [RecResult.Local]. If the seed has no embedding
     * (streaming-only / not yet indexed) or the local pass yields too few results, falls back to the
     * plugin's related songs → [RecResult.Fallback]. If neither yields anything → [RecResult.Empty].
     *
     * @param limit maximum number of songs to return.
     */
    suspend fun moreLikeThis(seed: MediaId, limit: Int): RecResult

    /**
     * "For You": a mix built from the user's taste. Forms a simple centroid ([basic taste model]) from
     * the embeddings of the user's liked + recently-played songs (capped), then runs kNN over the rest
     * of the library → [RecResult.Local]. Returns [RecResult.Empty] when nothing is embedded yet.
     *
     * P2 replaces the centroid with the real taste model; the centroid logic is intentionally simple
     * and isolated so it can be swapped without touching callers.
     *
     * @param limit maximum number of songs to return.
     */
    suspend fun forYouFromLibrary(limit: Int): RecResult

    /**
     * Live readiness of the on-device recommender (opt-in state, model readiness, index size, and any
     * in-progress indexing) so the UI can pick the right empty/explanatory state.
     */
    val readiness: Flow<RecReadiness>

    /**
     * Explicit user feedback (P4) on a recommended library song. A thumbs-**up** likes the song (reusing
     * the normal like path, which is itself a strong positive taste signal). A thumbs-**down** applies a
     * strong `DISLIKE` taste nudge away from the song's on-device embedding when one exists. Idempotent
     * and fire-safe (never throws); a no-op when the song can't be addressed.
     *
     * @param mediaId the recommended song.
     * @param positive true = thumbs up, false = thumbs down.
     */
    suspend fun sendFeedback(mediaId: MediaId, positive: Boolean)
}
