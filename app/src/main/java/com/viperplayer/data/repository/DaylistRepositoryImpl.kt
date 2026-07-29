package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.GenreDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.mapper.mediaIdFromColumns
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.EmbeddingKnn
import com.viperplayer.data.rec.bytesToEmbedding
import com.viperplayer.domain.model.Daylist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.DaylistTitleGenerator
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TimeBucket
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device implementation of [DaylistRepository]. Builds a daylist for the current
 * time-of-day slot from the user's per-bucket taste + the local CLAP embedding index, reusing the same
 * kNN machinery ([EmbeddingKnn]) as "For You" — no login, no network.
 *
 * One slot = one [TimeBucket] on a calendar day. The daylist is CACHED per `(bucket, epochDay)` so the
 * same slot returns the same list within its window, and REGENERATES when the bucket/day rolls over
 * (a fresh kNN + a freshly-seeded generated title). All naming math is delegated to the pure
 * [DaylistTitleGenerator]; this class owns only the DB/index glue.
 *
 * Everything degrades gracefully — [currentDaylist] returns `null` (never throws) whenever a daylist
 * can't be produced (recommendations off, cold bucket taste, or an unindexed library).
 *
 * @param clock  injectable "now" (epoch ms) so tests can drive the slot/seed deterministically.
 * @param zoneId injectable local zone so the bucket + epoch-day derivation is deterministic under a fake
 *               clock (defaults to the device zone in production).
 */
