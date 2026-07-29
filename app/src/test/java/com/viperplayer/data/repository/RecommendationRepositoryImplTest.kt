package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.data.rec.ClapModelState
import com.viperplayer.data.rec.IndexingStatus
import com.viperplayer.data.rec.IndexingStatusProvider
import com.viperplayer.data.rec.embeddingToBytes
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TasteState
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedPluginRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Unit tests for [RecommendationRepositoryImpl] over hand-written fakes: the local kNN path returns songs
 * in cosine order, a seed with no embedding falls back to the plugin's related songs, an empty state is
 * surfaced when nothing is available, and "For You" builds a centroid over the liked/recent pool.
 */
class RecommendationRepositoryImplTest {

    // 2-d unit vectors are enough to exercise the ranking (the real dim is 512 but the code is generic).
    private fun bytes(x: Float, y: Float): ByteArray {
        // Pad the 2-d direction into a full 512-d unit vector so it round-trips through the codec.
        val v = FloatArray(ClapModel.EMBEDDING_DIM)
        v[0] = x
        v[1] = y
        val n = sqrt((x * x + y * y).toDouble()).toFloat().takeIf { it > 0f } ?: 1f
        v[0] = x / n
        v[1] = y / n
        return embeddingToBytes(v)
    }

    /** As [bytes] but returns the 512-d unit FloatArray directly (for a warm taste vector). */
    private fun vector(x: Float, y: Float): FloatArray {
        val v = FloatArray(ClapModel.EMBEDDING_DIM)
        val n = sqrt((x * x + y * y).toDouble()).toFloat().takeIf { it > 0f } ?: 1f
        v[0] = x / n
        v[1] = y / n
        return v
    }

    private fun local(id: String) = MediaId.Local(id)

    private fun entity(rowId: Long, sourceId: String, albumId: Long? = null) = SongEntity(
        id = rowId,
        idType = "local",
        pluginId = "",
        sourceId = sourceId,
        title = sourceId,
        albumId = albumId,
    )

    private fun song(sourceId: String) = Song(id = local(sourceId), title = sourceId)

    @Test
    fun moreLikeThis_localPath_returnsKnnOrderExcludingSeed() = runTest {
        // Seed points along x. Candidates: aligned (best), 45deg, orthogonal. Seed itself is excluded.
        val dao = RecFakeSongDao(
            embeddings = listOf(
                SongEmbeddingRow(1L, bytes(1f, 0f)), // seed
                SongEmbeddingRow(2L, bytes(1f, 0f)), // most similar
                SongEmbeddingRow(3L, bytes(1f, 1f)), // 45deg
                SongEmbeddingRow(4L, bytes(0f, 1f)), // orthogonal
            ),
            entities = listOf(
                entity(1L, "seed"),
                entity(2L, "s2"),
                entity(3L, "s3"),
                entity(4L, "s4"),
            ),
            seedEmbeddings = mapOf(local("seed") to bytes(1f, 0f)),
        )
        val repo = repo(
            dao = dao,
            media = FakeMedia(mapOf(local("s2") to song("s2"), local("s3") to song("s3"), local("s4") to song("s4"))),
        )

        val result = repo.moreLikeThis(local("seed"), limit = 10)

        assertTrue(result is RecResult.Local)
        val ids = (result as RecResult.Local).songs.map { it.id }
        assertEquals(listOf(local("s2"), local("s3"), local("s4")), ids)
    }

    @Test
    fun moreLikeThis_excludesSameAlbumDuplicates() = runTest {
        // s2 shares the seed's album -> excluded even though it's the closest vector.
        val dao = RecFakeSongDao(
            embeddings = listOf(
                SongEmbeddingRow(1L, bytes(1f, 0f)),
                SongEmbeddingRow(2L, bytes(1f, 0f)), // same album as seed
                SongEmbeddingRow(3L, bytes(1f, 1f)),
            ),
            entities = listOf(
                entity(1L, "seed", albumId = 99L),
                entity(2L, "s2", albumId = 99L),
                entity(3L, "s3", albumId = 5L),
            ),
            seedEmbeddings = mapOf(local("seed") to bytes(1f, 0f)),
        )
        // Only 1 local result after exclusion (< MIN_LOCAL_RESULTS=3) -> fallback; give the plugin songs.
        val repo = repo(
            dao = dao,
            media = FakeMedia(mapOf(local("s3") to song("s3"))),
            plugin = FakePlugin(mapOf(local("seed") to listOf(song("r1"), song("r2")))),
        )

        val result = repo.moreLikeThis(local("seed"), limit = 10)

        // Fell back because only s3 survived exclusion (below the local threshold).
        assertTrue(result is RecResult.Fallback)
        assertEquals(listOf(local("r1"), local("r2")), (result as RecResult.Fallback).songs.map { it.id })
    }

