package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.mapper.mediaIdFromColumns
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.PlayRecord
import com.viperplayer.domain.rec.TasteModel
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TasteState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
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
 * @param clock injectable "now" so tests can drive recency/staleness deterministically.
 */
@Singleton
class TasteRepositoryImpl(
    private val store: TasteStore,
    private val songDao: SongDao,
    private val playHistoryDao: PlayHistoryDao,
    private val clock: () -> Long,
) : TasteRepository {

    /** Hilt entry point: uses the system clock. Tests use the primary constructor with a fake clock. */
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
        val snap = loadOrInit()
        val state = snap.toTasteState()
        if (isStale(state)) {
            val rebuilt = rebuildFromHistory()
            if (!rebuilt.isCold) {
                persist(
                    snap.copy(
                        vectorB64 = encodeVector(rebuilt.vector),
                        interactions = rebuilt.interactions,
                        rebuiltAtMs = rebuilt.rebuiltAtMs,
                    )
                )
                return@withLock rebuilt
            }
            // Rebuild yielded no taste (cold/unindexed library, or a transient DB miss). Bump the
            // rebuild stamp WITHOUT touching any existing vector, so we don't re-scan the whole
            // history on every call while cold — we recheck at COLD_RECHECK_MS (see isStale). A
            // previously-warm vector that transiently rebuilds cold is kept rather than wiped.
            persist(snap.copy(rebuiltAtMs = clock()))
        }
        state
    }

    override suspend fun onInteraction(interaction: Interaction) {
        mutex.withLock {
            val snap = loadOrInit()
            val current = snap.toTasteState()
            val next = TasteModel.update(current, interaction, nowMs = clock())
            if (next != current) {
                persist(
                    snap.copy(
                        vectorB64 = encodeVector(next.vector),
                        interactions = next.interactions,
                        rebuiltAtMs = next.rebuiltAtMs,
                    )
                )
            }
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
     * Rebuilds the taste centroid from listening history + likes via the pure [TasteModel.deriveTaste].
     * Resolves each play's stored embedding (streaming-only plays with no local vector are skipped) and
     * folds in liked-song embeddings. Returns [TasteState.COLD] when nothing is embedded/available.
     */
    private suspend fun rebuildFromHistory(): TasteState {
        val embeddingsByRow = runCatching {
            songDao.getAllEmbeddings(ClapModel.MODEL_VERSION)
                .associate { it.songId to it.embedding }
        }.getOrElse {
            Timber.w(it, "TasteRepository: failed to load embeddings for rebuild")
            return TasteState.COLD
        }
        if (embeddingsByRow.isEmpty()) return TasteState.COLD

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

        val plays = runCatching {
            playHistoryDao.observeAll().first().take(HISTORY_LIMIT)
        }.getOrNull().orEmpty().mapNotNull { play ->
            val vector = vectorByMediaId[play.mediaId] ?: return@mapNotNull null
            PlayRecord(
                embedding = vector,
                timestampMs = play.timestamp,
                completion = completionOf(play.listenedMs, play.durationMs),
            )
        }

        val likedVectors = runCatching { songDao.getAllLiked().first() }.getOrNull().orEmpty()
            .mapNotNull { entity ->
                embeddingsByRow[entity.id]?.let { b -> runCatching { bytesToEmbedding(b) }.getOrNull() }
            }

        return TasteModel.deriveTaste(plays, likedVectors, nowMs = clock())
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
     */
    @Serializable
    private data class Snapshot(
        val vectorB64: String? = null,
        val interactions: Int = 0,
        val rebuiltAtMs: Long = 0L,
        val servedRecency: Map<Long, Float> = emptyMap(),
        val servedCounts: Map<Long, Int> = emptyMap(),
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
    }
}
