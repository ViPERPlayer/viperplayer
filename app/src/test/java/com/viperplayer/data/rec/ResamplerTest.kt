package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure-JVM tests for the canonical [MelSpectrogram.resampleTo48k] / [MelSpectrogram.downmixToMono].
 * These lock the two properties that matter for embedding parity: the output length obeys the sample-
 * rate ratio, and a pure sine keeps its frequency (no gross aliasing / pitch shift) across a resample.
 */
class ResamplerTest {

    @Test
    fun resampleAt48kIsIdentity() {
        val x = FloatArray(1000) { it.toFloat() }
        val out = MelSpectrogram.resampleTo48k(x, 48_000)
        assertTrue("48k resample should return the same reference", out === x)
    }

    @Test
    fun upsampleLengthObeysRatio() {
        // 24kHz -> 48kHz doubles the sample count (ratio 2.0). floor(n * ratio).
        val n = 24_000
        val x = FloatArray(n) { 0f }
        val out = MelSpectrogram.resampleTo48k(x, 24_000)
        assertEquals(2 * n, out.size)
    }

    @Test
    fun downsampleLengthObeysRatio() {
        // 96kHz -> 48kHz halves the sample count (ratio 0.5).
        val n = 96_000
        val x = FloatArray(n) { 0f }
        val out = MelSpectrogram.resampleTo48k(x, 96_000)
        assertEquals(n / 2, out.size)
    }

    @Test
    fun arbitraryRatioLengthObeysRatio() {
        // 44100 -> 48000. outLen = floor(n * 48000/44100).
        val n = 44_100
        val x = FloatArray(n) { 0f }
        val out = MelSpectrogram.resampleTo48k(x, 44_100)
        val expected = Math.floor(n * 48_000.0 / 44_100.0).toInt()
        assertEquals(expected, out.size)
    }

    @Test
    fun emptyInputResamplesToEmpty() {
        assertEquals(0, MelSpectrogram.resampleTo48k(FloatArray(0), 44_100).size)
    }

    @Test
    fun resampledSinePreservesFrequency() {
        // A 1kHz sine at 24kHz, upsampled to 48kHz, must still be a 1kHz sine. Verify by measuring the
        // dominant frequency of the resampled signal via its zero-crossing rate (robust + backend-free).
        val srcSr = 24_000
        val toneHz = 1_000.0
        val durationS = 0.5
        val srcN = (srcSr * durationS).toInt()
        val src = FloatArray(srcN) { i -> sin(2.0 * PI * toneHz * i / srcSr).toFloat() }

        val out = MelSpectrogram.resampleTo48k(src, srcSr)
        val dstSr = 48_000
        assertEquals(2 * srcN, out.size)

        // Count positive-going zero crossings; freq ≈ crossings / duration. Ignore the kernel-warmup
        // and tail edges where the windowed-sinc has fewer taps.
        val guard = 64
        var crossings = 0
        for (i in (guard + 1) until (out.size - guard)) {
            if (out[i - 1] <= 0f && out[i] > 0f) crossings++
        }
        val measuredDurationS = (out.size - 2 * guard).toDouble() / dstSr
        val measuredHz = crossings / measuredDurationS
        assertEquals("resampled tone frequency", toneHz, measuredHz, 30.0) // within ~3%

        // And the amplitude should be preserved (≈1.0 peak), not collapsed by the filter.
        var peak = 0f
        for (i in guard until out.size - guard) if (abs(out[i]) > peak) peak = abs(out[i])
        assertTrue("resampled peak $peak should be ~1.0", peak in 0.9f..1.1f)
    }

    @Test
    fun downmixStereoAveragesChannels() {
        // Interleaved L/R: L constant 1.0, R constant -1.0 -> mono average 0.0.
        val frames = 100
        val interleaved = FloatArray(frames * 2)
        for (i in 0 until frames) {
            interleaved[i * 2] = 1.0f
            interleaved[i * 2 + 1] = -1.0f
        }
        val mono = MelSpectrogram.downmixToMono(interleaved, 2)
        assertEquals(frames, mono.size)
        for (v in mono) assertEquals(0.0f, v, 1e-6f)
    }

    @Test
    fun downmixMonoIsIdentity() {
        val x = FloatArray(10) { it.toFloat() }
        assertTrue(MelSpectrogram.downmixToMono(x, 1) === x)
    }

    @Test
    fun downmixThreeChannelAverages() {
        // 3 channels of constant 3, 6, 9 -> mono 6.0.
        val frames = 5
        val interleaved = FloatArray(frames * 3)
        for (i in 0 until frames) {
            interleaved[i * 3] = 3f
            interleaved[i * 3 + 1] = 6f
            interleaved[i * 3 + 2] = 9f
        }
        val mono = MelSpectrogram.downmixToMono(interleaved, 3)
        assertEquals(frames, mono.size)
        for (v in mono) assertEquals(6.0f, v, 1e-5f)
    }
}
