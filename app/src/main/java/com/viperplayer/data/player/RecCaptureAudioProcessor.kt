package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
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
 * ## Arming & the "always the HEAD" guarantee
 * The processor is idle until [arm] targets a specific `mediaId`. Each [arm]/[disarm] advances an
 * `armGeneration`, and a FRESH accumulation only ever begins for a new generation — i.e. at a genuine
 * new track start ([onMediaItemTransition] arms once per real transition, never on an intra-track seek).
 * This makes the captured clip ALWAYS the contiguous HEAD of the track:
 *  - a mid-track **seek** ([onFlush] with an unchanged generation) does NOT restart accumulation from the
 *    seek position — it finalizes the head captured so far and stops, so a mid-track clip is never stored;
 *  - a **gapless** same-format transition fires neither [onConfigure] nor [onFlush], so the target change
 *    is reconciled on the audio thread inside [queueInput] (a new generation ⇒ begin a fresh head), which
 *    prevents splicing the previous track's tail into the next track's clip.
 * While armed, each float frame is downmixed to mono and accumulated up to [captureSeconds] of SOURCE-rate
 * audio (a margin over the 10s CLAP clip so the post-resample buffer comfortably reaches 480000 samples @
 * 48kHz). Once the cap is reached — or the track ends — it snapshots the mono FloatArray + source sample
 * rate and hands them to [captureListener] on a background thread.
 *
 * ## Threading
 * [arm]/[disarm] are called from the playback service (main thread, serialized) and only advance the
 * `@Volatile` generation + target. The accumulator and its cursor are touched ONLY on the audio
 * (queueInput/onFlush/onReset) thread, which also READS the volatile arm state — so no locking is needed
 * on the hot path, and it does NO heap allocation while capturing (absolute [ByteBuffer.getFloat] reads,
 * a reused mono buffer). The one cross-thread hand-off is [captureListener], invoked with a fresh COPY of
 * the samples so the audio thread can keep reusing its buffer.
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

    // ---- Arming state (written on the main thread, read on the audio thread) ----

    /** The mediaId we are currently capturing for, or null when disarmed. */
    @Volatile
    private var armedMediaId: String? = null

    /**
     * Monotonically advanced by every [arm]/[disarm]. A fresh HEAD accumulation begins only when this
     * differs from [capturingGeneration], so a genuine new track (incl. the gapless case) starts clean
     * while an intra-track seek (which never re-arms) does not restart capture. Written only on the main
     * thread (arm/disarm are serialized there), read on the audio thread — `@Volatile` for visibility.
     */
    @Volatile
    private var armGeneration: Long = 0L

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

    /** Identity of the clip currently being accumulated (the armed target at [beginAccumulation] time). */
    private var capturingMediaId: String? = null

    /** The [armGeneration] the current accumulation began for; `-1` forces a re-begin on the next frame. */
    private var capturingGeneration: Long = -1L

    /** True once this clip has been handed off / abandoned, so we don't re-emit for the same accumulation. */
    private var handedOff: Boolean = false

    /**
     * ARM capture for [mediaId]. Advances the generation so the next new-track audio starts a fresh HEAD
     * accumulation for this target. Safe to call from the main thread. [captureSeconds] overrides the clip
     * budget. Callers MUST serialize arm/disarm (single writer) — the audio thread only reads this state.
     */
    fun arm(mediaId: String, captureSeconds: Double = DEFAULT_CAPTURE_SECONDS) {
        this.captureSeconds = captureSeconds
        armedMediaId = mediaId
        armGeneration += 1
    }

    /** DISARM capture (no target). Advances the generation so any in-flight accumulation is abandoned. */
    fun disarm() {
        armedMediaId = null
        armGeneration += 1
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        // Force the next frame to (re)begin accumulation for whatever is armed at the new format.
        capturingGeneration = -1L
        // Pure passthrough: the output format is identical to the input format.
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        // A flush is either a genuine new target (the next track was already re-armed ⇒ armGeneration
        // advanced past capturingGeneration) or an intra-track SEEK (same generation — nothing re-armed).
        // New target: reconcile on the next frame (fresh head). Seek: finalize the head captured so far
        // and STOP, so post-seek (mid-track) audio is never accumulated or emitted.
        if (armGeneration != capturingGeneration) {
            capturingGeneration = -1L
        } else {
            finalizeOnInterruption()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        // Reconcile the target on the audio thread: a new arm generation (a genuine new track, INCLUDING
        // the gapless case where neither onConfigure nor onFlush fires) begins a fresh HEAD accumulation.
        if (armGeneration != capturingGeneration) beginAccumulation()

        // Capture BEFORE handing the bytes on, using ABSOLUTE getFloat reads so the caller's buffer
        // position is untouched (the passthrough copy below still sees the full payload) and NO NIO view
        // objects are allocated on the real-time audio thread.
        if (shouldCapture()) accumulate(inputBuffer)

        // Pure passthrough: copy input -> output unchanged.
        val outputBuffer = replaceOutputBuffer(size)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    /** (Re)initialize the accumulator for the currently-armed target at the current format. */
    private fun beginAccumulation() {
        capturingGeneration = armGeneration
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

    /** Whether we should be accumulating right now (armed for a float track, not yet full/handed off). */
    private fun shouldCapture(): Boolean =
        isFloat && !handedOff && maxMonoFrames > 0 && capturingMediaId != null &&
            capturingGeneration == armGeneration && capturingMediaId == armedMediaId

    /**
     * Downmix the float frames in [input] to mono (average channels) and append to [monoBuffer] up to
     * [maxMonoFrames]. Once full, hand off. Allocation-free: reads floats via ABSOLUTE [ByteBuffer.getFloat]
     * over `position()..limit()` (which does NOT advance the buffer), so the subsequent passthrough copy is
     * unaffected and no per-call NIO view is allocated on the audio thread.
     */
    private fun accumulate(input: ByteBuffer) {
        val ch = channelCount
        if (ch <= 0) return
        val bytesPerFrame = ch * BYTES_PER_FLOAT
        val end = input.limit()
        var byteIdx = input.position()
        while (byteIdx + bytesPerFrame <= end && monoCount < maxMonoFrames) {
            var acc = 0f
            var c = 0
            while (c < ch) {
                acc += input.getFloat(byteIdx + c * BYTES_PER_FLOAT)
                c++
            }
            monoBuffer[monoCount++] = acc / ch
            byteIdx += bytesPerFrame
        }
        if (monoCount >= maxMonoFrames) handOff()
    }

    /**
     * Finalize the current accumulation on an interruption (a seek, or a same-generation flush): hand off
     * the contiguous HEAD captured so far if it is a usable length, then STOP so no post-interruption
     * (mid-track) audio is ever appended or emitted for this target.
     */
    private fun finalizeOnInterruption() {
        if (!handedOff && maxMonoFrames > 0 && capturingMediaId != null && monoCount >= minFramesForHandoff()) {
            handOff()
        }
        handedOff = true
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
        // Track ended before the cap: hand off the head we have if it's a usable minimum. A too-short clip
        // is still repeat-padded to 10s by the mel front-end, but require a small floor so a near-empty
        // tail isn't embedded as if it were the song.
        if (shouldCapture() && monoCount >= minFramesForHandoff()) handOff()
    }

    override fun onReset() {
        // Track/format teardown: drop any partial accumulation. The next frame re-begins if still armed.
        monoCount = 0
        handedOff = false
        capturingMediaId = null
        capturingGeneration = -1L
        maxMonoFrames = 0
    }

    /** Minimum captured frames to bother embedding a truncated head (≈1s of source audio). */
    private fun minFramesForHandoff(): Int = if (sampleRate > 0) sampleRate else 1

    /** True when a usable clip has been accumulated and dispatched (test hook). */
    val hasHandedOff: Boolean get() = handedOff

    companion object {
        /** Default seconds of source-rate audio to capture (margin over the 10s CLAP clip). */
        const val DEFAULT_CAPTURE_SECONDS = 12.0

        private const val BYTES_PER_FLOAT = 4
    }
}
