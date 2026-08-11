package com.viperplayer.domain.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The app's single sample-rate converter.
 *
 * Linear-phase windowed-sinc (Blackman-windowed) interpolation: each output sample convolves a
 * band-limited sinc kernel over the neighbouring input samples, with the cutoff pulled down to the
 * destination Nyquist when downsampling so nothing folds back into the band.
 *
 * Lifted out of `MelSpectrogram.resampleTo48k` unchanged — that was the only resampler in the app,
 * but it was hard-wired to 48 kHz and lived inside the recommender's mel front-end, so the DSP side
 * could not use it for convolver kernels. `resampleTo48k` now delegates here, so the recommender's
 * vector parity with the server still rides on exactly this code.
 */
object Resampler {

    /** Half-width of the interpolation kernel, in output taps. */
    private const val KERNEL_HALF_WIDTH = 16

    /**
     * Resamples mono PCM from [srcSampleRate] to [dstSampleRate].
     *
     * @return the input unchanged when the rates match. Empty in -> empty out.
     */
    fun resample(mono: FloatArray, srcSampleRate: Int, dstSampleRate: Int): FloatArray {
        require(srcSampleRate > 0) { "srcSampleRate must be > 0, got $srcSampleRate" }
        require(dstSampleRate > 0) { "dstSampleRate must be > 0, got $dstSampleRate" }
        if (srcSampleRate == dstSampleRate) return mono
        if (mono.isEmpty()) return FloatArray(0)

        val ratio = dstSampleRate.toDouble() / srcSampleRate // >1 upsample, <1 downsample
        val outLen = floor(mono.size * ratio).toInt()
        if (outLen <= 0) return FloatArray(0)
        val out = FloatArray(outLen)

        // Anti-alias cutoff, as a fraction of the SOURCE Nyquist. On downsampling, lower it to the
        // destination Nyquist so energy above it is not folded back into the band.
        val cutoff = if (ratio < 1.0) ratio else 1.0
        // Kernel spans +-KERNEL_HALF_WIDTH output taps, widened by 1/cutoff when downsampling.
        val filterHalf = KERNEL_HALF_WIDTH / cutoff

        val n = mono.size
        for (i in 0 until outLen) {
            val srcPos = i / ratio // position in source-sample space
            val left = floor(srcPos - filterHalf).toInt()
            val right = floor(srcPos + filterHalf).toInt()
            var acc = 0.0
            var norm = 0.0
            var j = left
            while (j <= right) {
                if (j in 0 until n) {
                    val x = (srcPos - j) * cutoff
                    val w = sincWindowed(x, srcPos - j, filterHalf)
                    acc += mono[j].toDouble() * w
                    norm += w
                }
                j++
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0.0f
        }
        return out
    }

    /**
     * Resamples interleaved multi-channel PCM from [srcSampleRate] to [dstSampleRate], one channel
     * at a time so no energy leaks between them.
     *
     * @return the input unchanged when the rates match, or when [channels] is not positive.
     */
    fun resampleInterleaved(
        interleaved: FloatArray,
        channels: Int,
        srcSampleRate: Int,
        dstSampleRate: Int,
    ): FloatArray {
        if (channels <= 0 || srcSampleRate == dstSampleRate || interleaved.isEmpty()) return interleaved
        if (channels == 1) return resample(interleaved, srcSampleRate, dstSampleRate)

        val frames = interleaved.size / channels
        val resampled = Array(channels) { channel ->
            val mono = FloatArray(frames) { frame -> interleaved[frame * channels + channel] }
            resample(mono, srcSampleRate, dstSampleRate)
        }

        // Channels are resampled independently, so guard against an off-by-one between them rather
        // than assuming they came back the same length.
        val outFrames = resampled.minOf { it.size }
        val out = FloatArray(outFrames * channels)
        for (frame in 0 until outFrames) {
            for (channel in 0 until channels) {
                out[frame * channels + channel] = resampled[channel][frame]
            }
        }
        return out
    }

    /**
     * Band-limited sinc weight, Blackman-windowed over [-filterHalf, +filterHalf] input-sample
     * offsets.
     *
     * @param sincArg cutoff-scaled distance (feeds sinc(pi * sincArg)).
     * @param offset  raw input-sample offset from the interpolation position (feeds the window).
     */
    private fun sincWindowed(sincArg: Double, offset: Double, filterHalf: Double): Double {
        // Blackman window over the raw offset in [-filterHalf, filterHalf].
        val t = (offset + filterHalf) / (2.0 * filterHalf) // 0..1
        if (t < 0.0 || t > 1.0) return 0.0
        val window = 0.42 - 0.5 * cos(2.0 * PI * t) + 0.08 * cos(4.0 * PI * t)
        return sinc(sincArg) * window
    }

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val px = PI * x
        return sin(px) / px
    }
}
