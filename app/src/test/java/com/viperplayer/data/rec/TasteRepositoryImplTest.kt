package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.data.stats.PlayHistoryEntity
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.InteractionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [TasteRepositoryImpl] over an in-memory store + fake DAOs: the persistence round-trips
 * across a fresh instance, an online like-nudge converges the taste toward the liked cluster, the taste
 * rebuilds from history on first use, and the served ring records + decays for anti-repetition.
 */
class TasteRepositoryImplTest {

    private val dim get() = com.viperplayer.data.rec.ClapModel.EMBEDDING_DIM

    /** A 512-d unit vector along axis [k]. */
    private fun axis(k: Int): FloatArray {
        val v = FloatArray(dim)
        v[k] = 1f
        return v
    }

    private fun bytes(v: FloatArray): ByteArray = embeddingToBytes(v)

    private fun cos(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        for (i in a.indices) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }

    private fun unit(vararg pairs: Pair<Int, Float>): FloatArray {
        val v = FloatArray(dim)
        for ((k, x) in pairs) v[k] = x
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        val inv = (1.0 / sqrt(n)).toFloat()
        for (i in v.indices) v[i] = v[i] * inv
        return v
    }

    private val now = 1_700_000_000_000L

    private fun repo(
        store: TasteStore = InMemoryTasteStore(),
        songDao: SongDao = FakeSongDao(),
        historyDao: PlayHistoryDao = FakeHistoryDao(),
        clock: () -> Long = { now },
    ) = TasteRepositoryImpl(store, songDao, historyDao, clock)

    // --- persistence --------------------------------------------------------------------------------

    @Test
    fun persistence_roundTripsAcrossInstances() = runTest {
        val store = InMemoryTasteStore()
        val a = repo(store = store)
        // Seed a taste via an online like, which persists it.
        a.onInteraction(Interaction(InteractionType.LIKE, axis(3)))
        val persistedVector = a.taste().vector!!

        // A fresh instance sharing the same store reads back the identical taste (round-trip).
        val b = repo(store = store)
        val reloaded = b.taste().vector!!
        assertEquals(persistedVector.size, reloaded.size)
        assertTrue("reloaded taste matches persisted", cos(persistedVector, reloaded) > 0.9999f)
    }

    @Test
    fun servedRing_recordsAndRoundTrips() = runTest {
        val store = InMemoryTasteStore()
        val a = repo(store = store)
        a.recordServed(listOf(10L, 20L))

        val recency = a.servedRecency()
        assertEquals(setOf(10L, 20L), recency.keys)
        assertEquals(1f, recency[10L]!!, 0f)
        assertEquals(mapOf(10L to 1, 20L to 1), a.servedCounts())

        // A new serve decays the previous entries and refreshes the new one; counts accumulate.
        a.recordServed(listOf(30L))
        val recency2 = a.servedRecency()
        assertTrue("previous entry decayed below 1.0", recency2[10L]!! < 1f)
        assertEquals(1f, recency2[30L]!!, 0f)

        // Persisted: a fresh instance sees the same ring.
        val b = repo(store = store)
        assertEquals(a.servedRecency().keys, b.servedRecency().keys)
    }

    // --- online convergence -------------------------------------------------------------------------

    @Test
    fun onInteraction_repeatedLikesConvergeTowardCluster() = runTest {
        val r = repo()
        val cluster = unit(5 to 1f, 6 to 1f, 7 to 1f)
        // Seed elsewhere, then repeatedly like the cluster.
        r.onInteraction(Interaction(InteractionType.LIKE, axis(0)))
        val before = cos(r.taste().vector!!, cluster)
        repeat(25) { r.onInteraction(Interaction(InteractionType.LIKE, cluster)) }
        val after = cos(r.taste().vector!!, cluster)
        assertTrue("repeated likes move taste toward the cluster", after > before)
        assertTrue("taste ends close to the cluster", after > 0.9f)
    }

    @Test
    fun onInteraction_skipMovesTasteAway() = runTest {
        val r = repo()
        r.onInteraction(Interaction(InteractionType.LIKE, unit(0 to 1f, 1 to 1f)))
        val disliked = axis(0)
        val before = cos(r.taste().vector!!, disliked)
        r.onInteraction(Interaction(InteractionType.SKIP, disliked, completion = 0f))
        val after = cos(r.taste().vector!!, disliked)
        assertTrue("a skip reduces similarity to the skipped song", after < before)
    }

    // --- rebuild from history -----------------------------------------------------------------------

    @Test
    fun taste_rebuildsFromLikesOnFirstUse() = runTest {
        // Row 1 (liked) is embedded along axis 2 → a cold-start rebuild produces a warm taste toward it.
        // (The like path keys purely by row id, so it needs no android.net.Uri — pure-JVM testable. The
        // play-history join is exercised by the repository/reranker tests + on-device.)
        val song = SongEntity(id = 1L, idType = "local", pluginId = "", sourceId = "s1", title = "s1")
        val songDao = FakeSongDao(
            embeddings = listOf(SongEmbeddingRow(1L, bytes(axis(2)))),
            rows = listOf(song),
            liked = listOf(song),
        )
        val r = repo(songDao = songDao)
        val taste = r.taste()
        assertFalse("a like produces a warm taste on rebuild", taste.isCold)
        assertTrue("taste points toward the liked song", cos(taste.vector!!, axis(2)) > 0.9f)
    }

