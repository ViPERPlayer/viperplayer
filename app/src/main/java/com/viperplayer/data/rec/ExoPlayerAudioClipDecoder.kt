package com.viperplayer.data.rec

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import androidx.media3.common.PlaybackParameters

/**
 * HEADLESS, device-only decoder that renders ~10s of audio from a NON-DRM adaptive [MediaSource]
 * (HLS/DASH — kinds a plain [android.media.MediaExtractor] can't handle) and returns the captured mono
 * float PCM. Used by [StreamingAudioSourceResolver] as the HLS/DASH branch of Path B.
 *
 * ## How it stays headless
 * A bare [ExoPlayer] runs on its own [HandlerThread] with a [RenderersFactory] whose audio sink is a
 * [DefaultAudioSink] carrying ONE capturing [BaseAudioProcessor] ([ClipCaptureProcessor]) at the head of
 * the chain. The player is muted (`volume = 0`) so nothing is audible, and it is released the instant
 * enough audio is captured. Capture is bounded to [captureSeconds] of source-rate audio; realtime pacing
 * is acceptable for a Wi-Fi + battery-not-low background batch worker.
 *
 * ## Limitations (device-only, untested on JVM)
 * Real ExoPlayer + codec need a device, so this is exercised only on the Pixel_9 AVD. DRM streams are
 * NOT handled here — the resolver skips them (Path A covers DRM via playback capture). If the clip can't
 * be captured within [timeoutSeconds] (network stall, unsupported codec), it returns null and the song
 * is skipped for this run.
 */
@OptIn(UnstableApi::class)
class ExoPlayerAudioClipDecoder(
    private val context: Context,
    private val captureSeconds: Double = DEFAULT_CAPTURE_SECONDS,
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) {

    /**
     * Render [mediaSource] headlessly and return the captured mono PCM (+ source sample rate), or null
     * on error/timeout/empty. Blocking; call from a background (worker) thread. Builds and tears down a
     * fresh ExoPlayer per call.
     */
    fun decode(mediaSource: MediaSource): DecodedAudio? {
        val playerThread = HandlerThread("rec-clip-decoder").apply { start() }
        val capture = ClipCaptureProcessor(captureSeconds)
        val doneLatch = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        var player: ExoPlayer? = null
        try {
            val p = ExoPlayer.Builder(context, renderersFactory(capture))
                .setLooper(playerThread.looper)
                .build()
            player = p
            capture.onFull = { doneLatch.countDown() }
            // Drive the player from ITS OWN looper thread (ExoPlayer is single-threaded per its looper).
            Handler(playerThread.looper).post {
                p.volume = 0f // headless: never audible
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Timber.w(error, "ExoPlayerAudioClipDecoder: player error")
                        failed.set(true)
                        doneLatch.countDown()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) doneLatch.countDown()
                    }
                })
                p.setMediaSource(mediaSource)
                p.prepare()
                p.play()
            }

            val finished = doneLatch.await(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) Timber.w("ExoPlayerAudioClipDecoder: timed out capturing clip")
            if (failed.get()) return null
            return capture.snapshot()
        } catch (e: Exception) {
            Timber.w(e, "ExoPlayerAudioClipDecoder: decode failed")
            return null
        } finally {
            // Release on the player's looper (required), then quit the thread.
            runCatching {
                val p = player
                if (p != null) {
                    val releaseLatch = CountDownLatch(1)
                    Handler(playerThread.looper).post {
                        runCatching { p.release() }
                        releaseLatch.countDown()
                    }
                    releaseLatch.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            playerThread.quitSafely()
        }
    }

    /** A renderers factory whose audio sink is a [DefaultAudioSink] fronted by [capture]. */
    private fun renderersFactory(capture: ClipCaptureProcessor): RenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setAudioProcessorChain(object : AudioProcessorChain {
                        override fun getAudioProcessors(): Array<AudioProcessor> = arrayOf(capture)
                        override fun applyPlaybackParameters(p: PlaybackParameters): PlaybackParameters = p
                        override fun applySkipSilenceEnabled(enabled: Boolean): Boolean = enabled
                        override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration
                        override fun getSkippedOutputFrameCount(): Long = 0
                    })
                    .build()
        }.setEnableAudioFloatOutput(true)

    /**
     * A pure-passthrough capturing processor: accumulates mono float PCM (downmixing by the configured
     * channel count) up to [captureSeconds] of source-rate audio, then signals [onFull]. Non-float input
     * is passed through and NOT captured (the sink is forced to float output, so this is defensive).
     */
    private class ClipCaptureProcessor(private val captureSeconds: Double) : BaseAudioProcessor() {
        @Volatile
        var onFull: (() -> Unit)? = null

        private var channelCount = 0
        private var sampleRate = 0
        private var isFloat = false
        private var mono = FloatArray(0)
        private var count = 0
        private var maxFrames = 0
        @Volatile
        private var full = false

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            channelCount = inputAudioFormat.channelCount
            sampleRate = inputAudioFormat.sampleRate
            isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            if (isFloat && sampleRate > 0 && channelCount > 0) {
                maxFrames = Math.ceil(captureSeconds * sampleRate).toInt().coerceAtLeast(1)
                if (mono.size < maxFrames) mono = FloatArray(maxFrames)
            }
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            if (isFloat && !full && maxFrames > 0) {
                val floats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
                val total = floats.remaining()
                var i = 0
                while (i + channelCount <= total && count < maxFrames) {
                    var acc = 0f
                    var c = 0
                    while (c < channelCount) { acc += floats.get(i + c); c++ }
                    mono[count++] = acc / channelCount
                    i += channelCount
                }
                if (count >= maxFrames && !full) {
                    full = true
                    onFull?.invoke()
                }
            }
            // Passthrough.
            val out = replaceOutputBuffer(size)
            out.put(inputBuffer)
            out.flip()
        }

        /** The captured mono clip + source sample rate as a [DecodedAudio] (mono, channels=1), or null. */
        fun snapshot(): DecodedAudio? {
            if (count <= 0 || sampleRate <= 0) return null
            return DecodedAudio(interleaved = mono.copyOf(count), sampleRate = sampleRate, channels = 1)
        }
    }

    companion object {
        const val DEFAULT_CAPTURE_SECONDS = 11.0
        private const val DEFAULT_TIMEOUT_SECONDS = 45L
        private const val RELEASE_TIMEOUT_SECONDS = 5L
    }
}
