package com.viperplayer.domain.repository

import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
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

    // Library / detail list sort order, persisted independently per [SortView].
    /** The persisted [SortOrder] for [view]. Defaults to [SortOrder.DEFAULT] (original order). */
    fun sortOrder(view: SortView): Flow<SortOrder>
    suspend fun setSortOrder(view: SortView, order: SortOrder)
}

