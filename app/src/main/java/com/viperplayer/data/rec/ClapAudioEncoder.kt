package com.viperplayer.data.rec

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Model constants for the on-device CLAP audio recommender (`laion/larger_clap_music` audio tower).
 *
 * [MODEL_VERSION] is the handshake key: an embedding row is only comparable to (and only kNN-usable
 * against) other rows produced by the SAME model version — the indexer/backend later gate on it, and
 * a stale [SongEntity.embeddingModelVersion] marks a row for recompute. Bump this string on ANY change
 * that alters the embedding vector space (new checkpoint, changed mel contract, changed resampler,
 * changed quantization). The server ingest must stamp rows with the identical string.
 */
object ClapModel {
    /**
     * Embedding-space version handshake. Format: `<checkpoint>-<frontend-rev>`. `mel1` = the mel
     * front-end + canonical windowed-sinc 48kHz resampler shipped in [MelSpectrogram] (P1a).
     */
    const val MODEL_VERSION = "laion-larger_clap_music-mel1"

    /** Audio embedding dimensionality. */
    const val EMBEDDING_DIM = 512

    /** Canonical target sample rate for the mel front-end (Hz). */
    const val TARGET_SAMPLE_RATE = 48_000

    /** Fixed clip length fed to the tower, in samples at [TARGET_SAMPLE_RATE] (10s * 48kHz). */
    const val CLIP_SAMPLES = 480_000

    /** Bytes of one stored embedding: EMBEDDING_DIM float32 little-endian. */
    const val EMBEDDING_BYTES = EMBEDDING_DIM * 4 // 2048
}

/**
 * Wraps an ONNX Runtime session for the CLAP audio tower (`clap_audio_tower.onnx`, opset 17).
 *
 * Pipeline: 48kHz mono PCM -> [MelSpectrogram] log-mel (1,1,1001,64) -> ONNX -> 512-d audio embedding.
 * The raw ONNX output is NOT L2-normalized (its natural norm is ~0.43-0.46); the normalized entry
 * points L2-normalize it so the result is directly comparable to reference/server vectors (which are
 * unit-norm) via cosine / dot product, and so a stored embedding is a unit vector.
 *
 * Model I/O (verified against clap_audio_tower.onnx):
 *   input  "input_features" : float32 [batch, 1, 1001, 64]
 *   output "audio_embeds"   : float32 [batch, 512]
 *
 * Threading: not thread-safe. Create one per thread or guard externally. Call [close] when done.
 *
 * Deliberately Android-`Context`-free: it loads the ONNX from a caller-provided file path or byte[]
 * (e.g. the app's downloaded-model dir, or androidTest assets) and returns an L2-normalized 512-d
 * FloatArray. This keeps it unit-testable off-device and reusable unchanged in the app.
 */