    @Test
    fun taste_coldWhenNothingEmbedded() = runTest {
        val r = repo() // empty DAOs
        assertTrue(r.taste().isCold)
    }

    @Test
    fun taste_coldLibrary_doesNotRescanHistoryOnEveryCall() = runTest {
        // A cold/unindexed library must not re-run the full history scan on every taste() call — the
        // rebuild stamp is bumped so repeated calls within COLD_RECHECK_MS reuse the cold state.
        val counting = FakeSongDao() // empty embeddings ⇒ stays cold
        var t = now
        val r = repo(songDao = counting, clock = { t })
        r.taste() // first call: one scan
        r.taste() // within the recheck window: no additional scan
        r.taste()
        assertEquals("cold rechecks are throttled, not per-call", 1, counting.getAllEmbeddingsCalls)

        // Advance past the cold-recheck window → it rescans once more.
        t += TasteRepositoryImpl.COLD_RECHECK_MS + 1
        r.taste()
        assertEquals(2, counting.getAllEmbeddingsCalls)
    }

    @Test
    fun servedCounts_survivePastRecencyEviction() = runTest {
        // An id's served COUNT must persist even after it decays out of the short recency ring, so the
        // exploration term keeps seeing it as previously-served (not brand-new) once it ages out.
        val store = InMemoryTasteStore()
        val r = repo(store = store)
        r.recordServed(listOf(7L))
        // Serve enough OTHER ids to decay 7L's recency below SERVED_MIN (0.85^n < 0.05 ⇒ n ≥ 19).
        repeat(25) { r.recordServed(listOf(1000L + it)) }

        assertFalse("7L has aged out of the recency ring", 7L in r.servedRecency())
        assertEquals("but its served count is retained", 1, r.servedCounts()[7L])
    }

    // --- fakes --------------------------------------------------------------------------------------

    private class InMemoryTasteStore : TasteStore {
        private var blob: String? = null
        override suspend fun read(): String? = blob
        override suspend fun write(blob: String) { this.blob = blob }
    }

    private class FakeHistoryDao(private val plays: List<PlayHistoryEntity> = emptyList()) : PlayHistoryDao {
        override suspend fun insert(event: PlayHistoryEntity): Long = 0L
        override fun observeAll(): Flow<List<PlayHistoryEntity>> = flowOf(plays)
        override fun observeCount(): Flow<Int> = flowOf(plays.size)
        override suspend fun clearAll() = Unit
    }

    /** Minimal [SongDao] fake serving only the methods [TasteRepositoryImpl] touches. */
    private class FakeSongDao(
        private val embeddings: List<SongEmbeddingRow> = emptyList(),
        private val rows: List<SongEntity> = emptyList(),
        private val liked: List<SongEntity> = emptyList(),
    ) : SongDao {
        /** Counts history-scan entries so tests can assert the cold-recheck throttle. */
        var getAllEmbeddingsCalls = 0
            private set

        override suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow> {
            getAllEmbeddingsCalls++
            return embeddings
        }
        override suspend fun getByIds(ids: List<Long>): List<SongEntity> = rows.filter { it.id in ids }
        override fun getAllLiked(): Flow<List<SongEntity>> = flowOf(liked)

        private fun nope(): Nothing = throw NotImplementedError("FakeSongDao: method not faked")
        override suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity? = nope()
        override fun getById(id: Long): Flow<SongEntity?> = nope()
        override suspend fun getByIdSync(id: Long): SongEntity? = nope()
        override fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?> = nope()
        override fun getByAlbum(albumId: Long): Flow<List<SongEntity>> = nope()
        override fun getAllSaved(): Flow<List<SongEntity>> = nope()
        override fun getAllSavedByDateAddedDesc(): Flow<List<SongEntity>> = nope()
        override fun getAllDownloaded(): Flow<List<SongEntity>> = nope()
        override fun observeLikedCount(): Flow<Int> = nope()
        override fun observeDownloadedCount(): Flow<Int> = nope()
        override fun getAll(): Flow<List<SongEntity>> = nope()
        override fun search(query: String): Flow<List<SongEntity>> = nope()
        override fun getMostPlayed(limit: Int): Flow<List<SongEntity>> = nope()
        override fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>> = nope()
        override suspend fun insert(song: SongEntity): Long = nope()
        override suspend fun insertAll(songs: List<SongEntity>) = nope()
        override suspend fun update(song: SongEntity) = nope()
        override suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean) = nope()
        override suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean) = nope()
        override suspend fun updateDownloaded(idType: String, pluginId: String, sourceId: String, isDownloaded: Boolean, downloadPath: String?) = nope()
        override suspend fun updateLocalArtworkPath(idType: String, pluginId: String, sourceId: String, localArtworkPath: String?) = nope()
        override suspend fun incrementPlayCount(idType: String, pluginId: String, sourceId: String, timestamp: Long) = nope()
        override suspend fun setEmbedding(idType: String, pluginId: String, sourceId: String, embedding: ByteArray?, modelVersion: String?, computedAtMs: Long?) = nope()
        override suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray? = nope()
        override suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int): List<SongEntity> = nope()
        override suspend fun getSongsMissingEmbeddingPaged(currentModelVersion: String, limit: Int, offset: Int): List<SongEntity> = nope()
        override fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
        override suspend fun delete(idType: String, pluginId: String, sourceId: String) = nope()
        override suspend fun deleteAll() = nope()
        override suspend fun deleteOrphanedSongs(): Int = nope()
    }
}
