package com.viperplayer.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Covers the rate-agnostic and interleaved entry points added for convolver kernels. The existing
 * `ResamplerTest` (in `data/rec`) pins the mono 48 kHz path the CLAP front-end depends on; these
 * tests cover what that path never exercised — an arbitrary destination rate, and multi-channel
 * input where the channels must not bleed into each other.
 */
class ResamplerInterleavedTest {

    @Test
    fun `returns the input untouched when the rates match`() {
        val input = FloatArray(64) { it.toFloat() }

        assertSame(input, Resampler.resample(input, 44100, 44100))
        assertSame(input, Resampler.resampleInterleaved(input, channels = 2, srcSampleRate = 44100, dstSampleRate = 44100))
    }

    @Test
    fun `scales length by the rate ratio`() {
        val input = FloatArray(4410) { sin(2.0 * PI * 440.0 * it / 44100).toFloat() }

        val upsampled = Resampler.resample(input, 44100, 48000)

        // 0.1s of audio at 48k is 4800 frames.
        assertEquals(4800, upsampled.size)
    }

    @Test
    fun `preserves a tone's frequency across a rate change`() {
        // 1 kHz at 44.1k -> 48k must still be 1 kHz, not 1 kHz * 48/44.1. This is exactly the
        // failure mode an unresampled impulse response has: every feature of the response lands at
        // the wrong frequency.
        val toneHz = 1000.0
        val srcRate = 44100
        val dstRate = 48000
        val input = FloatArray(srcRate) { sin(2.0 * PI * toneHz * it / srcRate).toFloat() }

        val output = Resampler.resample(input, srcRate, dstRate)

        assertEquals(dstRate, output.size)
        // Count zero crossings over the middle of the buffer (skipping the kernel's edge roll-off)
        // and turn that back into a frequency: 2 crossings per cycle.
        val from = dstRate / 4
        val to = dstRate * 3 / 4
        var crossings = 0
        for (i in from until to) {
            if ((output[i] < 0f) != (output[i - 1] < 0f)) crossings++
        }
        val measuredHz = crossings / 2.0 / ((to - from).toDouble() / dstRate)
        assertTrue("expected ~${toneHz}Hz, measured ${measuredHz}Hz", abs(measuredHz - toneHz) < 5.0)
    }

    @Test
    fun `resamples interleaved channels independently`() {
        // Left is a tone, right is silence. If the channels were resampled together — or the
        // de-interleave were off by one — energy would leak from left into right.
        val srcRate = 44100
        val frames = srcRate / 10
        val input = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            input[frame * 2] = sin(2.0 * PI * 1000.0 * frame / srcRate).toFloat()
            input[frame * 2 + 1] = 0f
        }

        val output = Resampler.resampleInterleaved(input, channels = 2, srcSampleRate = srcRate, dstSampleRate = 48000)

        assertEquals(4800 * 2, output.size)
        var leftPeak = 0f
        var rightPeak = 0f
        for (frame in 0 until output.size / 2) {
            leftPeak = maxOf(leftPeak, abs(output[frame * 2]))
            rightPeak = maxOf(rightPeak, abs(output[frame * 2 + 1]))
        }
        assertTrue("left channel should survive, peak=$leftPeak", leftPeak > 0.9f)
        assertEquals("right channel must stay silent", 0f, rightPeak, 1e-6f)
    }

    @Test
    fun `handles mono interleaved input`() {
        val input = FloatArray(4410) { sin(2.0 * PI * 440.0 * it / 44100).toFloat() }

        val output = Resampler.resampleInterleaved(input, channels = 1, srcSampleRate = 44100, dstSampleRate = 48000)

        assertEquals(Resampler.resample(input, 44100, 48000).toList(), output.toList())
    }

    @Test
    fun `downsamples as well as upsamples`() {
        val input = FloatArray(4800) { sin(2.0 * PI * 440.0 * it / 48000).toFloat() }

        val output = Resampler.resample(input, 48000, 44100)

        assertEquals(4410, output.size)
    }

    @Test
    fun `empty and degenerate inputs do not throw`() {
        assertEquals(0, Resampler.resample(FloatArray(0), 44100, 48000).size)
        assertEquals(0, Resampler.resampleInterleaved(FloatArray(0), 2, 44100, 48000).size)
        // A non-positive channel count is a caller bug, not a crash: hand the buffer back unchanged.
        val input = FloatArray(8) { it.toFloat() }
        assertSame(input, Resampler.resampleInterleaved(input, channels = 0, srcSampleRate = 44100, dstSampleRate = 48000))
    }
}
