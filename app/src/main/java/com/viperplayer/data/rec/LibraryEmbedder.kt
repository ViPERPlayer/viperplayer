package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Abstracts "48kHz mono PCM -> stored L2-normalized 512-d embedding" so the [LibraryEmbedder]
 * orchestration can be unit-tested with a fake, without loading an ONNX session / ORT native lib.
 *
 * The production implementation ([ClapEmbedder]) wraps a single [ClapAudioEncoder] (one ORT session,
 * reused across the whole indexing batch — creating a session per song is wasteful).
 */
interface AudioEmbedder {
    /**
     * @param pcm48kMono mono PCM already resampled to 48kHz (repeatpad/crop to 480000 happens inside
     *   the mel front-end). @return the L2-normalized 512-d embedding.
     */
    fun embed(pcm48kMono: FloatArray): FloatArray

    /**
     * Embed from a precomputed log-mel front-end buffer (row-major (1,1,1001,64)). Used by the streaming
     * capture path, which computes the mel on the capture thread and may DEFER embedding it (Path C), so
     * the model tower is the only step that runs at embed time. @return the L2-normalized 512-d embedding.
     */
    fun embedMel(logMel: FloatArray): FloatArray
}

/** Production [AudioEmbedder]: one reused [ClapAudioEncoder] session over a whole indexing run. */
class ClapEmbedder(private val encoder: ClapAudioEncoder) : AudioEmbedder, AutoCloseable {
    override fun embed(pcm48kMono: FloatArray): FloatArray = encoder.encodePcmNormalized(pcm48kMono)
    override fun embedMel(logMel: FloatArray): FloatArray = l2Normalize(encoder.encodeMel(logMel))
    override fun close() = encoder.close()

    companion object {
        /** Loads the ONNX at [modelFile] into one ORT session for the whole batch. */
        fun fromModelFile(modelFile: File): ClapEmbedder =
            ClapEmbedder(ClapAudioEncoder.fromFile(modelFile.absolutePath))
    }
}

/**
 * The per-song embed pipeline: local file -> [AudioDecoder] -> downmix-to-mono -> resample-to-48k ->
 * [MelSpectrogram] -> [AudioEmbedder] -> 512-d -> `SongDao.setEmbedding(...)` stamped with
 * [ClapModel.MODEL_VERSION].
 *
 * Songs with no accessible local audio are SKIPPED (they'll get server text embeddings in a later
 * wave). All dependencies are injected so the orchestration is testable with a fake decoder + fake
 * embedder — no device, codec or model required.
 */
class LibraryEmbedder @Inject constructor(
    private val sourceResolver: SongAudioSource,
    private val songDao: SongDao,
) {

    /** The outcome of attempting to embed one song. */
    enum class Outcome {
        /** Embedded and stored. */
        EMBEDDED,

        /** Skipped — no local bytes / could not open the source. */
        SKIPPED_NO_LOCAL_BYTES,

        /** Skipped — the file opened but could not be decoded (corrupt / unsupported codec). */
        SKIPPED_DECODE_FAILED,

        /** An error occurred while embedding/storing (logged; the run continues with the next song). */
        FAILED,
    }

    /**
     * Embeds [song] with the shared [decoder] + [embedder] (both created once per run) and stores the
     * result. Returns an [Outcome]; never throws for a per-song failure (the batch keeps going).
     * @param nowMs wall-clock timestamp to stamp the embedding with (injectable for tests).
     */
    suspend fun embedSong(
        song: SongEntity,
        decoder: AudioDecoder,
        embedder: AudioEmbedder,
        nowMs: Long = System.currentTimeMillis(),
    ): Outcome {
        if (!SongEmbedCandidates.hasLocalBytes(song)) return Outcome.SKIPPED_NO_LOCAL_BYTES
        return try {
            val decoded = sourceResolver.decode(song, decoder)
                ?: return Outcome.SKIPPED_DECODE_FAILED
            val embedding = embedFromDecoded(decoded, embedder)
            storeEmbedding(song, embedding, nowMs)
            Outcome.EMBEDDED
        } catch (e: Exception) {
            Timber.w(e, "LibraryEmbedder: failed to embed songId=${song.id}")
            Outcome.FAILED
        }
    }

    /** Downmix -> resample-to-48k -> mel -> embed. The 480000-sample crop happens inside the mel path. */
    private fun embedFromDecoded(decoded: DecodedAudio, embedder: AudioEmbedder): FloatArray {
        val mono = MelSpectrogram.downmixToMono(decoded.interleaved, decoded.channels)
        val mono48k = MelSpectrogram.resampleTo48k(mono, decoded.sampleRate)
        return embedder.embed(mono48k)
    }

    /** Persists the embedding BLOB, stamped with the current model version + [nowMs]. */
    private suspend fun storeEmbedding(song: SongEntity, embedding: FloatArray, nowMs: Long) {
        songDao.setEmbedding(
            idType = song.idType,
            pluginId = song.pluginId,
            sourceId = song.sourceId,
            embedding = embeddingToBytes(embedding),
            modelVersion = ClapModel.MODEL_VERSION,
            computedAtMs = nowMs,
        )
    }
}
