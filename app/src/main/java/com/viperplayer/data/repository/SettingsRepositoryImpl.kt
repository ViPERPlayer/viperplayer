package com.viperplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository {

    companion object {
        private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

        // Appearance
        private val DYNAMIC_THEME_MODE_KEY = stringPreferencesKey("dynamic_theme_mode")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val PURE_BLACK_KEY = booleanPreferencesKey("pure_black")

        // Player/Audio
        private val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")
        private val HISTORY_DURATION_KEY = stringPreferencesKey("history_duration")
        private val SKIP_SILENCE_KEY = booleanPreferencesKey("skip_silence")
        private val REPLAY_GAIN_ENABLED_KEY = booleanPreferencesKey("replay_gain_enabled")
        private val REPLAY_GAIN_PREAMP_DB_KEY = floatPreferencesKey("replay_gain_preamp_db")
        private val DSP_BYPASS_KEY = booleanPreferencesKey("dsp_bypass")
        private val AUTO_LOAD_MORE_KEY = booleanPreferencesKey("auto_load_more")

        // Content
        private val SHOW_EXPLICIT_CONTENT_KEY = booleanPreferencesKey("show_explicit_content")

        // Storage
        private val MAX_SONG_CACHE_SIZE_KEY = longPreferencesKey("max_song_cache_size")
        private val MAX_IMAGE_CACHE_SIZE_KEY = longPreferencesKey("max_image_cache_size")
    }

    private val dataStore = context.settingsDataStore

    // Appearance
    override val dynamicThemeMode: Flow<DynamicThemeMode> = dataStore.data.map { preferences ->
        preferences[DYNAMIC_THEME_MODE_KEY]?.let {
            try {
                DynamicThemeMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                DynamicThemeMode.DYNAMIC
            }
        } ?: DynamicThemeMode.DYNAMIC
    }

    override suspend fun setDynamicThemeMode(mode: DynamicThemeMode) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_THEME_MODE_KEY] = mode.name
        }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY]?.let {
            try {
                ThemeMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        } ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override val pureBlack: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PURE_BLACK_KEY] ?: false // Default to disabled
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PURE_BLACK_KEY] = enabled
        }
    }

    // Player/Audio
    override val audioQuality: Flow<AudioQuality> = dataStore.data.map { preferences ->
        preferences[AUDIO_QUALITY_KEY]?.let {
            try {
                AudioQuality.valueOf(it)
            } catch (e: IllegalArgumentException) {
                AudioQuality.HIGH
            }
        } ?: AudioQuality.HIGH
    }

    override suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { preferences ->
            preferences[AUDIO_QUALITY_KEY] = quality.name
        }
    }

    override val historyDuration: Flow<HistoryDuration> = dataStore.data.map { preferences ->
        preferences[HISTORY_DURATION_KEY]?.let {
            try {
                HistoryDuration.valueOf(it)
            } catch (e: IllegalArgumentException) {
                HistoryDuration.FOREVER
            }
        } ?: HistoryDuration.FOREVER
    }

    override suspend fun setHistoryDuration(duration: HistoryDuration) {
        dataStore.edit { preferences ->
            preferences[HISTORY_DURATION_KEY] = duration.name
        }
    }

    override val skipSilence: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SKIP_SILENCE_KEY] ?: false // Default to disabled
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SKIP_SILENCE_KEY] = enabled
        }
    }

    override val dspBypass: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DSP_BYPASS_KEY] ?: false // Default to disabled
    }

    override suspend fun setDspBypass(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DSP_BYPASS_KEY] = enabled
        }
    }

    override val replayGainEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[REPLAY_GAIN_ENABLED_KEY] ?: true
    }

    override suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_ENABLED_KEY] = enabled
        }
    }

    override val replayGainPreampDb: Flow<Float> = dataStore.data.map { preferences ->
        preferences[REPLAY_GAIN_PREAMP_DB_KEY] ?: 0f
    }

    override suspend fun setReplayGainPreampDb(preampDb: Float) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_PREAMP_DB_KEY] = preampDb
        }
    }

    override val autoLoadMore: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_LOAD_MORE_KEY] ?: false // Default to disabled
    }

    override suspend fun setAutoLoadMore(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_LOAD_MORE_KEY] = enabled
        }
    }

    // Content
    override val showExplicitContent: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_EXPLICIT_CONTENT_KEY] ?: true // Default to showing explicit content
    }

    override suspend fun setShowExplicitContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_EXPLICIT_CONTENT_KEY] = enabled
        }
    }

    // Storage (settings only - cache operations are in CacheRepository)
    override val maxSongCacheSize: Flow<Long> = dataStore.data.map { preferences ->
        preferences[MAX_SONG_CACHE_SIZE_KEY] ?: 500L * 1024 * 1024 // Default 500MB
    }

    override suspend fun setMaxSongCacheSize(size: Long) {
        dataStore.edit { preferences ->
            preferences[MAX_SONG_CACHE_SIZE_KEY] = size
        }
    }

    override val maxImageCacheSize: Flow<Long> = dataStore.data.map { preferences ->
        preferences[MAX_IMAGE_CACHE_SIZE_KEY] ?: 200L * 1024 * 1024 // Default 200MB
    }

    override suspend fun setMaxImageCacheSize(size: Long) {
        dataStore.edit { preferences ->
            preferences[MAX_IMAGE_CACHE_SIZE_KEY] = size
        }
    }
}
