package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for [StreamingCaptureEmbedder] (Path A back half) with FAKE collaborators — proves the two
 * branches: model READY -> embed + store; model NOT ready -> cache the mel to [PendingCaptureStore]. Also
 * covers the guards (recommendations off, non-streaming song, already-embedded, non-plugin id). No device,
 * codec or ONNX model — the embedder factory is faked via [StreamingCaptureEmbedder.embedderFactory].
 */
class StreamingCaptureEmbedderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // A constructed MediaId (its constructor is Uri-free; only encode() touches android.net.Uri) plus a
    // plain string cache key — so the tests never call MediaId.decode (which needs Uri, absent on the JVM).
    private val pluginId = MediaId.Plugin("testsource", "song1")
    private val encodedKey = "t=plugin&p=testsource&s=song1"

    private fun streamingSong(embedded: Boolean = false) = SongEntity(
        id = 1, idType = "plugin", pluginId = "testsource", sourceId = "song1", title = "t",
        audioEmbedding = if (embedded) ByteArray(ClapModel.EMBEDDING_BYTES) else null,
        embeddingModelVersion = if (embedded) ClapModel.MODEL_VERSION else null,
    )

    /** SongDao fake: serves one row by mediaId and records setEmbedding writes. */
    private class FakeDao(private val row: SongEntity?) : SongDao {
        val stored = mutableListOf<ByteArray?>()
        override suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity? =
            row?.takeIf { it.idType == idType && it.pluginId == pluginId && it.sourceId == sourceId }

        override suspend fun setEmbedding(
            idType: String, pluginId: String, sourceId: String,
            embedding: ByteArray?, modelVersion: String?, computedAtMs: Long?,
        ) { stored.add(embedding) }

        private fun nope(): Nothing = throw NotImplementedError()
        override fun getById(id: Long): Flow<SongEntity?> = nope()
        override suspend fun getByIdSync(id: Long): SongEntity? = nope()
        override suspend fun getByIds(ids: List<Long>): List<SongEntity> = nope()
        override fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?> = nope()
        override fun getByAlbum(albumId: Long): Flow<List<SongEntity>> = nope()
        override fun getAllLiked(): Flow<List<SongEntity>> = nope()
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
        override suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray? = nope()
        override suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int): List<SongEntity> = nope()
        override suspend fun getSongsMissingEmbeddingPaged(currentModelVersion: String, limit: Int, offset: Int): List<SongEntity> = nope()
        override suspend fun getStreamingSongsMissingEmbeddingPaged(currentModelVersion: String, limit: Int, offset: Int): List<SongEntity> = nope()
        override fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
        override fun countStreamingSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
        override suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow> = nope()
        override suspend fun delete(idType: String, pluginId: String, sourceId: String) = nope()
        override suspend fun deleteAll() = nope()
        override suspend fun deleteOrphanedSongs(): Int = nope()
    }

    private class FakeSettings(private val enabled: Boolean) :
        SettingsRepository by UnsupportedSettingsRepository() {
        override val recommendationsEnabled: Flow<Boolean> get() = flowOf(enabled)
    }

    private class FakeClapModel(
        private val file: File?,
        private val stale: Boolean = false,
    ) : ClapModelRepository {
        override val modelState: Flow<ClapModelState> get() = flowOf(ClapModelState.Absent)
        override suspend fun installedVersion(): String? = null
        override suspend fun installedFile(): File? = file
        override suspend fun isInstalledStale(): Boolean = stale
        override fun enqueueDownload() = Unit
        override suspend fun ensureDownloaded() = Unit
        override fun cancel() = Unit
        override suspend fun deleteModel() = Unit
    }

    private fun embedder(
        dao: SongDao,
        enabled: Boolean = true,
        modelFile: File? = null,
        stale: Boolean = false,
        store: PendingCaptureStore,
    ): StreamingCaptureEmbedder = StreamingCaptureEmbedder(
        songDao = dao,
        settingsRepository = FakeSettings(enabled),
        clapModelRepository = FakeClapModel(modelFile, stale),
        pendingCaptureStore = store,
    ).apply {
        // Fake embedder: returns a fixed 512-d vector from a mel, no ONNX.
        embedderFactory = StreamingCaptureEmbedder.MelEmbedderFactory {
            object : AudioEmbedder {
                override fun embed(pcm48kMono: FloatArray) = FloatArray(ClapModel.EMBEDDING_DIM) { 0.02f }
                override fun embedMel(logMel: FloatArray) = FloatArray(ClapModel.EMBEDDING_DIM) { 0.02f }
            }
        }
    }

    private fun pcm() = FloatArray(48_000) { 0.1f } // 1s mono @ 48k

    @Test
    fun modelReadyEmbedsAndStores() = runTest {
        val dao = FakeDao(streamingSong())
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        val modelFile = tmp.newFile("model.onnx").apply { writeText("x") }
        embedder(dao, modelFile = modelFile, store = store).process(pluginId, encodedKey, pcm(), 48_000)

        assertEquals(1, dao.stored.size)
        assertEquals(ClapModel.EMBEDDING_BYTES, dao.stored.single()!!.size)
        assertTrue("nothing should be cached when embedded", store.readAll().isEmpty())
    }

    @Test
    fun modelNotReadyCachesTheMel() = runTest {
        val dao = FakeDao(streamingSong())
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        // No model file -> not ready -> cache.
        embedder(dao, modelFile = null, store = store).process(pluginId, encodedKey, pcm(), 48_000)

        assertTrue("no embedding stored", dao.stored.isEmpty())
        val cached = store.readAll().single()
        assertEquals(encodedKey, cached.mediaId)
        assertEquals(MelSpectrogram.NUM_FRAMES * MelSpectrogram.N_MELS, cached.mel.size)
    }

    @Test
    fun staleModelCachesTheMel() = runTest {
        val dao = FakeDao(streamingSong())
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        val modelFile = tmp.newFile("model.onnx").apply { writeText("x") }
        embedder(dao, modelFile = modelFile, stale = true, store = store).process(pluginId, encodedKey, pcm(), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertEquals(1, store.readAll().size)
    }

    @Test
    fun recommendationsDisabledDoesNothing() = runTest {
        val dao = FakeDao(streamingSong())
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        embedder(dao, enabled = false, modelFile = tmp.newFile("m"), store = store)
            .process(pluginId, encodedKey, pcm(), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun alreadyEmbeddedSongIsSkipped() = runTest {
        val dao = FakeDao(streamingSong(embedded = true))
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        embedder(dao, modelFile = tmp.newFile("m"), store = store).process(pluginId, encodedKey, pcm(), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun downloadedSongIsNotAStreamingCandidateSoSkipped() = runTest {
        val downloaded = streamingSong().copy(isDownloaded = true, downloadPath = "/x")
        val dao = FakeDao(downloaded)
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        embedder(dao, modelFile = tmp.newFile("m"), store = store).process(pluginId, encodedKey, pcm(), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun songNotInLibraryIsIgnored() = runTest {
        // The row for this mediaId doesn't exist (e.g. deleted after capture) -> no work.
        val dao = FakeDao(row = null)
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        embedder(dao, modelFile = tmp.newFile("m"), store = store).process(pluginId, encodedKey, pcm(), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun emptyPcmIsIgnored() = runTest {
        val dao = FakeDao(streamingSong())
        val store = PendingCaptureStore.forDirectory(tmp.newFolder("c"))
        embedder(dao, modelFile = tmp.newFile("m"), store = store)
            .process(pluginId, encodedKey, FloatArray(0), 48_000)
        assertTrue(dao.stored.isEmpty())
        assertNull(store.readAll().firstOrNull())
    }
}
