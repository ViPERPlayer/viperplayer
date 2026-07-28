package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure-JVM parity test locking the Kotlin CLAP mel front-end ([MelSpectrogram]) to the Python/
 * transformers reference, WITHOUT a device or the ONNX model. It reads the small golden PCM + golden
 * mel `.bin`s from test resources (`src/test/resources/clap_golden/`) and asserts the Kotlin log-mel
 * matches the golden log-mel to cosine >= 0.99 (the spike measured bit-exact 1.0 off-device).
 *
 * This is the CI gate for mel parity: it runs in plain `:app:testDebugUnitTest` with no emulator.
 * The full ONNX-embedding parity (needs the 73MB int8 model) is the separate androidTest
 * `com.viperplayer.data.rec.ClapParityTest`, which skips gracefully when the model asset is absent.
 */
class MelSpectrogramParityTest {

    // name -> cropIdx. <=10s clips use 0. long_mixed_14s used the golden generator's seeded offset
    // 43567 (see golden_manifest crop_idx). A shipping app pins cropIdx=0; here we match the goldens.
    private val clips = listOf(
        "sine_440_5s" to 0,
        "white_noise_10s" to 0,
        "long_mixed_14s" to 43567,
    )

    private companion object {
        const val RES_DIR = "clap_golden"
        const val MEL_COS_BAR = 0.99
    }

    @Test
    fun windowSumMatchesContract() {
        // The periodic Hann window must sum to 512.0 (mel_spec.json). A wrong window silently corrupts
        // every downstream mel value, so assert this cheap invariant first.
        assertEquals(512.0, MelSpectrogram.windowSum(), 1e-6)
    }

    @Test
    fun melShapeIsFixedContract() {
        val pcm = readResourceFloatsLE("$RES_DIR/sine_440_5s.pcm_f32le.bin")
        val mel = MelSpectrogram.computeLogMel(pcm, cropIdx = 0)
        assertEquals(MelSpectrogram.NUM_FRAMES * MelSpectrogram.N_MELS, mel.size)
    }

    @Test
    fun melParityMatchesGoldenMel() {
        var minCos = 1.0
        for ((name, cropIdx) in clips) {
            val pcm = readResourceFloatsLE("$RES_DIR/$name.pcm_f32le.bin")
            val goldMel = readResourceFloatsLE("$RES_DIR/$name.mel_f32le.bin")

            val mel = MelSpectrogram.computeLogMel(pcm, cropIdx)

            assertEquals("$name: mel length", goldMel.size, mel.size)
            val cos = cosine(mel, goldMel)
            val relErr = maxAbsRelErr(mel, goldMel)
            if (cos < minCos) minCos = cos
            println("MEL  %-16s cos=%.8f  maxRelErr=%.3e".format(name, cos, relErr))
            assertTrue("$name: mel cos $cos < $MEL_COS_BAR", cos >= MEL_COS_BAR)
        }
        println("MEL  min cos across clips = %.8f (bar %.2f)".format(minCos, MEL_COS_BAR))
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    private fun readResourceBytes(path: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("test resource not found on classpath: $path")
        stream.use { input ->
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

    private fun readResourceFloatsLE(path: String): FloatArray {
        val bytes = readResourceBytes(path)
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

    private fun maxAbsRelErr(a: FloatArray, b: FloatArray): Double {
        var maxDiff = 0.0; var maxRef = 0.0
        for (i in a.indices) {
            val d = abs(a[i] - b[i]).toDouble()
            if (d > maxDiff) maxDiff = d
            val r = abs(b[i]).toDouble()
            if (r > maxRef) maxRef = r
        }
        return maxDiff / (maxRef + 1e-30)
    }
}
