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

interface SettingsRepository {
    // Appearance
    val dynamicTheme: Flow<Boolean>
    suspend fun setDynamicTheme(enabled: Boolean)
    
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
    
    val autoLoadMore: Flow<Boolean>
    suspend fun setAutoLoadMore(enabled: Boolean)
    
    // Content
    val showExplicitContent: Flow<Boolean>
    suspend fun setShowExplicitContent(enabled: Boolean)
    
    // Storage (settings only - cache operations are in CacheRepository)
    val maxSongCacheSize: Flow<Long> // In bytes
    suspend fun setMaxSongCacheSize(size: Long)
    
    val maxImageCacheSize: Flow<Long> // In bytes
    suspend fun setMaxImageCacheSize(size: Long)
}

