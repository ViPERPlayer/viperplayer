package com.viperplayer.domain.repository

import kotlinx.coroutines.flow.Flow

enum class AudioQuality {
    LOW,    // 128 kbps
    MEDIUM, // 256 kbps
    HIGH,   // 320 kbps
    LOSSLESS // FLAC/ALAC
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class HistoryDuration {
    DAYS_7,
    DAYS_30,
    DAYS_90,
    DAYS_180,
    DAYS_365,
    FOREVER
}

enum class DynamicThemeMode {
    OFF,
    DYNAMIC,
    SYSTEM
}

/**
 * ReplayGain track/album gain selection.
 * - [TRACK]: always per-track gain.
 * - [ALBUM]: always per-album gain.
 * - [SMART]: album gain when playing a sequential album, track gain when shuffling/mixed queues.
 */
enum class ReplayGainMode {
    TRACK,
    ALBUM,
    SMART
}

/**
 * Derives the effective [ReplayGainMode] from persisted settings, preserving back-compat with the
 * legacy `replay_gain_album_mode` boolean while defaulting fresh installs to [ReplayGainMode.SMART].
 *
 * Pure and Android-free so it can be unit-tested directly.
 *
 * @param explicit the value of the new `replay_gain_mode` key, or `null` if it was never written.
 * @param legacyAlbumMode the value of the legacy `replay_gain_album_mode` key, or `null` if that key
 *   was never written (i.e. a fresh install that predates neither key).
 *
 * Precedence:
 * 1. [explicit] non-null → use it (the user picked a mode explicitly).
 * 2. else [legacyAlbumMode] non-null → honor the legacy toggle both ways: `true` → [ReplayGainMode.ALBUM],
 *    `false` → [ReplayGainMode.TRACK]. This keeps existing users on their prior behavior instead of
 *    silently flipping per-track normalization to SMART on upgrade.
 * 3. else (neither key ever written = fresh install) → [ReplayGainMode.SMART] (the new default).
 */
fun deriveReplayGainMode(explicit: ReplayGainMode?, legacyAlbumMode: Boolean?): ReplayGainMode =
    when {
        explicit != null -> explicit
        legacyAlbumMode != null -> if (legacyAlbumMode) ReplayGainMode.ALBUM else ReplayGainMode.TRACK
        else -> ReplayGainMode.SMART
    }

interface SettingsRepository {
    // Appearance
    val dynamicThemeMode: Flow<DynamicThemeMode>
    suspend fun setDynamicThemeMode(mode: DynamicThemeMode)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val pureBlack: Flow<Boolean>
    suspend fun setPureBlack(enabled: Boolean)

    // Player/Audio
    val audioQuality: Flow<AudioQuality>
    suspend fun setAudioQuality(quality: AudioQuality)

    val historyDuration: Flow<HistoryDuration>
    suspend fun setHistoryDuration(duration: HistoryDuration)

    val skipSilence: Flow<Boolean>
    suspend fun setSkipSilence(enabled: Boolean)

    val replayGainEnabled: Flow<Boolean>
    suspend fun setReplayGainEnabled(enabled: Boolean)

    val replayGainPreampDb: Flow<Float>
    suspend fun setReplayGainPreampDb(preampDb: Float)

    /** When ReplayGain is on, prefer album gain over track gain (preserves intra-album loudness). */
    val replayGainAlbumMode: Flow<Boolean>
    suspend fun setReplayGainAlbumMode(enabled: Boolean)

    /** ReplayGain track/album gain selection (track / album / smart). Supersedes [replayGainAlbumMode]. */
    val replayGainMode: Flow<ReplayGainMode>
    suspend fun setReplayGainMode(mode: ReplayGainMode)

    /** Preamp (dB) applied to tracks with NO ReplayGain tags, independent of the tagged preamp. */
    val replayGainUntaggedPreampDb: Flow<Float>
    suspend fun setReplayGainUntaggedPreampDb(preampDb: Float)

    /** Dynamic-range compression / clipping protection: limit gain so peak * gain never clips. */
    val replayGainDrcEnabled: Flow<Boolean>
    suspend fun setReplayGainDrcEnabled(enabled: Boolean)

    /** Global post-amp (dB) applied AFTER ReplayGain and DRC, to trim the overall level. */
    val replayGainPostAmpDb: Flow<Float>
    suspend fun setReplayGainPostAmpDb(postAmpDb: Float)

    /** Bypass all app-side DSP (the ViPER processor + ReplayGain) for a clean/untouched signal path. */
    val dspBypass: Flow<Boolean>
    suspend fun setDspBypass(enabled: Boolean)

    val autoLoadMore: Flow<Boolean>
    suspend fun setAutoLoadMore(enabled: Boolean)

    /** Crossfade duration in seconds (0 = off). Implemented as a track-change volume fade. */
    val crossfadeDurationSeconds: Flow<Int>
    suspend fun setCrossfadeDurationSeconds(seconds: Int)

    // Content
    val showExplicitContent: Flow<Boolean>
    suspend fun setShowExplicitContent(enabled: Boolean)

    // Storage (settings only - cache operations are in CacheRepository)
    val maxSongCacheSize: Flow<Long> // In bytes
    suspend fun setMaxSongCacheSize(size: Long)

    val maxImageCacheSize: Flow<Long> // In bytes
    suspend fun setMaxImageCacheSize(size: Long)
}

