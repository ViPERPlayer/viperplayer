package com.viperplayer.data.rec

import android.content.res.AssetManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * On-device CLAP parity harness (ported from the P0 spike) — split into two independent checks:
 *
 *  - [melParity_matchesGoldenMel] needs NO model: it computes the log-mel in pure Kotlin
 *    ([MelSpectrogram]) and asserts cosine >= 0.99 vs the golden mel. The golden PCM/mel `.bin`s are
 *    small and committed under `src/androidTest/assets/clap_golden/`, so this always runs. (It mirrors
 *    the JVM `MelSpectrogramParityTest`; kept here to also exercise the mel path on-device.)
 *
 *  - [embeddingParity_matchesGoldenEmbedding] needs the ~73MB int8 (or 277MB fp32) ONNX audio tower,
 *    which is deliberately NOT committed to git (see RECSYS_TESTING.md). When the ONNX asset is absent
 *    this test SKIPS gracefully (assumeTrue + logged skip) instead of failing.
 *
 * IMPORTANT — LATENCY IS NOT VALIDATED HERE ON EMULATORS. On an x86_64 emulator the ONNX time is not
 * representative of a real arm64 Pixel; this harness validates PARITY (mel + embedding correctness).
 *
 * Assets (src/androidTest/assets/clap_golden/):
 *   clap_audio_tower.onnx       (NOT committed — drop in locally per RECSYS_TESTING.md)
 *   <clip>.pcm_f32le.bin        (raw float32 LE, mono, 48kHz — committed)
 *   <clip>.mel_f32le.bin        (raw float32 LE, row-major (1001,64) — committed)
 *   <clip>.ref_embedding.bin    (raw float32 LE, 512, L2-normalized — committed)
 */
@RunWith(AndroidJUnit4::class)
class ClapParityTest {

    private lateinit var assets: AssetManager

    // name -> cropIdx. <=10s clips use 0. long_mixed_14s used the golden generator's seeded offset
    // 43567 (a shipping app pins cropIdx=0; here we match the goldens).
    private val clips = listOf(
        "sine_440_5s" to 0,
        "white_noise_10s" to 0,
        "long_mixed_14s" to 43567,
    )

    private companion object {
        const val TAG = "ClapParityTest"
        const val ASSET_DIR = "clap_golden"
        // Prefer the int8 tower (73MB) if present, else the fp32 tower (277MB). Neither is committed.
        val ONNX_CANDIDATES = listOf(
            "clap_golden/clap_audio_tower.int8.onnx",
            "clap_golden/clap_audio_tower.onnx",
        )
        const val MEL_COS_BAR = 0.99
        const val EMB_COS_BAR = 0.98
    }

    private var encoder: ClapAudioEncoder? = null

    @After
    fun tearDown() {
        encoder?.close()
    }

    @Test
    fun melParity_matchesGoldenMel() {
        assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        Log.i(TAG, "Window sum = ${MelSpectrogram.windowSum()} (contract expects 512.0)")
        var minCos = 1.0
        for ((name, cropIdx) in clips) {
            val pcm = readAssetFloatsLE("$ASSET_DIR/$name.pcm_f32le.bin")
            val goldMel = readAssetFloatsLE("$ASSET_DIR/$name.mel_f32le.bin")

            val mel = MelSpectrogram.computeLogMel(pcm, cropIdx)

            assertEquals("$name: mel length", goldMel.size, mel.size)
            val cos = cosine(mel, goldMel)
            if (cos < minCos) minCos = cos
            Log.i(TAG, "MEL  %-16s cos=%.8f".format(name, cos))
            assertTrue("$name: mel cos $cos < $MEL_COS_BAR", cos >= MEL_COS_BAR)
        }
        Log.i(TAG, "MEL  min cos across clips = %.8f (bar %.2f)".format(minCos, MEL_COS_BAR))
    }

    @Test
    fun embeddingParity_matchesGoldenEmbedding() {
        assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val modelBytes = tryReadOnnx()
        assumeTrue(
            "CLAP ONNX audio tower not present in androidTest assets — skipping embedding parity. " +
                "Drop clap_audio_tower(.int8).onnx into src/androidTest/assets/clap_golden/ to run " +
                "(see RECSYS_TESTING.md).",
            modelBytes != null
        )
        val bytes = modelBytes!!
        Log.i(TAG, "Loaded ONNX audio tower: ${bytes.size / 1_000_000} MB")
        val enc = ClapAudioEncoder.fromBytes(bytes, intraOpThreads = 4).also { encoder = it }

        var minCos = 1.0
        for ((name, cropIdx) in clips) {
            val pcm = readAssetFloatsLE("$ASSET_DIR/$name.pcm_f32le.bin")
            val refEmb = readAssetFloatsLE("$ASSET_DIR/$name.ref_embedding.bin")
            assertEquals("$name: ref embed dim", ClapAudioEncoder.EMBED_DIM, refEmb.size)

            val emb = enc.encodePcmNormalized(pcm, cropIdx)
            val cos = cosine(emb, refEmb)
            if (cos < minCos) minCos = cos
            Log.i(TAG, "EMB  %-16s cos=%.8f".format(name, cos))
            assertTrue("$name: embedding cos $cos < $EMB_COS_BAR", cos >= EMB_COS_BAR)
        }
        Log.i(TAG, "EMB  min cos across clips = %.8f (bar %.2f)".format(minCos, EMB_COS_BAR))
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    /** Read the first present ONNX candidate, or null if none is bundled in the test APK. */
    private fun tryReadOnnx(): ByteArray? {
        for (path in ONNX_CANDIDATES) {
            try {
                return readAssetBytes(path)
            } catch (_: FileNotFoundException) {
                // try next candidate
            }
        }
        return null
    }

    private fun readAssetBytes(path: String): ByteArray {
        assets.open(path).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun readAssetFloatsLE(path: String): FloatArray {
        val bytes = readAssetBytes(path)
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val out = FloatArray(fb.remaining())
        fb.get(out)
        return out
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size)
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            na += a[i].toDouble() * a[i]
            nb += b[i].toDouble() * b[i]
        }
        return dot / (sqrt(na) * sqrt(nb) + 1e-30)
    }
}