    @Test
    fun moreLikeThis_noSeedEmbedding_fallsBackToPluginRelated() = runTest {
        val dao = RecFakeSongDao(
            embeddings = emptyList(),
            entities = emptyList(),
            seedEmbeddings = emptyMap(), // seed is streaming-only: no embedding
        )
        val repo = repo(
            dao = dao,
            plugin = FakePlugin(mapOf(local("seed") to listOf(song("r1"), song("r2")))),
        )

        val result = repo.moreLikeThis(local("seed"), limit = 10)

        assertTrue(result is RecResult.Fallback)
        assertEquals(listOf(local("r1"), local("r2")), (result as RecResult.Fallback).songs.map { it.id })
    }

    @Test
    fun moreLikeThis_noEmbeddingAndNoRelated_returnsEmpty() = runTest {
        val dao = RecFakeSongDao(emptyList(), emptyList(), emptyMap())
        val repo = repo(dao = dao, plugin = FakePlugin(emptyMap()))

        val result = repo.moreLikeThis(local("seed"), limit = 10)

        assertTrue(result is RecResult.Empty)
        assertEquals(RecEmptyReason.NO_RESULTS, (result as RecResult.Empty).reason)
    }

    @Test
    fun forYou_buildsCentroidOverLikedAndRecent_excludingThePool() = runTest {
        // Liked pool = {s1}, all pointing +x. Non-pool s3 (+x) should rank above s4 (orthogonal).
        val dao = RecFakeSongDao(
            embeddings = listOf(
                SongEmbeddingRow(1L, bytes(1f, 0f)), // in the liked pool
                SongEmbeddingRow(3L, bytes(1f, 0.1f)), // closest non-pool
                SongEmbeddingRow(4L, bytes(0f, 1f)), // orthogonal
            ),
            entities = listOf(entity(1L, "s1"), entity(3L, "s3"), entity(4L, "s4")),
            seedEmbeddings = emptyMap(),
            liked = listOf(entity(1L, "s1")),
            recent = emptyList(),
        )
        val repo = repo(
            dao = dao,
            media = FakeMedia(mapOf(local("s3") to song("s3"), local("s4") to song("s4"))),
        )

        val result = repo.forYouFromLibrary(limit = 10)

        assertTrue(result is RecResult.Local)
        val ids = (result as RecResult.Local).songs.map { it.id }
        assertEquals("pool member s1 excluded; s3 ranks above s4", listOf(local("s3"), local("s4")), ids)
    }

    @Test
    fun forYou_warmTaste_personalizesOrderVsPlainCentroid() = runTest {
        // A warm taste pointing along +y. Non-pool candidates: s3 (+y, on-taste) should outrank s4 (+x,
        // off-taste). The plain centroid path (cold taste, below) builds from the +x liked pool and would
        // prefer s4 — so the personalized order genuinely differs.
        val dao = RecFakeSongDao(
            embeddings = listOf(
                SongEmbeddingRow(1L, bytes(1f, 0f)), // liked pool (points +x)
                SongEmbeddingRow(3L, bytes(0f, 1f)), // on the +y taste
                SongEmbeddingRow(4L, bytes(1f, 0f)), // off the +y taste
            ),
            entities = listOf(entity(1L, "s1"), entity(3L, "s3"), entity(4L, "s4")),
            seedEmbeddings = emptyMap(),
            liked = listOf(entity(1L, "s1")),
            recent = emptyList(),
        )
        val media = FakeMedia(mapOf(local("s3") to song("s3"), local("s4") to song("s4")))
        val warm = FakeTaste(TasteState(vector = vector(0f, 1f)))
        val cold = FakeTaste(TasteState.COLD)

        val personalized = (repo(dao = dao, media = media, taste = warm).forYouFromLibrary(limit = 10)
            as RecResult.Local).songs.map { it.id }
        val plain = (repo(dao = dao, media = media, taste = cold).forYouFromLibrary(limit = 10)
            as RecResult.Local).songs.map { it.id }

        // Personalized taste (+y) ranks s3 first; the plain +x centroid ranks s4 first.
        assertEquals(local("s3"), personalized.first())
        assertEquals(local("s4"), plain.first())
        assertTrue("personalized order differs from plain kNN", personalized != plain)
    }

