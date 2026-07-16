package com.viperplayer.domain.audio

/**
 * Pure, Android-free decision helpers for the playback pipeline: how consecutive tracks transition
 * (gapless vs crossfade), how the crossfade fade combines with the ReplayGain base volume without
 * the two clobbering each other, which container/codec MIME types the host can play, and how big
 * the ExoPlayer buffers should be for the current network.
 *
 * All logic here is side-effect free and dependency free so it is unit-tested on the JVM
 * ([PlaybackTuningTest]). The [PlaybackService] only gathers Android state and applies the results.
 */

/**
 * How the player should join one track to the next.
 *
 * - [GAPLESS]: no gap and no overlap — decoder encoder-delay/padding is honoured so gapless-mastered
 *   albums play seamlessly. This is ExoPlayer's native behaviour when nothing forces a reset.
 * - [CROSSFADE]: the tail of the outgoing track is volume-faded down while the head of the incoming
 *   track is faded up (a track-boundary volume fade — an approximation of a true overlap crossfade).
 */
enum class TrackTransition {
    GAPLESS,
    CROSSFADE,
}

/**
 * The resolved transition policy for the current crossfade setting. Crossfade wins when it is
 * enabled (a positive duration); otherwise tracks join gaplessly. A fade and true gaplessness are
 * mutually exclusive, so exactly one [transition] is chosen.
 *
 * Note: gapless is ExoPlayer's native, always-on behaviour (it honours encoder delay/padding on
 * gapless-mastered material automatically), so there is no separate "enable gapless" flag to carry —
 * turning crossfade off IS the gapless path.
 */
data class TransitionPolicy(
    val transition: TrackTransition,
    /** The crossfade fade duration in milliseconds; 0 when not crossfading (the gapless case). */
    val crossfadeMs: Long,
)

/**
 * Which network the device is on, used to size buffers: cellular/metered links favour smaller
 * buffers (less wasted data if the user skips), unmetered Wi-Fi/Ethernet can buffer more for
 * smoother playback, and [UNKNOWN] takes a conservative middle ground.
 */
enum class NetworkType {
    UNMETERED,
    METERED,
    UNKNOWN,
}

/**
 * ExoPlayer `DefaultLoadControl` buffer sizing, in milliseconds. Values are chosen by
 * [PlaybackTuning.selectBuffer] from the network type. `minBufferMs <= maxBufferMs` and both
 * playback-start thresholds are `<= minBufferMs` always hold (asserted in tests) so the values are
 * safe to hand straight to `DefaultLoadControl.Builder`.
 */
data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
)

object PlaybackTuning {

    // --- Gapless vs crossfade ----------------------------------------------

    /**
     * Resolves the transition policy from [crossfadeSeconds] (0 = crossfade off). Crossfade when the
     * duration is positive; otherwise tracks join gaplessly. A negative duration is treated as off.
     */
    fun resolveTransition(crossfadeSeconds: Int): TransitionPolicy {
        val crossfadeMs = (crossfadeSeconds.coerceAtLeast(0)) * 1000L
        return if (crossfadeMs > 0L) {
            TransitionPolicy(transition = TrackTransition.CROSSFADE, crossfadeMs = crossfadeMs)
        } else {
            TransitionPolicy(transition = TrackTransition.GAPLESS, crossfadeMs = 0L)
        }
    }

    /**
     * The crossfade volume multiplier in `[0, 1]` for a track at [positionMs] of [durationMs], given
     * the [crossfadeMs] fade length: ramps up over the first [crossfadeMs] and down over the last
     * [crossfadeMs]. Returns 1.0 (no attenuation) when crossfade is off, playback is paused
     * ([isPlaying] false), the duration is unknown (`<= 0`), or the position sits outside both fade
     * windows.
     *
     * The [isPlaying] guard matches the original loop: a paused track is never attenuated, so pausing
     * inside a fade window (e.g. at track start or near the end) can't leave the volume stuck low.
     * The fade-in floor of 0.05 keeps a just-started, playing track audible instead of dead silent
     * for a frame.
     */
    fun crossfadeFactor(
        positionMs: Long,
        durationMs: Long,
        crossfadeMs: Long,
        isPlaying: Boolean = true,
    ): Float {
        if (crossfadeMs <= 0L || durationMs <= 0L || !isPlaying) return 1f
        val remaining = durationMs - positionMs
        return when {
            remaining in 1..crossfadeMs -> remaining.toFloat() / crossfadeMs
            positionMs in 0..crossfadeMs -> (positionMs.toFloat() / crossfadeMs).coerceAtLeast(0.05f)
            else -> 1f
        }
    }

    /**
     * Combines the ReplayGain base volume with the crossfade fade factor so the two volume owners do
     * not clobber each other: the effective player volume is `replayGainVolume * crossfadeFactor`.
     * When crossfade is off [crossfadeFactor] is 1.0 and the ReplayGain volume passes through
     * untouched. Result is clamped to `[0, 1]` (ExoPlayer's accepted range for `player.volume`).
     */
    fun effectiveVolume(replayGainVolume: Float, crossfadeFactor: Float): Float =
        (replayGainVolume * crossfadeFactor).coerceIn(0f, 1f)

    // --- Format support -----------------------------------------------------

