package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.getEmbedding
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.InteractionType
import com.viperplayer.domain.rec.TasteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The data-layer seam that turns raw playback/library signals into online [TasteRepository] nudges,
 * keeping the learning wiring OUT of the UI and off the callers' threads.
 *
 * Both hooks are **fire-and-forget**: they launch on a dedicated IO scope and swallow all failures, so
 * they can never slow down or crash the playback / like paths that call them. A play/like whose song has
 * no local embedding (streaming-only, not yet indexed) is silently ignored — there is no vector to nudge
 * the taste toward. The play/skip decision (completion ratio) and the interaction shapes are the pure
 * [com.viperplayer.domain.rec.TasteModel]'s job; this only resolves embeddings and dispatches.
 */
@Singleton
class TasteLearningHooks @Inject constructor(
    private val songDao: SongDao,
    private val tasteRepository: TasteRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Learns from a finished play of [mediaId] that was listened to for [listenedMs] of [durationMs].
     * A near-complete listen is a [InteractionType.COMPLETE] positive nudge (scaled by completion); a
     * very short listen relative to duration is treated as a [InteractionType.SKIP] negative nudge. In
     * between (partial but not a skip) is a gentle [InteractionType.CLICK]. No-op without an embedding.
     */
    fun onPlay(mediaId: MediaId, listenedMs: Long, durationMs: Long) {
        scope.launch {
            runCatching {
                val embedding = embeddingFor(mediaId) ?: return@runCatching
                val completion = if (durationMs > 0L) {
                    (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    1f // unknown duration but it fired a session-end ⇒ assume a real listen.
                }
                val interaction = when {
                    completion >= COMPLETE_THRESHOLD ->
                        Interaction(InteractionType.COMPLETE, embedding, completion)
                    completion <= SKIP_THRESHOLD ->
                        Interaction(InteractionType.SKIP, embedding, completion)
                    else ->
                        Interaction(InteractionType.CLICK, embedding, completion)
                }
                tasteRepository.onInteraction(interaction)
            }.onFailure { Timber.w(it, "TasteLearningHooks: onPlay nudge failed") }
        }
    }

    /**
     * Learns from a like/unlike of [mediaId]: [InteractionType.LIKE] (strong positive) or
     * [InteractionType.UNLIKE] (mild negative). No-op without an embedding.
     */
    fun onLikeChanged(mediaId: MediaId, isLiked: Boolean) {
        scope.launch {
            runCatching {
                val embedding = embeddingFor(mediaId) ?: return@runCatching
                val type = if (isLiked) InteractionType.LIKE else InteractionType.UNLIKE
                tasteRepository.onInteraction(Interaction(type, embedding))
            }.onFailure { Timber.w(it, "TasteLearningHooks: onLikeChanged nudge failed") }
        }
    }

    /** The decoded local embedding for [mediaId], or null when there is none / it can't be decoded. */
    private suspend fun embeddingFor(mediaId: MediaId): FloatArray? {
        val bytes = songDao.getEmbedding(mediaId) ?: return null
        return runCatching { bytesToEmbedding(bytes) }.getOrNull()
    }

    private companion object {
        /** At/above this completion ratio a play is a positive COMPLETE signal. */
        const val COMPLETE_THRESHOLD = 0.6f

        /** At/below this completion ratio a (session-ended) play is treated as a SKIP. */
        const val SKIP_THRESHOLD = 0.2f
    }
}
