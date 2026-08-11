package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * Pure-JVM guards for [MediaCodecAudioDecoder]'s decode-length cap. The cap itself lives inside the
 * `MediaCodec` dequeue loop (device-only), but its arithmetic is what breaks, so the arithmetic is
 * pinned here.
 *
 * Two properties matter:
 *  * [MediaCodecAudioDecoder.UNBOUNDED_SECONDS] must saturate the frame cap to [Int.MAX_VALUE] rather
 *    than wrapping, so an impulse response decodes to EOS instead of being truncated;
 *  * the `frames >= maxFrames * channels` comparison must be done in `Long`. In `Int` it overflows to
 *    a negative value for a saturated cap, which makes the comparison trivially true and truncates the
 *    decode to its first output buffer.
 */
class DecodeFrameCapTest {

    /** Mirrors the cap computation in `MediaCodecAudioDecoder.pumpDecoder`. */
    private fun maxFrames(seconds: Double, sampleRate: Int): Int =
        ceil(seconds * sampleRate).toInt()

    @Test
    fun clapBudgetCapsAtElevenSecondsOfSource() {
        assertEquals(528_000, maxFrames(MediaCodecAudioDecoder.DECODE_SECONDS, 48_000))
        assertEquals(485_100, maxFrames(MediaCodecAudioDecoder.DECODE_SECONDS, 44_100))
    }

    @Test
    fun unboundedBudgetSaturatesRatherThanWrapping() {
        for (rate in intArrayOf(8_000, 44_100, 48_000, 96_000, 192_000)) {
            assertEquals(
                "unbounded cap must saturate at rate $rate",
                Int.MAX_VALUE,
                maxFrames(MediaCodecAudioDecoder.UNBOUNDED_SECONDS, rate),
            )
        }
    }

    @Test
    fun saturatedCapStaysPositiveInLongMath() {
        val cap = maxFrames(MediaCodecAudioDecoder.UNBOUNDED_SECONDS, 48_000)
        for (channels in 1..8) {
            assertTrue(
                "cap * $channels must stay positive in Long math",
                cap.toLong() * channels > 0L,
            )
        }
    }

    @Test
    fun intMathWouldOverflowForStereo() {
        // The regression this guards: the same product in Int wraps negative, so the decode loop's
        // `size >= cap * channels` test fires on the very first output buffer.
        val cap = maxFrames(MediaCodecAudioDecoder.UNBOUNDED_SECONDS, 48_000)
        assertTrue("Int math is expected to overflow here", cap * 2 < 0)
    }

    @Test
    fun boundedCapIsUnaffectedByLongMath() {
        val cap = maxFrames(MediaCodecAudioDecoder.DECODE_SECONDS, 48_000)
        assertEquals(cap.toLong() * 2, (cap * 2).toLong())
    }
}