    /**
     * Audio container/codec MIME types media3 1.10 can play out of the box on the codecs this app
     * ships (core extractors + platform decoders), plus the ClearKey-DASH/HLS adaptive paths. Kept
     * lowercase for case-insensitive matching in [isSupportedFormat].
     */
    private val SUPPORTED_MIME_TYPES: Set<String> = setOf(
        // Progressive audio
        "audio/mpeg",            // MP3
        "audio/mp4",             // AAC/ALAC in MP4
        "audio/mp4a-latm",       // AAC
        "audio/aac",
        "audio/flac",            // FLAC (native extractor + platform/FLAC decoder)
        "audio/ogg",             // Ogg container
        "audio/opus",            // Opus
        "audio/vorbis",          // Vorbis
        "audio/webm",            // WebM (Opus/Vorbis)
        "audio/wav",             // WAV / PCM
        "audio/x-wav",
        "audio/wave",
        "audio/raw",             // raw PCM (our PcmStreamDataSource path)
        "audio/3gpp",
        "audio/amr",
        "audio/amr-wb",
        // Adaptive manifests
        "application/dash+xml",  // DASH
        "application/x-mpegurl", // HLS
        "application/vnd.apple.mpegurl",
        "application/x-mpegURL".lowercase(),
    )

    /**
     * Whether the host can attempt playback of the given container/codec [mimeType]. Unknown or
     * blank MIME types return `true` (optimistic): many plugin streams arrive with no declared type
     * and ExoPlayer sniffs the container itself, so we only hard-reject types we positively know we
     * cannot decode. Matching is case-insensitive and tolerant of `; codecs=...` parameters.
     */
    fun isSupportedFormat(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true
        val base = mimeType.substringBefore(';').trim().lowercase()
        if (base.isEmpty()) return true
        if (base in SUPPORTED_MIME_TYPES) return true
        // Positively reject formats we know media3 cannot decode without extra native decoders.
        return base !in UNSUPPORTED_MIME_TYPES
    }

    /**
     * Formats media3 1.10 cannot decode with the codecs this app ships (no bundled native decoder).
     * These are hard-rejected by [isSupportedFormat] so the service can skip the item with a clear
     * error instead of stalling on an item that will never render.
     */
    private val UNSUPPORTED_MIME_TYPES: Set<String> = setOf(
        "audio/x-ape",        // Monkey's Audio (APE)
        "audio/ape",
        "audio/x-wavpack",    // WavPack
        "audio/wavpack",
        "audio/x-musepack",   // Musepack
        "audio/x-tta",        // True Audio
        "audio/dsd",          // DSD / DSF
        "audio/x-dsf",
        "audio/vnd.dolby.dd-raw", // raw AC-3 needs a passthrough-capable sink
    )

    // --- Audio focus / becoming-noisy policy -------------------------------

    /**
     * What playback should do in response to a system audio-focus change. ExoPlayer applies this
     * automatically when `handleAudioFocus` is enabled; the pure mapping is factored out here so the
     * intended policy is unit-tested and unambiguous.
     */
    enum class FocusReaction {
        /** Keep playing at full volume (focus gained, or nothing to react to). */
        PLAY,
        /** Lower the volume but keep playing (transient loss that may duck, e.g. a nav prompt). */
        DUCK,
        /** Pause playback, remembering to resume when focus returns (a transient loss). */
        PAUSE_TRANSIENT,
        /** Stop/abandon playback; do not auto-resume (permanent loss, e.g. another music app). */
        PAUSE_PERMANENT,
    }

    /**
     * Android `AudioManager.AUDIOFOCUS_*` change constant → the reaction playback should take.
     * Mirrors ExoPlayer's built-in focus handling so the behaviour is asserted in tests independent
     * of the framework.
     *
     * @param focusChange one of `AUDIOFOCUS_GAIN(1)`, `AUDIOFOCUS_LOSS(-1)`,
     *   `AUDIOFOCUS_LOSS_TRANSIENT(-2)`, `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK(-3)`.
     * @param willPauseWhenDucked when true, a duckable transient loss pauses instead of ducking
     *   (respecting a user preference to never lower volume). Defaults to ducking.
     */
    fun focusReaction(focusChange: Int, willPauseWhenDucked: Boolean = false): FocusReaction =
        when (focusChange) {
            1 -> FocusReaction.PLAY                    // AUDIOFOCUS_GAIN
            -1 -> FocusReaction.PAUSE_PERMANENT        // AUDIOFOCUS_LOSS
            -2 -> FocusReaction.PAUSE_TRANSIENT        // AUDIOFOCUS_LOSS_TRANSIENT
            -3 -> if (willPauseWhenDucked) FocusReaction.PAUSE_TRANSIENT else FocusReaction.DUCK
            else -> FocusReaction.PLAY
        }

    /**
     * Whether an audio-output change should pause playback ("becoming noisy"). Unplugging headphones
     * or disconnecting Bluetooth routes audio to the loud speaker, so playback must pause; ExoPlayer
     * does this when `handleAudioBecomingNoisy` is enabled. Returns true only while [isPlaying].
     */
    fun shouldPauseOnBecomingNoisy(isPlaying: Boolean): Boolean = isPlaying

    // --- Buffering / prefetch ----------------------------------------------

    /**
     * Picks a [BufferProfile] for the [network]. Cellular/metered links buffer less to avoid wasting
     * mobile data on content the user might skip; unmetered links buffer more for resilience to
     * jitter. Values keep ExoPlayer's invariants (`bufferForPlayback <= minBuffer <= maxBuffer`) so
     * the profile is safe to feed straight into `DefaultLoadControl.Builder`.
     */
    fun selectBuffer(network: NetworkType): BufferProfile = when (network) {
        NetworkType.UNMETERED -> BufferProfile(
            minBufferMs = 30_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 2_000,
            bufferForPlaybackAfterRebufferMs = 4_000,
        )
        NetworkType.METERED -> BufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 2_000,
            bufferForPlaybackAfterRebufferMs = 5_000,
        )
        NetworkType.UNKNOWN -> BufferProfile(
            minBufferMs = 20_000,
            maxBufferMs = 40_000,
            bufferForPlaybackMs = 2_000,
            bufferForPlaybackAfterRebufferMs = 5_000,
        )
    }
}
