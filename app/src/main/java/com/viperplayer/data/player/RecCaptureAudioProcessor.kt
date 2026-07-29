package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import androidx.media3.common.audio.AudioProcessor.StreamMetadata

/**
 * A PURE PASSTHROUGH [BaseAudioProcessor] that opportunistically TAPS the clean, pre-DSP float PCM of
 * the currently-playing track so a streaming-only song can be given a CLAP audio embedding WITHOUT any
 * extra network (the audio is already streaming for playback). It is Path A of the streaming-embedding
 * feature; because playback already decrypts/decodes to PCM, this is the only path that can cover DRM /
 * ClearKey tracks (which the background Path B intentionally skips).
 *
 * ## Contract
 *  - It NEVER alters audio: [queueInput] copies input -> output byte-for-byte and hands the buffer on.
 *  - It only captures [C.ENCODING_PCM_FLOAT] input at the track's native sample rate (the app's sink is
 *    float, see `PlaybackService.createExoPlayerRenderersFactory`). Any other encoding still passes
 *    through untouched but captures nothing.
 *  - Inserted FIRST in the chain (before [ViperAudioProcessor]) so it sees CLEAN audio, unaffected by the
 *    ViPER DSP / speed / silence-skip stages.
 *
 * ## Arming
 * The processor is idle until [arm] targets a specific `mediaId`. While armed, it downmixes each float
 * frame to mono and accumulates it into a preallocated buffer up to [captureSeconds] of SOURCE-rate
 * audio (a margin over the 10s CLAP clip so the post-resample buffer comfortably reaches 480000 samples
 * @ 48kHz). Once the cap is reached — or the track ends / resets — it snapshots the mono FloatArray +
 * source sample rate and hands them to [onCapture] on a background thread (the accumulation stays on the
 * audio thread; the heavy mel/ONNX work does not).
 *
 * ## Threading
 * [arm]/[disarm] are called from the playback service (main thread) and only flip `@Volatile` fields.
 * The accumulator and its cursor are touched ONLY on the audio (queueInput) thread, so no locking is
 * needed on the hot path — it just reuses buffers and does allocation-light work. The one cross-thread
 * hand-off is [onCapture], invoked with a fresh COPY of the samples so the audio thread can keep reusing
 * its buffer.
 *
 * Deliberately native-free and Android-`Context`-free so the whole capture/threshold/downmix/reset logic
 * is unit-testable on the JVM by feeding it float [ByteBuffer]s (see `RecCaptureAudioProcessorTest`).
 */
