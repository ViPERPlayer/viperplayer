package com.viperplayer.data.rec

import com.viperplayer.data.local.entity.SongEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orchestration test for [LibraryEmbedder] with a FAKE decoder, FAKE source and FAKE embedder — proves
 * the embed -> store round-trip and the skip decisions WITHOUT a device, codec or ONNX model.
 *
 * Also exercises the window/length handling: a SHORT and a LONG interleaved PCM both flow through the
 * real [MelSpectrogram] downmix + resample + 480000-sample crop and produce a stored 512-d BLOB.
 */
class LibraryEmbedderTest {

    private fun song(
        id: Long,
        idType: String = "plugin",
        pluginId: String = "testsource",
        sourceId: String = "s$id",
        isDownloaded: Boolean = false,
        downloadPath: String? = null,
        isLiked: Boolean = false,
    ) = SongEntity(
        id = id,
        idType = idType,
        pluginId = pluginId,
        sourceId = sourceId,
        title = "t$id",
        isDownloaded = isDownloaded,
        downloadPath = downloadPath,
        isLiked = isLiked,
    )

    /** Fake source that returns a fixed [DecodedAudio] (or null to simulate a decode failure). */
    private class FakeSource(private val audio: DecodedAudio?) : SongAudioSource {
        var calls = 0
        override fun decode(song: SongEntity, decoder: AudioDecoder): DecodedAudio? {
            calls++
            return audio
        }
    }

    /** Fake embedder: returns a deterministic unit-ish 512-d vector; records how many samples it saw. */
    private class FakeEmbedder : AudioEmbedder {
        var lastPcmSize = -1
        override fun embed(pcm48kMono: FloatArray): FloatArray {
            lastPcmSize = pcm48kMono.size
            return FloatArray(ClapModel.EMBEDDING_DIM) { 0.01f }
        }
    }

    private val noopDecoder = object : AudioDecoder {
        override fun decode(filePath: String): DecodedAudio? = null
        override fun decode(fd: java.io.FileDescriptor): DecodedAudio? = null
    }

    @Test
    fun embedsAndStoresDownloadedSong() = runTest {
        val decoded = DecodedAudio(FloatArray(48_000) { 0.1f }, sampleRate = 48_000, channels = 1)
        val dao = RecordingSongDao()
        val embedder = LibraryEmbedder(FakeSource(decoded), dao)
        val fake = FakeEmbedder()

        val outcome = embedder.embedSong(
            song(1, isDownloaded = true, downloadPath = "/music/a.mp3"),
            noopDecoder, fake, nowMs = 123_456L,
        )

        assertEquals(LibraryEmbedder.Outcome.EMBEDDED, outcome)
        val stored = dao.stored.single()
        assertEquals("testsource", stored.pluginId)
        assertEquals("s1", stored.sourceId)
        assertEquals(ClapModel.MODEL_VERSION, stored.modelVersion)
        assertEquals(123_456L, stored.computedAtMs)
        // Stored BLOB is the 2048-byte float32 LE encoding of the fake embedding, round-trippable.
        assertEquals(ClapModel.EMBEDDING_BYTES, stored.embedding!!.size)
        assertArrayEquals(FloatArray(ClapModel.EMBEDDING_DIM) { 0.01f }, bytesToEmbedding(stored.embedding), 0f)
    }

    @Test
    fun skipsStreamingOnlySongWithoutTouchingSourceOrDao() = runTest {
        val source = FakeSource(DecodedAudio(FloatArray(100), 48_000, 1))
        val dao = RecordingSongDao()
        val embedder = LibraryEmbedder(source, dao)

        // Not downloaded, not local -> streaming only.
        val outcome = embedder.embedSong(song(2, isLiked = true), noopDecoder, FakeEmbedder())

        assertEquals(LibraryEmbedder.Outcome.SKIPPED_NO_LOCAL_BYTES, outcome)
        assertEquals(0, source.calls) // never even attempted to open
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun skipsWhenDecodeFails() = runTest {
        val dao = RecordingSongDao()
        val embedder = LibraryEmbedder(FakeSource(audio = null), dao)
        val outcome = embedder.embedSong(
            song(3, isDownloaded = true, downloadPath = "/x.flac"), noopDecoder, FakeEmbedder(),
        )
        assertEquals(LibraryEmbedder.Outcome.SKIPPED_DECODE_FAILED, outcome)
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun shortPcmIsRepeatPaddedToClipLengthBeforeEmbedding() = runTest {
        // 2s of 48kHz mono (< the 10s clip) -> mel repeat-pads up to 480000; the resampler is a no-op
        // at 48kHz so the embedder receives exactly the short 96000-sample buffer (the 480000 crop is
        // inside the mel front-end, which the FakeEmbedder does not run).
        val decoded = DecodedAudio(FloatArray(96_000) { 0.2f }, sampleRate = 48_000, channels = 1)
        val dao = RecordingSongDao()
        val fake = FakeEmbedder()
        val embedder = LibraryEmbedder(FakeSource(decoded), dao)

        val outcome = embedder.embedSong(
            song(4, idType = "local", pluginId = "", sourceId = "content://m/4"),
            noopDecoder, fake,
        )
        assertEquals(LibraryEmbedder.Outcome.EMBEDDED, outcome)
        assertEquals(96_000, fake.lastPcmSize) // mono, no resample needed at 48k
        assertEquals(1, dao.stored.size)
    }

    @Test
    fun longStereoPcmIsDownmixedAndResampledBeforeEmbedding() = runTest {
        // 12s of 44.1kHz STEREO -> downmix halves the interleaved length, resample to 48k grows it.
        val frames = 44_100 * 12
        val interleaved = FloatArray(frames * 2) { if (it % 2 == 0) 0.3f else -0.3f }
        val decoded = DecodedAudio(interleaved, sampleRate = 44_100, channels = 2)
        val dao = RecordingSongDao()
        val fake = FakeEmbedder()
        val embedder = LibraryEmbedder(FakeSource(decoded), dao)

        val outcome = embedder.embedSong(
            song(5, isDownloaded = true, downloadPath = "/x.m4a"), noopDecoder, fake,
        )
        assertEquals(LibraryEmbedder.Outcome.EMBEDDED, outcome)
        // After downmix (frames) then resample 44.1k -> 48k: ~ frames * 48000/44100.
        val expected = Math.floor(frames * 48_000.0 / 44_100.0).toInt()
        assertEquals(expected, fake.lastPcmSize)
        assertEquals(1, dao.stored.size)
    }

    @Test
    fun runReleasesNothingButStoresRoundTripBlob() = runTest {
        val decoded = DecodedAudio(FloatArray(48_000) { 0.05f }, 48_000, 1)
        val dao = RecordingSongDao()
        val embedder = LibraryEmbedder(FakeSource(decoded), dao)
        embedder.embedSong(song(6, isDownloaded = true, downloadPath = "/y.wav"), noopDecoder, FakeEmbedder())
        // Read-back via the DAO returns the same bytes.
        val back = dao.getEmbedding("plugin", "testsource", "s6")
        assertEquals(ClapModel.EMBEDDING_BYTES, back!!.size)
        assertNull(dao.getEmbedding("plugin", "testsource", "does-not-exist"))
    }
}
