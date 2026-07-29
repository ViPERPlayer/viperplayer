package com.viperplayer.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM tests for [RecCaptureAudioProcessor]: the Path A tap. Feeds float [ByteBuffer]s through the
 * media3 [androidx.media3.common.audio.BaseAudioProcessor] lifecycle and asserts the invariants that
 * matter — it NEVER alters audio (output == input), it downmixes to mono, it caps at the clip budget and
 * hands off exactly once, it resets per track, and non-float input passes through capturing nothing.
 * Native-free by design (no ONNX / codec), so it runs off-device.
 */
class RecCaptureAudioProcessorTest {

    /** Captures each hand-off (mediaId, samples, sampleRate) so tests can assert on them. */
    private class RecordingListener : RecCaptureAudioProcessor.CaptureListener {
        data class Capture(val mediaId: String, val pcm: FloatArray, val sampleRate: Int)
        val captures = mutableListOf<Capture>()
        override fun onCaptured(mediaId: String, monoPcm: FloatArray, sampleRate: Int) {
            captures.add(Capture(mediaId, monoPcm, sampleRate))
        }
    }

    private fun floatFormat(sampleRate: Int, channels: Int) =
        AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_FLOAT)

    /** Interleaved float PCM ByteBuffer (native order, as media3 delivers it). */
    private fun floatBuffer(vararg samples: Float): ByteBuffer {
        val bb = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.nativeOrder())
        for (s in samples) bb.putFloat(s)
        bb.flip()
        return bb
    }

    /** Read a float ByteBuffer (from getOutput) back to a FloatArray. */
    private fun readFloats(buffer: ByteBuffer): FloatArray {
        val fb = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(fb.remaining()) { fb.get() }
    }

    @Test
    fun passesInputThroughUnchangedWhenNotArmed() {
        val p = RecCaptureAudioProcessor()
        p.configure(floatFormat(48_000, 2))
        p.flush(StreamMetadata.DEFAULT)

        val input = floatBuffer(0.1f, -0.2f, 0.3f, -0.4f)
        p.queueInput(input.duplicate())
        val out = readFloats(p.output)

        assertArrayEquals(floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f), out, 0f)
    }

    @Test
    fun passthroughIsIdenticalWhenArmedAndCapturing() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("t=plugin&p=testsource&s=song1")
        p.configure(floatFormat(48_000, 2))
        p.flush(StreamMetadata.DEFAULT)

        val input = floatBuffer(0.5f, 0.5f, -0.25f, -0.75f)
        p.queueInput(input.duplicate())

        // Output is byte-for-byte the input (the tap never mutates audio).
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, -0.25f, -0.75f), readFloats(p.output), 0f)
    }

    @Test
    fun downmixesStereoToMonoAndCapsAtClipBudget() {
        val listener = RecordingListener()
        // Tiny capture budget so the cap is reached quickly: 4 frames @ 4 Hz = 1s * 4s? Use a small
        // sampleRate + captureSeconds so maxFrames is small.
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("mid", captureSeconds = 1.0) // 1s @ 4 Hz -> 4 frames cap
        p.configure(floatFormat(4, 2)) // 4 Hz stereo
        p.flush(StreamMetadata.DEFAULT)

        // 6 stereo frames; cap is 4 -> only the first 4 mono frames captured, downmixed (avg of L,R).
        // frames: (1,1)->1, (0.5,-0.5)->0, (0.2,0.2)->0.2, (1,0)->0.5, (extra ignored)...
        p.queueInput(floatBuffer(1f, 1f, 0.5f, -0.5f, 0.2f, 0.2f, 1f, 0f, 0.9f, 0.9f, 0.3f, 0.3f))

        assertTrue(p.hasHandedOff)
        val capture = listener.captures.single()
        assertEquals("mid", capture.mediaId)
        assertEquals(4, capture.sampleRate)
        assertArrayEquals(floatArrayOf(1f, 0f, 0.2f, 0.5f), capture.pcm, 1e-6f)
    }

    @Test
    fun handsOffExactlyOnceEvenWithMoreInput() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("once", captureSeconds = 1.0)
        p.configure(floatFormat(2, 1)) // 2 Hz mono -> 2-frame cap
        p.flush(StreamMetadata.DEFAULT)

        p.queueInput(floatBuffer(0.1f, 0.2f)) // fills the cap -> hand off
        p.queueInput(floatBuffer(0.3f, 0.4f)) // more input, must NOT hand off again
        assertEquals(1, listener.captures.size)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f), listener.captures.single().pcm, 0f)
    }

    @Test
    fun endOfStreamHandsOffAPartialClipAboveTheFloor() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        // sampleRate = 2 -> minFramesForHandoff = 2; give exactly 2 frames then EOS before the cap.
        p.arm("short", captureSeconds = 10.0)
        p.configure(floatFormat(2, 1))
        p.flush(StreamMetadata.DEFAULT)

        p.queueInput(floatBuffer(0.7f, 0.8f))
        assertFalse(p.hasHandedOff) // cap (20 frames) not reached
        p.queueEndOfStream()

        assertTrue(p.hasHandedOff)
        assertArrayEquals(floatArrayOf(0.7f, 0.8f), listener.captures.single().pcm, 0f)
    }

    @Test
    fun tooShortTailAtEndOfStreamIsNotHandedOff() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        // sampleRate = 10 -> minFramesForHandoff = 10; only 1 frame captured -> below floor -> dropped.
        p.arm("tiny", captureSeconds = 10.0)
        p.configure(floatFormat(10, 1))
        p.flush(StreamMetadata.DEFAULT)

        p.queueInput(floatBuffer(0.9f))
        p.queueEndOfStream()
        assertTrue(listener.captures.isEmpty())
    }

    @Test
    fun resetAbandonsPartialAndReArmsPerTrack() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("trackA", captureSeconds = 1.0)
        p.configure(floatFormat(4, 1))
        p.flush(StreamMetadata.DEFAULT)
        p.queueInput(floatBuffer(0.1f, 0.2f)) // partial (cap = 4)

        // A new track: re-arm, then flush (same format -> no re-configure). Accumulation must restart.
        p.arm("trackB", captureSeconds = 1.0)
        p.flush(StreamMetadata.DEFAULT)
        p.queueInput(floatBuffer(0.5f, 0.6f, 0.7f, 0.8f)) // fills cap for trackB

        val capture = listener.captures.single()
        assertEquals("trackB", capture.mediaId)
        assertArrayEquals(floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f), capture.pcm, 0f)
    }

    @Test
    fun disarmStopsCapture() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("x", captureSeconds = 1.0)
        p.configure(floatFormat(2, 1))
        p.disarm()
        p.flush(StreamMetadata.DEFAULT) // re-begin against the now-null target
        p.queueInput(floatBuffer(0.1f, 0.2f))
        p.queueEndOfStream()
        assertTrue(listener.captures.isEmpty())
    }

    @Test
    fun nonFloatEncodingPassesThroughAndCapturesNothing() {
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("pcm16")
        p.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        p.flush(StreamMetadata.DEFAULT)

        // 16-bit input: 2 shorts = 4 bytes.
        val input = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
        input.putShort(100); input.putShort(-200); input.flip()
        p.queueInput(input.duplicate())

        // Passthrough copies the raw bytes unchanged.
        val out = p.output
        out.order(ByteOrder.nativeOrder())
        assertEquals(100.toShort(), out.short)
        assertEquals((-200).toShort(), out.short)
        // Nothing captured.
        assertTrue(listener.captures.isEmpty())
        assertFalse(p.hasHandedOff)
    }

    @Test
    fun gaplessReArmCapturesTheNewTrackWithoutSplicing() {
        // A GAPLESS same-format transition fires NEITHER onConfigure NOR onFlush; the only signal is the
        // new arm() (a new generation). The processor must reconcile that on the audio thread: begin a
        // FRESH head for the new track and drop the previous track's partial — never splice A's tail into
        // B's clip nor freeze on A.
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("A", captureSeconds = 1.0)
        p.configure(floatFormat(4, 1)) // 4-frame cap
        p.flush(StreamMetadata.DEFAULT)
        p.queueInput(floatBuffer(0.1f, 0.2f)) // A partial (2 of 4), NOT handed off

        p.arm("B", captureSeconds = 1.0) // gapless: re-armed, but NO flush/configure
        p.queueInput(floatBuffer(0.5f, 0.6f, 0.7f, 0.8f)) // B's head fills the cap

        val capture = listener.captures.single()
        assertEquals("B", capture.mediaId)
        assertArrayEquals(floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f), capture.pcm, 0f)
    }

    @Test
    fun midTrackSeekNeverCapturesPostSeekAudio() {
        // A mid-track seek flushes the chain with the SAME arm generation (nothing re-armed). The captured
        // clip must remain the HEAD — post-seek (mid-track) audio must never be accumulated or emitted.
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("A", captureSeconds = 10.0) // cap 40 (won't be hit); sampleRate 4 -> floor 4
        p.configure(floatFormat(4, 1))
        p.flush(StreamMetadata.DEFAULT)
        p.queueInput(floatBuffer(0.1f, 0.2f, 0.3f)) // 3 head frames (below the 4-frame floor)

        p.flush(StreamMetadata.DEFAULT) // SEEK (same generation) -> finalize head-so-far, then stop
        p.queueInput(floatBuffer(0.9f, 0.9f, 0.9f, 0.9f, 0.9f)) // post-seek: must be ignored
        p.queueEndOfStream()

        // Head was below the floor so nothing handed off; crucially the mid-track 0.9 audio is never taken.
        assertTrue(listener.captures.isEmpty())
    }

    @Test
    fun seekAfterEnoughHeadHandsOffOnlyTheHead() {
        // Same as above but the head captured before the seek clears the floor: it is handed off as the
        // song's clip and post-seek audio is still excluded (the stored vector is the HEAD, not mid-track).
        val listener = RecordingListener()
        val p = RecCaptureAudioProcessor().apply { captureListener = listener }
        p.arm("A", captureSeconds = 10.0) // sampleRate 2 -> floor 2, cap 20
        p.configure(floatFormat(2, 1))
        p.flush(StreamMetadata.DEFAULT)
        p.queueInput(floatBuffer(0.1f, 0.2f, 0.3f)) // 3 head frames (>= floor 2)

        p.flush(StreamMetadata.DEFAULT) // SEEK -> finalize the head-so-far
        p.queueInput(floatBuffer(0.9f, 0.9f)) // post-seek audio: ignored

        val capture = listener.captures.single()
        assertEquals("A", capture.mediaId)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), capture.pcm, 0f)
    }
}