@OptIn(UnstableApi::class)
@Singleton
class RecCaptureAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    /** Callback invoked (off the audio thread) with a captured mono PCM clip for [mediaId]. */
    fun interface CaptureListener {
        /** @param monoPcm mono float PCM at [sampleRate] Hz (a fresh copy the caller owns). */
        fun onCaptured(mediaId: String, monoPcm: FloatArray, sampleRate: Int)
    }

    /** Set once by the DI wiring; the sink that receives captured clips (the [StreamingCaptureEmbedder]). */
    @Volatile
    var captureListener: CaptureListener? = null

    // ---- Arming state (written on main thread, read on the audio thread) ----

    /** The mediaId we are currently capturing for, or null when disarmed. */
    @Volatile
    private var armedMediaId: String? = null

    /**
     * Seconds of SOURCE-rate audio to capture. 12s > the 10s (480000-sample @ 48kHz) CLAP clip contract,
     * so after resampling to 48kHz the buffer safely covers the first 10s. Volatile so [arm] can pin it.
     */
    @Volatile
    private var captureSeconds: Double = DEFAULT_CAPTURE_SECONDS

    // ---- Format captured in onConfigure (audio thread) ----
    private var channelCount: Int = 0
    private var sampleRate: Int = 0
    private var isFloat: Boolean = false

    // ---- Accumulator (audio thread only) ----
    private var monoBuffer: FloatArray = FloatArray(0)
    private var monoCount: Int = 0
    private var maxMonoFrames: Int = 0

    /**
     * Identity of the clip currently being accumulated. Captured from [armedMediaId] on the FIRST frame
     * after (re)configuration so a mid-track arm/disarm can't splice two tracks' audio into one clip.
     */
    private var capturingMediaId: String? = null

    /** True once this clip has been handed off, so we don't re-emit for the same accumulation. */
    private var handedOff: Boolean = false

    /**
     * ARM capture for [mediaId]. The next configured float track whose transition set this target will be
     * accumulated. Safe to call from the main thread. [captureSeconds] overrides the default clip budget.
     */
    fun arm(mediaId: String, captureSeconds: Double = DEFAULT_CAPTURE_SECONDS) {
        this.captureSeconds = captureSeconds
        armedMediaId = mediaId
    }

    /** DISARM capture (no target). Any in-flight accumulation is abandoned on the next reset. */
    fun disarm() {
        armedMediaId = null
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        beginAccumulation()
        // Pure passthrough: the output format is identical to the input format.
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        // Track boundaries and seeks flush the chain WITHOUT re-configuring when the format is unchanged
        // (onConfigure only fires on a real format change). Re-begin accumulation here so each new track
        // (or post-seek segment) captures for the currently-armed target and never splices two tracks.
        beginAccumulation()
    }

    /** (Re)initialize the accumulator for the currently-armed target at the current format. */
    private fun beginAccumulation() {
        capturingMediaId = armedMediaId
        handedOff = false
        monoCount = 0
        if (isFloat && capturingMediaId != null && sampleRate > 0 && channelCount > 0) {
            maxMonoFrames = Math.ceil(captureSeconds * sampleRate).toInt().coerceAtLeast(1)
            if (monoBuffer.size < maxMonoFrames) monoBuffer = FloatArray(maxMonoFrames)
        } else {
            maxMonoFrames = 0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        // Capture BEFORE handing the bytes on, without consuming the caller's buffer: read from a
        // duplicate so the original position/limit are untouched for the passthrough copy below.
        if (shouldCapture()) {
            accumulate(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        }

        // Pure passthrough: copy input -> output unchanged.
        val outputBuffer = replaceOutputBuffer(size)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    /** Whether we should be accumulating right now (armed for a float track, not yet full/handed off). */
    private fun shouldCapture(): Boolean =
        isFloat && !handedOff && maxMonoFrames > 0 && capturingMediaId != null &&
            capturingMediaId == armedMediaId

    /**
     * Downmix the float frames in [src] to mono (average channels) and append to [monoBuffer] up to
     * [maxMonoFrames]. Once full, snapshot + hand off. Allocation-light: no per-frame boxing, reuses the
     * preallocated buffer. [src] is a float-ordered duplicate positioned at the payload.
     */
    private fun accumulate(src: ByteBuffer) {
        val floats = src.asFloatBuffer()
        val ch = channelCount
        val totalFloats = floats.remaining()
        var i = 0
        while (i + ch <= totalFloats && monoCount < maxMonoFrames) {
            var acc = 0f
            var c = 0
            while (c < ch) {
                acc += floats.get(i + c)
                c++
            }
            monoBuffer[monoCount++] = acc / ch
            i += ch
        }
        if (monoCount >= maxMonoFrames) handOff()
    }

    /** Snapshot the accumulated mono clip and dispatch it to the listener (off the audio thread). */
    private fun handOff() {
        if (handedOff) return
        val mediaId = capturingMediaId ?: return
        if (monoCount <= 0) return
        handedOff = true
        val copy = monoBuffer.copyOf(monoCount)
        val sr = sampleRate
        // Fire-and-forget: the listener owns moving this to a background scope. Never touch the
        // accumulator after this — the copy is independent.
        captureListener?.onCaptured(mediaId, copy, sr)
    }

    override fun onQueueEndOfStream() {
        // Track ended before the cap: hand off whatever we have if it's a usable minimum. A too-short
        // clip is still repeat-padded to 10s by the mel front-end, but require a small floor so a
        // near-empty tail isn't embedded as if it were the song.
        if (shouldCapture() && monoCount >= minFramesForHandoff()) handOff()
    }

    override fun onReset() {
        // Track/format teardown: drop any partial accumulation. A new onConfigure re-arms if targeted.
        monoCount = 0
        handedOff = false
        capturingMediaId = null
        maxMonoFrames = 0
    }

    /** Minimum captured frames to bother embedding a truncated tail (≈1s of source audio). */
    private fun minFramesForHandoff(): Int = if (sampleRate > 0) sampleRate else 1

    /** True when a usable clip has been accumulated and dispatched (test hook). */
    val hasHandedOff: Boolean get() = handedOff

    companion object {
        /** Default seconds of source-rate audio to capture (margin over the 10s CLAP clip). */
        const val DEFAULT_CAPTURE_SECONDS = 12.0
    }
}