@Singleton
class DaylistRepositoryImpl(
    private val songDao: SongDao,
    private val genreDao: GenreDao,
    private val crossRefDao: CrossRefDao,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val tasteRepository: TasteRepository,
    private val clock: () -> Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DaylistRepository {

    /** Hilt entry point: uses the system clock + device zone. Tests use the primary constructor. */
    @Inject
    constructor(
        songDao: SongDao,
        genreDao: GenreDao,
        crossRefDao: CrossRefDao,
        mediaLibraryRepository: MediaLibraryRepository,
        settingsRepository: SettingsRepository,
        tasteRepository: TasteRepository,
    ) : this(
        songDao = songDao,
        genreDao = genreDao,
        crossRefDao = crossRefDao,
        mediaLibraryRepository = mediaLibraryRepository,
        settingsRepository = settingsRepository,
        tasteRepository = tasteRepository,
        clock = { System.currentTimeMillis() },
    )

    private val mutex = Mutex()

    /** The cached daylist for the slot key it was built for; regenerated when the key rolls over. */
    private data class Cached(val key: SlotKey, val daylist: Daylist)

    /** Identifies one daylist slot: a time-of-day [bucket] on a calendar [epochDay]. */
    private data class SlotKey(val bucket: TimeBucket, val epochDay: Long)

    private var cached: Cached? = null

    override suspend fun currentDaylist(): Daylist? = mutex.withLock {
        // Always re-check the opt-in first: a daylist must vanish the moment recommendations are turned
        // off, even mid-slot, so gate BEFORE the cache (and drop any cached slot when disabled).
        if (!recommendationsEnabled()) {
            cached = null
            return@withLock null
        }

        val now = clock()
        val key = SlotKey(bucket = bucketOf(now), epochDay = epochDayOf(now))

        // Same slot as last time → return the cached daylist (stable within its window).
        cached?.let { if (it.key == key) return@withLock it.daylist }

        val fresh = runCatchingDaylist("build daylist") { build(key, now) }
        if (fresh != null) cached = Cached(key, fresh)
        fresh
    }

    /**
     * Builds the daylist for [key] at wall-clock [now], or null when one can't be produced. The
     * recommendations opt-in is already checked by [currentDaylist]; this gates on a warm bucket taste +
     * an indexed library, then runs a taste-centered kNN, aggregates the picked songs' top genres, and
     * asks [DaylistTitleGenerator] to name the slot.
     */
    private suspend fun build(key: SlotKey, now: Long): Daylist? {
        // 1) Gate: a warm bucket taste (currentBucketTaste falls back to global; both cold ⇒ no daylist).
        val taste = runCatchingDaylist("read bucket taste") { tasteRepository.currentBucketTaste() }
        val tasteVector = taste?.vector ?: return null

        // 2) Gate: an indexed library to draw candidates from.
        val candidates = loadCandidates()
        if (candidates.isEmpty()) return null

        // 3) kNN around the bucket taste for a slot-sized shortlist, excluding the taste's own pool
        //    (liked + recently played) so the daylist surfaces songs the taste was NOT built from.
        val poolIds = tastePoolIds()
        val rankedIds = EmbeddingKnn.cosineTopK(
            query = tasteVector,
            candidates = candidates,
            k = SLOT_SIZE,
            excludeIds = poolIds,
        ).map { it.id }
        // A near-empty library (everything owned/excluded) shouldn't yield a one-song "daylist".
        if (rankedIds.size < MIN_SONGS) return null

        val songsByRow = resolveSongs(rankedIds)
        if (songsByRow.size < MIN_SONGS) return null
        val songs = songsByRow.values.toList()

        // 4) Name the slot from the picked songs' dominant genres + the slot's bucket/weekday/seed.
        val dominantGenres = dominantGenres(songsByRow)
        val naming = DaylistTitleGenerator.generate(
            dominantGenres = dominantGenres,
            bucket = key.bucket,
            dayOfWeek = Instant.ofEpochMilli(now).atZone(zoneId).dayOfWeek,
            daySeed = daySeed(key),
        )

        return Daylist(
            bucket = key.bucket,
            title = naming.title,
            description = naming.description,
            songs = songs,
            generatedAtMs = now,
        )
    }

    // --- genre aggregation -------------------------------------------------------------------------

    /**
     * The playlist's dominant genres (most-frequent first) over the picked `rowId → Song` map. For each
     * song it counts the normalized song_genres cross-ref (via [CrossRefDao]/[GenreDao]) when that row has
     * one, and ONLY otherwise falls back to the song's own carried [Song.genres] tags (covering streaming
     * rows with no cross-ref) — a per-row fallback, so a row is never counted from both sources. Ties
     * break by first appearance so the result is deterministic.
     */
    private suspend fun dominantGenres(songsByRow: Map<Long, Song>): List<String> {
        val counts = LinkedHashMap<String, Int>()
        val rowIds = songsByRow.keys.toList()

        // Cross-ref genres, keyed by Room row id (only local library rows have these).
        val genreIdsByRow = runCatchingDaylist("load song genre ids") {
            rowIds.associateWith { crossRefDao.getGenreIdsForSong(it) }
        }.orEmpty()
        val allGenreIds = genreIdsByRow.values.flatten().distinct()
        val genreNameById = if (allGenreIds.isEmpty()) {
            emptyMap()
        } else {
            runCatchingDaylist("load genre names") {
                genreDao.getByIds(allGenreIds).associate { it.id to it.name }
            }.orEmpty()
        }

        for ((rowId, song) in songsByRow) {
            // A row's cross-ref genre NAMES (some ids may not resolve to a name).
            val crossRefNames = genreIdsByRow[rowId].orEmpty()
                .mapNotNull { gid -> genreNameById[gid]?.trim()?.takeIf { it.isNotEmpty() } }
            if (crossRefNames.isNotEmpty()) {
                for (name in crossRefNames) counts.merge(name, 1, Int::plus)
            } else {
                // No cross-ref for this row → fall back to its own carried tags (streaming rows).
                for (raw in song.genres) {
                    val name = raw.trim()
                    if (name.isNotEmpty()) counts.merge(name, 1, Int::plus)
                }
            }
        }

        return counts.entries
            .sortedByDescending { it.value } // stable sort keeps first-appearance order among ties
            .map { it.key }
            .take(TOP_GENRES)
    }

    // --- shared glue (mirrors RecommendationRepositoryImpl) ----------------------------------------

    /** All current-version `(rowId, vector)` candidates, decoding the stored BLOBs. Never throws. */
    private suspend fun loadCandidates(): List<Pair<Long, FloatArray>> {
        val rows = runCatchingDaylist("load all embeddings") {
            songDao.getAllEmbeddings(ClapModel.MODEL_VERSION)
        } ?: return emptyList()
        return rows.mapNotNull { row ->
            runCatchingDaylist("decode embedding") { row.songId to bytesToEmbedding(row.embedding) }
        }
    }

    /** The taste pool (liked + recently played) as a set of Room row ids, to exclude from the daylist. */
    private suspend fun tastePoolIds(): Set<Long> {
        val liked = runCatchingDaylist("load liked") { songDao.getAllLiked().first() }.orEmpty()
        val recent = runCatchingDaylist("load recent") {
            songDao.getRecentlyPlayed(RECENT_LIMIT).first()
        }.orEmpty()
        return (liked + recent).mapTo(HashSet()) { it.id }
    }

    /**
     * Resolves ranked Room row [ids] back to fully-hydrated domain [Song]s, as an ordered
     * `rowId → Song` map (ranking order preserved). Rows that no longer resolve are dropped. The row-id
     * key lets [dominantGenres] pair each song with its own cross-ref genres for a per-row fallback.
     */
    private suspend fun resolveSongs(ids: List<Long>): Map<Long, Song> {
        if (ids.isEmpty()) return emptyMap()
        val entities = runCatchingDaylist("load ranked rows") { songDao.getByIds(ids) }.orEmpty()
        val mediaIdById = entities.associate { entity ->
            entity.id to mediaIdFromColumns(entity.idType, entity.pluginId, entity.sourceId)
        }
        val out = LinkedHashMap<Long, Song>(ids.size)
        for (id in ids) {
            val mediaId = mediaIdById[id] ?: continue
            val song = runCatchingDaylist("hydrate song") {
                mediaLibraryRepository.getSong(mediaId).first()
            } ?: continue
            out[id] = song
        }
        return out
    }

    private suspend fun recommendationsEnabled(): Boolean =
        runCatchingDaylist("read setting") { settingsRepository.recommendationsEnabled.first() } ?: false

    // --- slot / seed derivation --------------------------------------------------------------------

    /** The time-of-day bucket for wall-clock [nowMs] in the injected zone. */
    private fun bucketOf(nowMs: Long): TimeBucket =
        TimeBucket.fromHour(Instant.ofEpochMilli(nowMs).atZone(zoneId).hour)

    /** The local calendar day (epoch-day) for wall-clock [nowMs] in the injected zone. */
    private fun epochDayOf(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()

    /**
     * A stable, deterministic per-slot seed for [DaylistTitleGenerator]: `epochDay * 4 + bucket.ordinal`.
     * Constant within a slot (so the title never changes mid-window) yet distinct across every slot/day,
     * so the generator rotates its vocabulary from slot to slot.
     */
    private fun daySeed(key: SlotKey): Long = key.epochDay * TimeBucket.entries.size + key.bucket.ordinal

    /** Runs [block], logging and swallowing any failure to null so the daylist never crashes the app. */
    private inline fun <T> runCatchingDaylist(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Daylist step failed: %s", what)
            null
        }

    private companion object {
        /** Target songs per daylist slot (~25 — a full sitting without overloading the kNN). */
        const val SLOT_SIZE = 25

        /** Below this many resolved songs, a daylist isn't worth surfacing. */
        const val MIN_SONGS = 5

        /** Recently-played rows folded into the exclude pool. */
        const val RECENT_LIMIT = 50

        /** How many dominant genres to feed the title generator. */
        const val TOP_GENRES = 3
    }
}
