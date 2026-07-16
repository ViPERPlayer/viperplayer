package com.viperplayer.domain.audio

import kotlin.math.min
import kotlin.math.pow

/**
 * How ReplayGain picks between per-track and per-album gain.
 *
 * - [TRACK]: always use per-track gain/peak.
 * - [ALBUM]: always use per-album gain/peak (preserves intra-album loudness relationships).
 * - [SMART]: use album gain when the queue is playing an album in order (sequential, not shuffled),
 *   track gain otherwise. See [ReplayGainCalculator.pickSmartSource].
 */
enum class ReplayGainMode {
    TRACK,
    ALBUM,
    SMART,
}

/**
 * The ReplayGain tags carried by a single track. All values are optional because a track may be
 * fully tagged, partially tagged (e.g. only track gain), or completely untagged.
 *
 * Gains are in dB (a track's tagged loudness offset relative to the reference level). Peaks are
 * linear sample amplitudes in `(0, 1]`, where `1.0` means the track already reaches full scale.
 */
data class ReplayGainTags(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    /** True when neither a track nor an album gain is present — the track is untagged. */
    val isUntagged: Boolean
        get() = trackGainDb == null && albumGainDb == null
}

/**
 * User-configurable ReplayGain settings, all independent of Android.
 *
 * @param enabled master toggle; when false the calculator returns unity (1.0).
 * @param mode track / album / smart gain selection.
 * @param preampDb preamp applied to *tagged* tracks, on top of their RG gain.
 * @param untaggedPreampDb preamp applied to *untagged* tracks (which have no RG gain of their own),
 *   letting the user pull untagged material up or down independently of tagged material.
 * @param drcEnabled dynamic-range / clipping protection. When on, the final gain is limited so the
 *   track's peak never exceeds full scale (`peak * gain <= 1.0`), preventing boost-induced clipping.
 * @param postAmpDb a global gain applied *after* RG (and after DRC limiting), so the user can bring
 *   the overall level back up or down.
 */
data class ReplayGainSettings(
    val enabled: Boolean = true,
    val mode: ReplayGainMode = ReplayGainMode.TRACK,
    val preampDb: Float = 0f,
    val untaggedPreampDb: Float = 0f,
    val drcEnabled: Boolean = false,
    val postAmpDb: Float = 0f,
)

/**
 * The chosen (gain, peak) pair for a track after resolving [ReplayGainMode] against its tags.
 * [gainDb] is null when the track carries no usable gain for the selected mode (untagged path).
 */
data class ResolvedReplayGain(
    val gainDb: Float?,
    val peak: Float?,
)

/**
 * Pure, Android-free ReplayGain gain computation. Given a track's tags, the user's settings, and a
 * flag describing whether the queue is currently playing a sequential album, it computes the final
 * linear volume multiplier to hand to the audio pipeline (ExoPlayer's `player.volume`).
 *
 * Kept side-effect free and dependency free so it can be unit-tested on the JVM.
 */
object ReplayGainCalculator {

    /** Result is always clamped into this range (ExoPlayer accepts `[0, 1]` for `player.volume`). */
    private const val MIN_VOLUME = 0f
    private const val MAX_VOLUME = 1f

    /** Converts a dB gain to a linear amplitude multiplier. 0 dB maps exactly to 1.0. */
    fun dbToLinear(db: Float): Float = if (db == 0f) 1f else 10f.pow(db / 20f)

    /**
     * Resolves which (gain, peak) to use for the given [mode], falling back gracefully when only one
     * of album/track is tagged.
     *
     * - [ReplayGainMode.TRACK]: prefer track values, fall back to album values.
     * - [ReplayGainMode.ALBUM]: prefer album values, fall back to track values.
     * - [ReplayGainMode.SMART]: album when [sequentialAlbum] is true, else track — each with the
     *   other as a fallback (see [pickSmartSource]).
     */
    fun resolve(
        tags: ReplayGainTags,
        mode: ReplayGainMode,
        sequentialAlbum: Boolean,
    ): ResolvedReplayGain {
        val preferAlbum = when (mode) {
            ReplayGainMode.TRACK -> false
            ReplayGainMode.ALBUM -> true
            ReplayGainMode.SMART -> pickSmartSource(sequentialAlbum)
        }
        val gainDb = if (preferAlbum) {
            tags.albumGainDb ?: tags.trackGainDb
        } else {
            tags.trackGainDb ?: tags.albumGainDb
        }
        val peak = if (preferAlbum) {
            tags.albumPeak ?: tags.trackPeak
        } else {
            tags.trackPeak ?: tags.albumPeak
        }
        return ResolvedReplayGain(gainDb = gainDb, peak = peak)
    }

    /**
     * Smart-mode decision: use album gain when the queue is a sequential album (playing in order,
     * not shuffled), otherwise use track gain. Isolated for direct testing.
     */
    fun pickSmartSource(sequentialAlbum: Boolean): Boolean = sequentialAlbum

    /**
     * Computes the final linear volume multiplier for a track.
     *
     * Order of operations:
     * 1. If disabled → unity.
     * 2. Resolve gain/peak per [ReplayGainSettings.mode] and [sequentialAlbum].
     * 3. Base gain (dB): the tagged gain + tagged preamp, OR — when the track is untagged — just the
     *    untagged preamp (there is no RG gain to add it to).
     * 4. Convert to linear.
     * 5. DRC: if enabled and a peak is known, limit the gain so `peak * gain <= 1.0` (clip guard).
     * 6. Post-amp: multiply by the linear post-amp gain.
     * 7. Clamp to `[0, 1]`.
     */
    fun computeVolume(
        tags: ReplayGainTags,
        settings: ReplayGainSettings,
        sequentialAlbum: Boolean = false,
    ): Float {
        if (!settings.enabled) return MAX_VOLUME

        val resolved = resolve(tags, settings.mode, sequentialAlbum)
        val gainDb = resolved.gainDb

        // Base gain before post-amp. Untagged tracks get the untagged preamp only; tagged tracks get
        // their RG gain plus the tagged preamp.
        val baseGainDb = if (gainDb != null) gainDb + settings.preampDb else settings.untaggedPreampDb
        var gain = dbToLinear(baseGainDb)

        // DRC / clip protection: never let peak * gain exceed full scale. Only applies when a peak is
        // known; peaks must be positive to be meaningful. This mirrors Rockbox-style "prevent
        // clipping" and provides the clipping-prevention floor of DRC without native DSP.
        val peak = resolved.peak
        if (settings.drcEnabled && peak != null && peak > 0f) {
            gain = min(gain, MAX_VOLUME / peak)
        }

        // Post-amp: applied after RG and DRC so the user can trim the overall level.
        gain *= dbToLinear(settings.postAmpDb)

        return gain.coerceIn(MIN_VOLUME, MAX_VOLUME)
    }
}