    @Test
    fun forYou_recordsServedIdsForAntiRepetition() = runTest {
        val dao = RecFakeSongDao(
            embeddings = listOf(SongEmbeddingRow(1L, bytes(1f, 0f)), SongEmbeddingRow(3L, bytes(1f, 0.1f))),
            entities = listOf(entity(1L, "s1"), entity(3L, "s3")),
            seedEmbeddings = emptyMap(),
            liked = listOf(entity(1L, "s1")),
        )
        val taste = FakeTaste(TasteState(vector = vector(1f, 0f)))
        repo(dao = dao, media = FakeMedia(mapOf(local("s3") to song("s3"))), taste = taste)
            .forYouFromLibrary(limit = 10)
        assertTrue("served ids are recorded for anti-repetition", taste.served.isNotEmpty())
    }

    @Test
    fun forYou_disabled_returnsDisabledEmpty() = runTest {
        val dao = RecFakeSongDao(emptyList(), emptyList(), emptyMap())
        val repo = repo(dao = dao, recommendationsEnabled = false)

        val result = repo.forYouFromLibrary(limit = 10)

        assertEquals(RecEmptyReason.RECOMMENDATIONS_DISABLED, (result as RecResult.Empty).reason)
    }

    @Test
    fun forYou_noEmbeddingsWhileModelNotReady_returnsNotIndexed() = runTest {
        val dao = RecFakeSongDao(emptyList(), emptyList(), emptyMap())
        val repo = repo(dao = dao, modelState = ClapModelState.Absent)

        val result = repo.forYouFromLibrary(limit = 10)

        assertEquals(RecEmptyReason.NOT_INDEXED_YET, (result as RecResult.Empty).reason)
    }

    // --- wiring ------------------------------------------------------------------------------------

    private fun repo(
        dao: SongDao,
        media: MediaLibraryRepository = FakeMedia(emptyMap()),
        plugin: PluginRepository = FakePlugin(emptyMap()),
        recommendationsEnabled: Boolean = true,
        modelState: ClapModelState = ClapModelState.Ready("v", File("x")),
        indexingStatus: IndexingStatus = IndexingStatus.Idle,
        taste: TasteRepository = FakeTaste(),
    ) = RecommendationRepositoryImpl(
        songDao = dao,
        mediaLibraryRepository = media,
        pluginRepository = plugin,
        settingsRepository = FakeSettings(recommendationsEnabled),
        clapModelRepository = FakeClapModel(modelState),
        indexRepository = FakeIndex(indexingStatus),
        tasteRepository = taste,
    )

    private class FakeMedia(private val songs: Map<MediaId, Song>) :
        MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        override fun getSong(mediaId: MediaId): Flow<Song?> = flowOf(songs[mediaId])
    }

    private class FakePlugin(private val related: Map<MediaId, List<Song>>) :
        PluginRepository by UnsupportedPluginRepository() {
        override suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> =
            Result.success(PagedResult(related[songId].orEmpty()))
    }

    private class FakeSettings(private val enabled: Boolean) :
        SettingsRepository by UnsupportedSettingsRepository() {
        override val recommendationsEnabled: Flow<Boolean> get() = flowOf(enabled)
    }

    private class FakeClapModel(private val state: ClapModelState) : ClapModelRepository {
        override val modelState: Flow<ClapModelState> get() = flowOf(state)
        override suspend fun installedVersion(): String? = null
        override suspend fun installedFile(): File? = null
        override suspend fun isInstalledStale(): Boolean = false
        override fun enqueueDownload() = Unit
        override suspend fun ensureDownloaded() = Unit
        override fun cancel() = Unit
        override suspend fun deleteModel() = Unit
    }

    private class FakeIndex(private val status: IndexingStatus) : IndexingStatusProvider {
        override val indexingStatus: Flow<IndexingStatus> get() = flowOf(status)
    }

    /**
     * Test [TasteRepository]. Defaults to a COLD taste (so "For You" exercises the centroid fallback),
     * and records the ids passed to [recordServed] so a test can assert the feed was recorded.
     */
    class FakeTaste(private val state: TasteState = TasteState.COLD) : TasteRepository {
        val served = mutableListOf<Long>()
        override suspend fun taste(): TasteState = state
        override suspend fun recordServed(servedIds: List<Long>) { served += servedIds }
        override suspend fun servedRecency(): Map<Long, Float> = emptyMap()
        override suspend fun servedCounts(): Map<Long, Int> = emptyMap()
        override suspend fun onInteraction(interaction: Interaction) = Unit
        override suspend fun invalidate() = Unit
    }
}