class ClapAudioEncoder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession
) : Closeable {

    companion object {
        const val INPUT_NAME = "input_features"
        const val OUTPUT_NAME = "audio_embeds"
        const val EMBED_DIM = ClapModel.EMBEDDING_DIM
        private val INPUT_SHAPE =
            longArrayOf(1, 1, MelSpectrogram.NUM_FRAMES.toLong(), MelSpectrogram.N_MELS.toLong())

        /**
         * Build an encoder from the raw ONNX model bytes.
         * @param modelBytes     the audio-tower ONNX (fp32 `clap_audio_tower.onnx` or int8 variant).
         * @param intraOpThreads intra-op thread count; 0 lets ORT pick. On a Pixel, matching big-core
         *                       count (e.g. 4) is a reasonable default.
         */
        fun fromBytes(modelBytes: ByteArray, intraOpThreads: Int = 4): ClapAudioEncoder {
            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(modelBytes, buildOptions(intraOpThreads))
            return ClapAudioEncoder(env, session)
        }

        /**
         * Build an encoder from an ONNX model file. ORT memory-maps the file, so this avoids reading
         * the (large) model fully into a Java byte[]. Preferred when the model lives on disk.
         */
        fun fromFile(modelPath: String, intraOpThreads: Int = 4): ClapAudioEncoder {
            require(File(modelPath).isFile) { "ONNX model not found at $modelPath" }
            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(modelPath, buildOptions(intraOpThreads))
            return ClapAudioEncoder(env, session)
        }

        private fun buildOptions(intraOpThreads: Int): OrtSession.SessionOptions =
            OrtSession.SessionOptions().apply {
                if (intraOpThreads > 0) setIntraOpNumThreads(intraOpThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // CPU EP is the reliable parity baseline; the CLAP HTS-AT graph is not guaranteed
                // NNAPI-friendly. An EP experiment belongs in a later device-latency wave.
            }
    }

    /**
     * Run inference on a precomputed log-mel buffer (row-major (1,1,1001,64) = 1001*64 floats).
     * Returns the RAW (un-normalized) 512-d embedding.
     */
    fun encodeMel(logMelRowMajor: FloatArray): FloatArray {
        require(logMelRowMajor.size == MelSpectrogram.NUM_FRAMES * MelSpectrogram.N_MELS) {
            "expected ${MelSpectrogram.NUM_FRAMES * MelSpectrogram.N_MELS} mel values, got ${logMelRowMajor.size}"
        }
        val buf = FloatBuffer.wrap(logMelRowMajor)
        OnnxTensor.createTensor(env, buf, INPUT_SHAPE).use { input ->
            session.run(mapOf(INPUT_NAME to input)).use { result ->
                val out = result[OUTPUT_NAME].get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                val arr = out.value as Array<FloatArray> // [1][512]
                return arr[0].copyOf()
            }
        }
    }

    /** Full pipeline: 48kHz mono PCM -> mel -> raw embedding. */
    fun encodePcm(pcm48kMono: FloatArray, cropIdx: Int = 0): FloatArray =
        encodeMel(MelSpectrogram.computeInputFeatures(pcm48kMono, cropIdx))

    /**
     * Full pipeline with L2 normalization — the canonical stored/comparable 512-d embedding.
     * @param pcm48kMono mono PCM already resampled to 48kHz (use [MelSpectrogram.resampleTo48k] /
     *                   [MelSpectrogram.downmixToMono] upstream for arbitrary device audio).
     */
    fun encodePcmNormalized(pcm48kMono: FloatArray, cropIdx: Int = 0): FloatArray =
        l2Normalize(encodePcm(pcm48kMono, cropIdx))

    override fun close() {
        session.close()
        // Do NOT close the shared OrtEnvironment singleton here.
    }
}

/** L2-normalize a vector (returns a new array). */
fun l2Normalize(v: FloatArray): FloatArray {
    var n = 0.0
    for (x in v) n += x.toDouble() * x
    val inv = 1.0 / (sqrt(n) + 1e-30)
    return FloatArray(v.size) { (v[it] * inv).toFloat() }
}

/**
 * Serialize a 512-d embedding to its stored byte layout: float32 LITTLE-ENDIAN, [ClapModel.EMBEDDING_BYTES]
 * bytes. This is the exact on-wire/at-rest layout the server ingest and local kNN read back with
 * [bytesToEmbedding].
 */
fun embeddingToBytes(embedding: FloatArray): ByteArray {
    require(embedding.size == ClapModel.EMBEDDING_DIM) {
        "expected ${ClapModel.EMBEDDING_DIM}-d embedding, got ${embedding.size}"
    }
    val bb = ByteBuffer.allocate(ClapModel.EMBEDDING_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    for (x in embedding) bb.putFloat(x)
    return bb.array()
}

/** Inverse of [embeddingToBytes]: parse a float32 LE byte payload back to a 512-d FloatArray. */
fun bytesToEmbedding(bytes: ByteArray): FloatArray {
    require(bytes.size == ClapModel.EMBEDDING_BYTES) {
        "expected ${ClapModel.EMBEDDING_BYTES} bytes, got ${bytes.size}"
    }
    val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    val out = FloatArray(ClapModel.EMBEDDING_DIM)
    fb.get(out)
    return out
}
