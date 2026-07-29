package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.mapper.mediaIdFromColumns
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.PlayRecord
import com.viperplayer.domain.rec.TasteModel
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TasteState
import com.viperplayer.domain.rec.TimeBucket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt-singleton implementation of [TasteRepository]. Persists the online-learned taste (and the small
 * recently-served ring) through a [TasteStore] as one compact JSON blob, rebuilds the taste from
 * listening history when cold/stale, and applies online [TasteModel.update] nudges as the data layer
 * feeds it learning signals.
 *
 * All state math is delegated to the pure [TasteModel]; this class owns only the DB/store glue and an
 * in-memory cache guarded by a [Mutex] so concurrent play/like signals serialize cleanly. Every public
 * method swallows failures to a safe default (a cold taste / empty maps) so recommendations never crash
 * on a corrupt blob or a DB hiccup.
 *
 * @param clock  injectable "now" so tests can drive recency/staleness deterministically.
 * @param zoneId injectable local time zone so the time-of-day bucket derivation is deterministic under
 *               a fake clock in tests (defaults to the device's zone in production).
 */
@Singleton
class TasteRepositoryImpl(
    private val store: TasteStore,
    private val songDao: SongDao,
    private val playHistoryDao: PlayHistoryDao,
    private val clock: () -> Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : TasteRepository {

    /** Hilt entry point: uses the system clock + device zone. Tests use the primary constructor. */
    @Inject
    constructor(
        store: TasteStore,
        songDao: SongDao,
        playHistoryDao: PlayHistoryDao,
    ) : this(store, songDao, playHistoryDao, clock = { System.currentTimeMillis() })

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    // In-memory cache of the persisted snapshot; loaded lazily on first access, kept in sync on writes.
    private var cached: Snapshot? = null

    override suspend fun taste(): TasteState = mutex.withLock {
        ensureFresh().toTasteState()
    }

    override suspend fun currentBucketTaste(): TasteState = mutex.withLock {
        val snap = ensureFresh()
        val bucket = snap.bucketTasteState(currentBucket(clock()))
        // Fall back to the global taste when the current bucket hasn't warmed up yet.
        if (bucket.isCold) snap.toTasteState() else bucket
    }

    override suspend fun moodCentroids(): List<FloatArray> = mutex.withLock {
        ensureFresh().moodCentroidsB64.mapNotNull { decodeVector(it) }
    }

    override suspend fun suppressDiscoveryId(id: String) {
        if (id.isBlank()) return
        mutex.withLock {
            val snap = loadOrInit()
            if (id in snap.suppressedDiscoveryIds) return@withLock // idempotent
            // Newest-first, bounded so the blob can't grow without limit.
            val next = (listOf(id) + snap.suppressedDiscoveryIds).take(SUPPRESSED_DISCOVERY_CAP)
            persist(snap.copy(suppressedDiscoveryIds = next))
        }
    }

    override suspend fun suppressedDiscoveryIds(): Set<String> = mutex.withLock {
        loadOrInit().suppressedDiscoveryIds.toSet()
    }

    /**
     * Loads the snapshot and, if the global taste is cold/stale, rebuilds the global taste + per-bucket
     * tastes + mood centroids from history in one pass, persisting the result. Returns the fresh snapshot.
     * Caller must hold [mutex].
     */
    private suspend fun ensureFresh(): Snapshot {
        val snap = loadOrInit()
        val state = snap.toTasteState()
        if (!isStale(state)) return snap

        val rebuilt = rebuildFromHistory()
        return if (!rebuilt.global.isCold) {
            val updated = snap.copy(
                vectorB64 = encodeVector(rebuilt.global.vector),
                interactions = rebuilt.global.interactions,
                rebuiltAtMs = rebuilt.global.rebuiltAtMs,
                // A rebuild reseeds the per-bucket tastes and mood centroids from history too, so they
                // track the same refreshed signal (online nudges then diverge them again between rebuilds).
                bucketVectorsB64 = rebuilt.bucketVectors.entries
                    .associate { (bucket, v) -> bucket.name to encodeVector(v)!! },
                bucketInteractions = rebuilt.bucketInteractions,
                moodCentroidsB64 = rebuilt.moodCentroids.map { encodeVector(it)!! },
            )
            persist(updated)
            updated
        } else {
            // Rebuild yielded no taste (cold/unindexed library, or a transient DB miss). Bump the
            // rebuild stamp WITHOUT touching any existing vector, so we don't re-scan the whole
            // history on every call while cold — we recheck at COLD_RECHECK_MS (see isStale). A
            // previously-warm vector that transiently rebuilds cold is kept rather than wiped.
            val updated = snap.copy(rebuiltAtMs = clock())
            persist(updated)
            updated
        }
    }

    override suspend fun onInteraction(interaction: Interaction) {
        mutex.withLock {
            val snap = loadOrInit()
            val now = clock()

            // Update the GLOBAL taste.
            val current = snap.toTasteState()
            val nextGlobal = TasteModel.update(current, interaction, nowMs = now)

            // Update the CURRENT time-of-day bucket's taste with the same math (P4).
            val bucket = currentBucket(now)
            val bucketCurrent = snap.bucketTasteState(bucket)
            val nextBucket = TasteModel.update(bucketCurrent, interaction, nowMs = now)

            val globalChanged = nextGlobal != current
            val bucketChanged = nextBucket != bucketCurrent
            if (!globalChanged && !bucketChanged) return@withLock

            var updated = snap
            if (globalChanged) {
                updated = updated.copy(
                    vectorB64 = encodeVector(nextGlobal.vector),
                    interactions = nextGlobal.interactions,
                    rebuiltAtMs = nextGlobal.rebuiltAtMs,
                )
            }
            if (bucketChanged) {
                updated = updated.withBucketTaste(bucket, nextBucket)
            }
            persist(updated)
        }
    }

    override suspend fun recordServed(servedIds: List<Long>) {
        if (servedIds.isEmpty()) return
        mutex.withLock {
            val snap = loadOrInit()
            // Decay every existing entry, then set the just-served ids to full recency and bump counts.
            val recency = HashMap<Long, Float>(snap.servedRecency.size + servedIds.size)
            for ((id, w) in snap.servedRecency) {
                val decayed = w * SERVED_DECAY
                if (decayed >= SERVED_MIN) recency[id] = decayed
            }
            val counts = HashMap(snap.servedCounts)
            for (id in servedIds) {
                recency[id] = 1f
                counts[id] = (counts[id] ?: 0) + 1
            }
            // Bound the ring: keep the most-recent entries so the blob can't grow without limit.
            val boundedRecency = if (recency.size > SERVED_RING_CAP) {
                recency.entries.sortedByDescending { it.value }.take(SERVED_RING_CAP)
                    .associate { it.key to it.value }
            } else recency
            // Counts are a longer-lived uncertainty signal (they drive the exploration term) than the
            // short, fast-decaying recency ring, so bound them INDEPENDENTLY by their own (larger) cap
            // rather than dropping a count the moment its id ages out of recency — otherwise a
            // heavily-served id would be re-treated as brand-new and re-explored the instant it decays.
            val boundedCounts = if (counts.size > SERVED_COUNTS_CAP) {
                counts.entries.sortedByDescending { it.value }.take(SERVED_COUNTS_CAP)
                    .associate { it.key to it.value }
            } else counts
            persist(snap.copy(servedRecency = boundedRecency, servedCounts = boundedCounts))
        }
    }

    override suspend fun servedRecency(): Map<Long, Float> = mutex.withLock {
        loadOrInit().servedRecency
    }

    override suspend fun servedCounts(): Map<Long, Int> = mutex.withLock {
        loadOrInit().servedCounts
    }

    override suspend fun invalidate() {
        mutex.withLock {
            val snap = loadOrInit()
            // Force a rebuild next read by zeroing the rebuild stamp (keeps the served ring intact).
            persist(snap.copy(rebuiltAtMs = 0L))
        }
    }

    // --- history rebuild ---------------------------------------------------------------------------

    /**
     * The output of a full history rebuild: the global taste, the per-bucket tastes (only warm buckets
     * are present), and the mood centroids. Cold when nothing is embedded/available.
     */
    private data class RebuildResult(
        val global: TasteState,
        val bucketVectors: Map<TimeBucket, FloatArray>,
        val bucketInteractions: Map<String, Int>,
        val moodCentroids: List<FloatArray>,
    ) {
        companion object {
            val COLD = RebuildResult(TasteState.COLD, emptyMap(), emptyMap(), emptyList())
        }
    }

    /**
     * Rebuilds the taste from listening history + likes via the pure [TasteModel]: the global centroid
     * ([TasteModel.deriveTaste]), a per-time-of-day-bucket centroid over that bucket's plays (P4), and
     * the mood centroids ([TasteModel.deriveMoodCentroids]) over all play+liked embeddings. Resolves each
     * play's stored embedding (streaming-only plays with no local vector are skipped) and folds in
     * liked-song embeddings. Returns [RebuildResult.COLD] when nothing is embedded/available.
     */
    private suspend fun rebuildFromHistory(): RebuildResult {
        val embeddingsByRow = runCatching {
            songDao.getAllEmbeddings(ClapModel.MODEL_VERSION)
                .associate { it.songId to it.embedding }
        }.getOrElse {
            Timber.w(it, "TasteRepository: failed to load embeddings for rebuild")
            return RebuildResult.COLD
        }
        if (embeddingsByRow.isEmpty()) return RebuildResult.COLD

        // Map each embedded row's encoded MediaId -> its decoded vector, so play-history rows (keyed by
        // the encoded mediaId) can look up their embedding without a per-row DB call.
        val vectorByMediaId = HashMap<String, FloatArray>(embeddingsByRow.size)
        val rows = runCatching { songDao.getByIds(embeddingsByRow.keys.toList()) }.getOrNull().orEmpty()
        for (row in rows) {
            val bytes = embeddingsByRow[row.id] ?: continue
            val encodedId = runCatching {
                mediaIdFromColumns(row.idType, row.pluginId, row.sourceId).encode()
            }.getOrNull() ?: continue
            val vector = runCatching { bytesToEmbedding(bytes) }.getOrNull() ?: continue
            vectorByMediaId[encodedId] = vector
        }

        // Plays paired with their local-time bucket, so we can build a per-bucket centroid.
        val bucketedPlays = runCatching {
            playHistoryDao.observeAll().first().take(HISTORY_LIMIT)
        }.getOrNull().orEmpty().mapNotNull { play ->
            val vector = vectorByMediaId[play.mediaId] ?: return@mapNotNull null
            val record = PlayRecord(
                embedding = vector,
                timestampMs = play.timestamp,
                completion = completionOf(play.listenedMs, play.durationMs),
            )
            bucketOf(play.timestamp) to record
        }
        val plays = bucketedPlays.map { it.second }

        val likedVectors = runCatching { songDao.getAllLiked().first() }.getOrNull().orEmpty()
            .mapNotNull { entity ->
                embeddingsByRow[entity.id]?.let { b -> runCatching { bytesToEmbedding(b) }.getOrNull() }
            }

        val now = clock()
        val global = TasteModel.deriveTaste(plays, likedVectors, nowMs = now)
        if (global.isCold) return RebuildResult.COLD

        // Per-bucket centroids: derive from just that bucket's plays (likes are recency-agnostic and
        // shared across buckets, so they seed every warm bucket too).
        val bucketVectors = HashMap<TimeBucket, FloatArray>()
        val bucketInteractions = HashMap<String, Int>()
        for ((bucket, bucketPlays) in bucketedPlays.groupBy({ it.first }, { it.second })) {
            val bucketTaste = TasteModel.deriveTaste(bucketPlays, likedVectors, nowMs = now)
            val v = bucketTaste.vector ?: continue
            bucketVectors[bucket] = v
            bucketInteractions[bucket.name] = 0 // reseeded ⇒ reset the online counter for this bucket
        }

        // Mood centroids over all play + liked embeddings.
        val moodCentroids = TasteModel.deriveMoodCentroids(
            plays.map { it.embedding } + likedVectors,
            k = TasteModel.DEFAULT_MOOD_CENTROIDS,
        )

        return RebuildResult(global, bucketVectors, bucketInteractions, moodCentroids)
    }

    /** The current time-of-day bucket for wall-clock [nowMs] in the injected [zoneId]. Deterministic. */
    private fun currentBucket(nowMs: Long): TimeBucket = bucketOf(nowMs)

    private fun bucketOf(epochMs: Long): TimeBucket {
        val hour = Instant.ofEpochMilli(epochMs).atZone(zoneId).hour
        return TimeBucket.fromHour(hour)
    }

    // --- persistence -------------------------------------------------------------------------------

    /** Loads the snapshot from the store into the cache on first access; returns the cache thereafter. */
    private suspend fun loadOrInit(): Snapshot {
        cached?.let { return it }
        val raw = store.read()
        val snap = raw?.let { runCatching { json.decodeFromString<Snapshot>(it) }.getOrNull() }
            ?: Snapshot()
        cached = snap
        return snap
    }

    private suspend fun persist(snap: Snapshot) {
        cached = snap
        runCatching { store.write(json.encodeToString(snap)) }
            .onFailure { Timber.w(it, "TasteRepository: failed to persist taste") }
    }

    private fun Snapshot.toTasteState(): TasteState =
        TasteState(
            vector = decodeVector(vectorB64),
            interactions = interactions,
            rebuiltAtMs = rebuiltAtMs,
        )

    /** The persisted taste for [bucket], or [TasteState.COLD] when that bucket has no vector yet. */
    private fun Snapshot.bucketTasteState(bucket: TimeBucket): TasteState {
        val vector = decodeVector(bucketVectorsB64[bucket.name]) ?: return TasteState.COLD
        // Buckets share the global rebuild stamp for staleness (they're only rebuilt alongside it).
        return TasteState(
            vector = vector,
            interactions = bucketInteractions[bucket.name] ?: 0,
            rebuiltAtMs = rebuiltAtMs,
        )
    }

    /** Returns a copy of this snapshot with [bucket]'s taste set to [state] (dropped when [state] is cold). */
    private fun Snapshot.withBucketTaste(bucket: TimeBucket, state: TasteState): Snapshot {
        val encoded = encodeVector(state.vector)
        val vectors = HashMap(bucketVectorsB64)
        val counts = HashMap(bucketInteractions)
        if (encoded != null) {
            vectors[bucket.name] = encoded
            counts[bucket.name] = state.interactions
        } else {
            vectors.remove(bucket.name)
            counts.remove(bucket.name)
        }
        return copy(bucketVectorsB64 = vectors, bucketInteractions = counts)
    }

    private fun isStale(state: TasteState): Boolean {
        // While cold, recheck often (a library that's still indexing should warm up promptly); once
        // warm, rebuild at most daily. A stamp of 0 (never built / invalidated) is always stale.
        val interval = if (state.isCold) COLD_RECHECK_MS else REBUILD_INTERVAL_MS
        return (clock() - state.rebuiltAtMs) >= interval
    }

    private fun encodeVector(vector: FloatArray?): String? =
        vector?.let { Base64.getEncoder().encodeToString(embeddingToBytes(it)) }

    private fun decodeVector(b64: String?): FloatArray? =
        b64?.let {
            runCatching { bytesToEmbedding(Base64.getDecoder().decode(it)) }.getOrNull()
        }

    private fun completionOf(listenedMs: Long, durationMs: Long): Float =
        if (durationMs > 0L) (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 1f

    /**
     * The persisted snapshot: the taste vector (base64 of its float32-LE bytes), learning counters, and
     * the recently-served ring (id → recency weight) + per-id served counts. Serialized as one JSON blob.
     *
     * P4 adds four backward-compatible fields (all default to empty/absent, so an old P2/P3 blob loads
     * unchanged — `ignoreUnknownKeys` on read, `encodeDefaults` on write):
     *  - [bucketVectorsB64]  per time-of-day bucket taste vectors (bucket name → base64), absent when cold;
     *  - [bucketInteractions] per-bucket learning counters;
     *  - [moodCentroidsB64]  the base64 mood centroids from the last rebuild;
     *  - [suppressedDiscoveryIds] thumbed-down server-Discovery ids to exclude from the feed.
     */
    @Serializable
    private data class Snapshot(
        val vectorB64: String? = null,
        val interactions: Int = 0,
        val rebuiltAtMs: Long = 0L,
        val servedRecency: Map<Long, Float> = emptyMap(),
        val servedCounts: Map<Long, Int> = emptyMap(),
        val bucketVectorsB64: Map<String, String> = emptyMap(),
        val bucketInteractions: Map<String, Int> = emptyMap(),
        val moodCentroidsB64: List<String> = emptyList(),
        val suppressedDiscoveryIds: List<String> = emptyList(),
    )

    internal companion object {
        /** Rebuild the taste from history at most this often (else the cached/online-updated one is used). */
        const val REBUILD_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24h

        /**
         * While still cold (nothing embedded / no taste yet), recheck history this often instead of on
         * every call — a library that's actively indexing should warm up promptly, but not at the cost
         * of a full multi-DAO scan per `taste()` invocation.
         */
        const val COLD_RECHECK_MS = 5L * 60 * 1000 // 5 min

        /** Cap on plays folded into a rebuild (newest first) — keeps the centroid recent-biased & cheap. */
        const val HISTORY_LIMIT = 500

        /** Per-serve decay applied to every recency entry (each new serve ages the previous ones). */
        const val SERVED_DECAY = 0.85f

        /** Drop a served entry once its recency decays below this (bounds the ring). */
        const val SERVED_MIN = 0.05f

        /** Hard cap on the served ring size so the persisted blob stays small. */
        const val SERVED_RING_CAP = 200

        /**
         * Hard cap on the retained per-id served counts — bounded independently of (and larger than)
         * the recency ring, since counts feed the exploration term and outlive the fast recency decay.
         */
        const val SERVED_COUNTS_CAP = 1000

        /** Hard cap on the thumbed-down Discovery id suppression list so the blob stays bounded. */
        const val SUPPRESSED_DISCOVERY_CAP = 500
    }
}
