package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.getByMediaId
import com.viperplayer.data.local.dao.getEmbedding
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.mapper.mediaIdFromColumns
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.domain.rec.ClapModelState
import com.viperplayer.data.rec.EmbeddingKnn
import com.viperplayer.data.rec.IndexingStatus
import com.viperplayer.data.rec.IndexingStatusProvider
import com.viperplayer.data.rec.bytesToEmbedding
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.rec.Candidate
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.InteractionType
import com.viperplayer.domain.rec.RerankParams
import com.viperplayer.domain.rec.Reranker
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.RecommendationRepository
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local audio-embedding kNN recommender (P1d) with a plugin related-songs fallback.
 *
 * The kNN math is delegated to the pure [EmbeddingKnn]; this class owns only the DB/index/plugin glue:
 * reading embeddings (keyed by the Room row `Long` id), building candidate lists, resolving ranked ids
 * back to fully-hydrated [Song]s (via [MediaLibraryRepository]), and choosing between the local result
 * and the [PluginRepository.getRelatedSongs] fallback. Everything degrades to a fallback or a clear
 * [RecResult.Empty]; it never throws to the caller.
 */
@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val pluginRepository: PluginRepository,
    private val settingsRepository: SettingsRepository,
    private val clapModelRepository: ClapModelRepository,
    private val indexRepository: IndexingStatusProvider,
    private val tasteRepository: TasteRepository,
) : RecommendationRepository {

    override suspend fun moreLikeThis(seed: MediaId, limit: Int): RecResult {
        if (limit <= 0) return RecResult.Empty(RecEmptyReason.NO_RESULTS)

        // Streaming-only / not-yet-indexed seed: no local vector → go straight to the plugin fallback.
        val seedBytes = runCatchingRec("read seed embedding") { songDao.getEmbedding(seed) }
            ?: return fallback(seed, limit, disabled = !recommendationsEnabled())

        val query = runCatchingRec("decode seed embedding") { bytesToEmbedding(seedBytes) }
            ?: return fallback(seed, limit)

        val seedEntity = runCatchingRec("load seed row") { songDao.getByMediaId(seed) }

        val candidates = loadCandidates()
        if (candidates.isEmpty()) return fallback(seed, limit)

        // Exclude the seed itself and its trivial same-album duplicates (near-identical vectors that
        // would otherwise crowd the head of the list with alternate pressings of the same track).
        val excluded = buildExclusionSet(candidates, seedEntity)

        // 1) A wider kNN shortlist around the seed than we finally show, so the reranker has room to
        //    diversify / anti-repeat without dropping below `limit`.
        val shortlist = EmbeddingKnn.cosineTopK(
            query = query,
            candidates = candidates,
            k = limit * RERANK_POOL_FACTOR,
            excludeIds = excluded,
        )

        // 2) Rerank the shortlist against the seed: relevance-to-seed with MMR diversity + anti-repetition
        //    (no taste novelty/exploration here — "more like this" should stay close to the seed).
        val byId = candidates.associate { it.first to it.second }
        val rankedIds = rerank(
            reference = query,
            poolIds = shortlist.map { it.id },
            byId = byId,
            limit = limit,
            params = MORE_LIKE_THIS_PARAMS,
        )

        val songs = resolveSongs(rankedIds)
        // Too few local hits to be a satisfying "more like this" — prefer the plugin's fuller feed.
        return if (songs.size < MIN_LOCAL_RESULTS) {
            fallback(seed, limit, localFewSongs = songs)
        } else {
            recordServed(rankedIds)
            // Every result is "More like {seed title}" (the anchor is the seed itself).
            val seedTitle = seedEntity?.title
            val reasons = if (!seedTitle.isNullOrBlank()) {
                songs.associate { it.id to RecReason(RecReason.Kind.MORE_LIKE, seedTitle) }
            } else {
                emptyMap()
            }
            RecResult.Local(songs, reasons)
        }
    }

    override suspend fun forYouFromLibrary(limit: Int): RecResult {
        if (limit <= 0) return RecResult.Empty(RecEmptyReason.NO_RESULTS)
        if (!recommendationsEnabled()) return RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED)

        val candidates = loadCandidates()
        if (candidates.isEmpty()) {
            // Nothing embedded yet: distinguish "still indexing" from "genuinely empty".
            val notIndexed = clapModelRepository.modelState.first() !is ClapModelState.Ready ||
                indexRepository.indexingStatus.first() is IndexingStatus.Indexing
            return RecResult.Empty(
                if (notIndexed) RecEmptyReason.NOT_INDEXED_YET else RecEmptyReason.NO_RESULTS
            )
        }
        val byId = candidates.associate { it.first to it.second }

        // The online-learned taste (P2), time-of-day-aware (P4): prefer the current-hour bucket's taste
        // when warm, else the global taste. Cold/stale ⇒ rebuilt from history inside the repository.
        val taste = runCatchingRec("read taste") { tasteRepository.currentBucketTaste() }
        val tasteVector = taste?.vector

        // Mood centroids (P4): distinct taste clusters so the feed can span several moods, not just the
        // single averaged vector. Empty while cold ⇒ single-vector behavior below.
        val centroids = runCatchingRec("read mood centroids") { tasteRepository.moodCentroids() }.orEmpty()

        // Exclude the taste's own pool (liked + recently played) from the feed so "For You" surfaces
        // NEW songs, not the ones the taste was built from.
        val poolIds = tastePoolIds().filter { it in byId }.take(TASTE_POOL_CAP).toSet()

        val rankedIds: List<Long> = when {
            // Multi-mood: gather a kNN shortlist around EACH centroid, pool + dedupe, then rerank ONCE
            // over the pool (so MMR/novelty/anti-repetition still apply across moods). Ranked against
            // the current taste vector so relevance stays personalized.
            centroids.isNotEmpty() && tasteVector != null -> {
                val perCentroidK = maxOf(limit, (limit * RERANK_POOL_FACTOR) / centroids.size)
                val pooled = LinkedHashSet<Long>()
                for (centroid in centroids) {
                    EmbeddingKnn.cosineTopK(
                        query = centroid,
                        candidates = candidates,
                        k = perCentroidK,
                        excludeIds = poolIds,
                    ).forEach { pooled += it.id }
                }
                rerank(
                    reference = tasteVector,
                    poolIds = pooled.toList(),
                    byId = byId,
                    limit = limit,
                    params = FOR_YOU_PARAMS.copy(seed = explorationSeed()),
                )
            }

            tasteVector != null -> {
                // Personalized single-vector: narrow to the taste's neighbourhood with kNN, then rerank
                // that shortlist with novelty + exploration + MMR diversity + anti-repetition.
                val shortlist = EmbeddingKnn.cosineTopK(
                    query = tasteVector,
                    candidates = candidates,
                    k = limit * RERANK_POOL_FACTOR,
                    excludeIds = poolIds,
                ).map { it.id }
                // Vary the exploration seed by day so the long-tail picks the Reranker surfaces rotate
                // day-to-day (as its exploration term documents) while staying stable within a session/day.
                rerank(
                    reference = tasteVector,
                    poolIds = shortlist,
                    byId = byId,
                    limit = limit,
                    params = FOR_YOU_PARAMS.copy(seed = explorationSeed()),
                )
            }

            else -> {
                // Cold taste: fall back to the P1 centroid of the liked/recent pool (non-personalized).
                val poolVectors = poolIds.mapNotNull { byId[it] }
                val centroid = EmbeddingKnn.meanVector(poolVectors)
                    ?: return RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET) // no taste signal yet
                EmbeddingKnn.cosineTopK(
                    query = centroid,
                    candidates = candidates,
                    k = limit,
                    excludeIds = poolIds,
                ).map { it.id }
            }
        }

        val songs = resolveSongs(rankedIds)
        if (songs.isEmpty()) return RecResult.Empty(RecEmptyReason.NO_RESULTS)
        recordServed(rankedIds)
        // "Sounds like X" reasons: for each result, its nearest owned liked/most-played anchor by cosine.
        val reasons = forYouReasons(songs, rankedIds, byId, poolIds)
        return RecResult.Local(songs, reasons)
    }

    override val readiness: Flow<RecReadiness> = combine(
        settingsRepository.recommendationsEnabled,
        clapModelRepository.modelState,
        songDao.countSongsMissingEmbedding(ClapModel.MODEL_VERSION),
        indexRepository.indexingStatus,
    ) { enabled, modelState, _, indexing ->
        val indexed = runCatchingRec("count embeddings") {
            songDao.getAllEmbeddings(ClapModel.MODEL_VERSION).size
        } ?: 0
        RecReadiness(
            recommendationsEnabled = enabled,
            modelReady = modelState is ClapModelState.Ready,
            indexedCount = indexed,
            indexingProcessed = (indexing as? IndexingStatus.Indexing)?.processed,
            indexingTotal = (indexing as? IndexingStatus.Indexing)?.total,
        )
    }

    override suspend fun sendFeedback(mediaId: MediaId, positive: Boolean) {
        if (positive) {
            // Thumbs up = like the song. The like path already emits a strong positive taste nudge via
            // TasteLearningHooks.onLikeChanged, so we don't double-count with a manual onInteraction.
            runCatchingRec("thumbs up like") { mediaLibraryRepository.setSongLiked(mediaId, true) }
            return
        }
        // Thumbs down = a strong DISLIKE taste nudge away from the song's embedding, when we have one.
        val bytes = runCatchingRec("read feedback embedding") { songDao.getEmbedding(mediaId) } ?: return
        val embedding = runCatchingRec("decode feedback embedding") { bytesToEmbedding(bytes) } ?: return
        runCatchingRec("thumbs down dislike") {
            tasteRepository.onInteraction(Interaction(InteractionType.DISLIKE, embedding))
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    /**
     * Reranks the kNN [poolIds] with the pure [Reranker] against [reference] (the seed or taste vector),
     * blending relevance + novelty + exploration + MMR diversity + anti-repetition (weights per [params]).
     * Reads the persisted served-recency/count maps for the anti-repetition/exploration terms. Falls back
     * to the input order if reranking can't run. Returns the reranked row ids, best first.
     */
    private suspend fun rerank(
        reference: FloatArray,
        poolIds: List<Long>,
        byId: Map<Long, FloatArray>,
        limit: Int,
        params: RerankParams,
    ): List<Long> {
        if (poolIds.isEmpty()) return emptyList()
        val candidates = poolIds.mapNotNull { id -> byId[id]?.let { Candidate(id, it) } }
        if (candidates.isEmpty()) return poolIds.take(limit)

        val recency = runCatchingRec("read served recency") { tasteRepository.servedRecency() }.orEmpty()
        val counts = runCatchingRec("read served counts") { tasteRepository.servedCounts() }.orEmpty()

        return runCatchingRec("rerank") {
            Reranker.rank(
                taste = reference,
                candidates = candidates,
                servedRecency = recency,
                servedCounts = counts,
                limit = limit,
                params = params,
            ).map { it.id }
        } ?: poolIds.take(limit)
    }

    /** Records the just-served row [ids] so anti-repetition can down-weight them next time. Never throws. */
    private suspend fun recordServed(ids: List<Long>) {
        if (ids.isEmpty()) return
        runCatchingRec("record served") { tasteRepository.recordServed(ids) }
    }

    /** All current-version `(rowId, vector)` candidates, decoding the stored BLOBs. Never throws. */
    private suspend fun loadCandidates(): List<Pair<Long, FloatArray>> {
        val rows = runCatchingRec("load all embeddings") {
            songDao.getAllEmbeddings(ClapModel.MODEL_VERSION)
        } ?: return emptyList()
        return rows.mapNotNull { row ->
            runCatchingRec("decode embedding") { row.songId to bytesToEmbedding(row.embedding) }
        }
    }

    /**
     * The exclusion set for "more like this": the seed's own row id plus every candidate that shares the
     * seed's album (trivial duplicates). Only rows present in [candidates] need excluding.
     */
    private suspend fun buildExclusionSet(
        candidates: List<Pair<Long, FloatArray>>,
        seedEntity: SongEntity?,
    ): Set<Long> {
        if (seedEntity == null) return emptySet()
        val excluded = HashSet<Long>()
        excluded += seedEntity.id
        val albumId = seedEntity.albumId
        if (albumId != null) {
            val sameAlbumIds = runCatchingRec("load same-album ids") {
                songDao.getByIds(candidates.map { it.first }).filter { it.albumId == albumId }.map { it.id }
            }
            if (sameAlbumIds != null) excluded += sameAlbumIds
        }
        return excluded
    }

    /** The taste pool: liked songs first, then recently played, as Room row ids (deduped, order-kept). */
    private suspend fun tastePoolIds(): List<Long> {
        val liked = runCatchingRec("load liked") { songDao.getAllLiked().first() }.orEmpty()
        val recent = runCatchingRec("load recent") {
            songDao.getRecentlyPlayed(RECENT_LIMIT).first()
        }.orEmpty()
        // Liked first (a stronger signal), then recents; distinct keeps the first occurrence.
        return (liked + recent).map { it.id }.distinct()
    }

    /**
     * "Sounds like X" reasoning (P4) for the "For You" feed: for each result [songs]/[rankedIds] pair,
     * finds its NEAREST owned anchor from the taste [poolIds] (the liked/recently-played songs the taste
     * was built from) by cosine over the local embeddings ([byId]), and labels it
     * "Sounds like {anchor title}". A result with no computable anchor (missing embedding / no titled
     * anchor above [REASON_MIN_COSINE]) is simply left without a reason rather than fabricating one.
     */
    private suspend fun forYouReasons(
        songs: List<Song>,
        rankedIds: List<Long>,
        byId: Map<Long, FloatArray>,
        poolIds: Set<Long>,
    ): Map<MediaId, RecReason> {
        if (songs.isEmpty() || poolIds.isEmpty()) return emptyMap()
        // Anchor titles for the pool rows (only those with a usable local embedding can be an anchor).
        val anchors = runCatchingRec("load anchor rows") { songDao.getByIds(poolIds.toList()) }.orEmpty()
            .mapNotNull { entity ->
                val vector = byId[entity.id] ?: return@mapNotNull null
                val title = entity.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Triple(entity.id, vector, title)
            }
        if (anchors.isEmpty()) return emptyMap()

        // Ranked row id -> the result song's MediaId (drops rows that didn't resolve to a shown song).
        val mediaIdByRow = runCatchingRec("load reason rows") { songDao.getByIds(rankedIds) }.orEmpty()
            .associate { it.id to mediaIdFromColumns(it.idType, it.pluginId, it.sourceId) }
        val shownIds = songs.mapTo(HashSet()) { it.id }

        val reasons = HashMap<MediaId, RecReason>(songs.size)
        for (id in rankedIds) {
            val candidateVec = byId[id] ?: continue
            val mediaId = mediaIdByRow[id]?.takeIf { it in shownIds } ?: continue
            var bestTitle: String? = null
            var bestCos = REASON_MIN_COSINE
            for ((anchorId, anchorVec, anchorTitle) in anchors) {
                if (anchorId == id) continue
                val c = cosine(candidateVec, anchorVec)
                if (c > bestCos) {
                    bestCos = c
                    bestTitle = anchorTitle
                }
            }
            bestTitle?.let { reasons[mediaId] = RecReason(RecReason.Kind.SOUNDS_LIKE, it) }
        }
        return reasons
    }

    /** Cosine (== dot for unit vectors) of two equal-length embeddings; 0 on a dimension mismatch. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var acc = 0.0
        for (i in a.indices) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }

    /**
     * Resolves ranked Room row [ids] back to fully-hydrated domain [Song]s, preserving the ranking order.
     * Maps each row to its [MediaId] then hydrates through [MediaLibraryRepository.getSong] (artists +
     * album + playability). Rows that no longer resolve are dropped.
     */
    private suspend fun resolveSongs(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val entities = runCatchingRec("load ranked rows") { songDao.getByIds(ids) }.orEmpty()
        val mediaIdById = entities.associate { entity ->
            entity.id to mediaIdFromColumns(entity.idType, entity.pluginId, entity.sourceId)
        }
        return ids.mapNotNull { id ->
            val mediaId = mediaIdById[id] ?: return@mapNotNull null
            runCatchingRec("hydrate song") { mediaLibraryRepository.getSong(mediaId).first() }
        }
    }

    /**
     * The plugin related-songs fallback for [seed]. When smart recommendations are [disabled] and no
     * related songs come back, surface the disabled state rather than a generic empty. If a local pass
     * produced a few songs ([localFewSongs]) but the fallback is empty, keep those local songs.
     */
    private suspend fun fallback(
        seed: MediaId,
        limit: Int,
        disabled: Boolean = false,
        localFewSongs: List<Song> = emptyList(),
    ): RecResult {
        val related = runCatchingRec("plugin related songs") {
            pluginRepository.getRelatedSongs(seed).getOrNull()
        }?.items.orEmpty().take(limit)

        return when {
            related.isNotEmpty() -> RecResult.Fallback(related)
            localFewSongs.isNotEmpty() -> RecResult.Local(localFewSongs)
            disabled -> RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED)
            else -> RecResult.Empty(RecEmptyReason.NO_RESULTS)
        }
    }

    private suspend fun recommendationsEnabled(): Boolean =
        runCatchingRec("read setting") { settingsRepository.recommendationsEnabled.first() } ?: false

    /**
     * A day-bucketed exploration seed: deterministic within a calendar day (so a "For You" feed is
     * stable across a browsing session) yet advancing day-to-day so the Reranker's per-(id, seed)
     * exploration jitter surfaces different long-tail picks over time instead of the same fixed ones.
     */
    private fun explorationSeed(): Long = System.currentTimeMillis() / MS_PER_DAY

    /** Runs [block], logging and swallowing any failure to a null so recommendations never crash. */
    private inline fun <T> runCatchingRec(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Recommendation step failed: %s", what)
            null
        }

    private companion object {
        /** Below this many local hits, prefer the plugin's fuller related-songs feed. */
        const val MIN_LOCAL_RESULTS = 3

        /** Recently-played rows to fold into the taste pool. */
        const val RECENT_LIMIT = 50

        /** Cap on the taste pool so the centroid stays cheap and recent-biased. */
        const val TASTE_POOL_CAP = 100

        /** kNN pulls this many times `limit` as a shortlist for the reranker to diversify within. */
        const val RERANK_POOL_FACTOR = 4

        /**
         * Minimum cosine an anchor must reach to earn a "Sounds like X" reason — below this the nearest
         * owned song isn't a meaningful match, so the row shows no (fabricated) reason.
         */
        const val REASON_MIN_COSINE = 0.5f

        /** Milliseconds per day — the bucket size for the day-varying exploration seed. */
        const val MS_PER_DAY = 24L * 60 * 60 * 1000

        /**
         * Rerank weights for "For You": relevance-led but with real novelty/exploration/diversity so the
         * feed is a personalized *mix*, not just the taste's nearest neighbours (which would collapse the
         * filter bubble). Anti-repetition is on so the feed rotates between calls.
         */
        val FOR_YOU_PARAMS = RerankParams(
            relevanceWeight = 1.0f,
            noveltyWeight = 0.15f,
            explorationWeight = 0.12f,
            diversityWeight = 0.30f,
            antiRepetitionWeight = 0.50f,
        )

        /**
         * Rerank weights for "More like this": stay CLOSE to the seed (no novelty/exploration pull), but
         * still de-duplicate near-identical tracks (MMR) and avoid recently-served ids (anti-repetition).
         */
        val MORE_LIKE_THIS_PARAMS = RerankParams(
            relevanceWeight = 1.0f,
            noveltyWeight = 0.0f,
            explorationWeight = 0.0f,
            diversityWeight = 0.25f,
            antiRepetitionWeight = 0.30f,
        )
    }
}
