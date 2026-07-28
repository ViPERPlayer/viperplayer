package com.viperplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsBehavior
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SEEK_INCREMENT_DEFAULT_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MAX_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MIN_SECONDS
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.SwipeSensitivity
import com.viperplayer.domain.repository.deriveReplayGainMode
import com.viperplayer.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    // Production entry point: Hilt injects the app Context and we open the file-backed "settings" store.
    // The primary constructor takes a DataStore directly so unit tests can inject an in-memory one
    // (PreferenceDataStoreFactory) and round-trip flags on the JVM without Android.
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

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
        private val REPLAY_GAIN_ALBUM_MODE_KEY = booleanPreferencesKey("replay_gain_album_mode")
        private val AUTO_LOAD_MORE_KEY = booleanPreferencesKey("auto_load_more")
        private val CROSSFADE_DURATION_SECONDS_KEY = intPreferencesKey("crossfade_duration_seconds")
        private val SKIP_ON_ERROR_KEY = booleanPreferencesKey("skip_on_error")
        private val SEEK_INCREMENT_SECONDS_KEY = intPreferencesKey("seek_increment_seconds")
        private val STOP_ON_TASK_REMOVED_KEY = booleanPreferencesKey("stop_on_task_removed")
        private val KEEP_SCREEN_ON_PLAYER_KEY = booleanPreferencesKey("keep_screen_on_player")
        private val SWIPE_TO_CHANGE_SONG_KEY = booleanPreferencesKey("swipe_to_change_song")
        private val SWIPE_SENSITIVITY_KEY = stringPreferencesKey("swipe_sensitivity")
        private val PAUSE_WHEN_MUTED_KEY = booleanPreferencesKey("pause_when_muted")
        private val RESUME_ON_BLUETOOTH_KEY = booleanPreferencesKey("resume_on_bluetooth")
        private val SLEEP_TIMER_FADE_OUT_KEY = booleanPreferencesKey("sleep_timer_fade_out")
        private val PREVENT_DUPLICATE_QUEUE_KEY = booleanPreferencesKey("prevent_duplicate_queue")
        private val PERSISTENT_QUEUE_ENABLED_KEY = booleanPreferencesKey("persistent_queue_enabled")
        private val BLOCK_SCREENSHOTS_KEY = booleanPreferencesKey("block_screenshots")
        private val REPLAY_GAIN_MODE_KEY = stringPreferencesKey("replay_gain_mode")
        private val REPLAY_GAIN_UNTAGGED_PREAMP_DB_KEY = floatPreferencesKey("replay_gain_untagged_preamp_db")
        private val REPLAY_GAIN_DRC_ENABLED_KEY = booleanPreferencesKey("replay_gain_drc_enabled")
        private val REPLAY_GAIN_POST_AMP_DB_KEY = floatPreferencesKey("replay_gain_post_amp_db")

        // Content
        private val SHOW_EXPLICIT_CONTENT_KEY = booleanPreferencesKey("show_explicit_content")
        private val OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
        private val AUTO_DOWNLOAD_ON_LIKE_KEY = booleanPreferencesKey("auto_download_on_like")

        // Storage
        private val MAX_SONG_CACHE_SIZE_KEY = longPreferencesKey("max_song_cache_size")
        private val MAX_IMAGE_CACHE_SIZE_KEY = longPreferencesKey("max_image_cache_size")

        // Lyrics
        private val LYRICS_FONT_SIZE_KEY = stringPreferencesKey("lyrics_font_size")
        private val LYRICS_ALIGNMENT_KEY = stringPreferencesKey("lyrics_alignment")
        private val LYRICS_FONT_WEIGHT_KEY = stringPreferencesKey("lyrics_font_weight")
        private val LYRICS_HIGHLIGHT_COLOR_KEY = stringPreferencesKey("lyrics_highlight_color")
        private val LYRICS_ACTIVE_LINE_SCALE_KEY = floatPreferencesKey("lyrics_active_line_scale")
        private val LYRICS_AUTO_SCROLL_KEY = booleanPreferencesKey("lyrics_auto_scroll")
        private val LYRICS_TAP_TO_SEEK_KEY = booleanPreferencesKey("lyrics_tap_to_seek")
        private val LYRICS_DIM_INACTIVE_KEY = booleanPreferencesKey("lyrics_dim_inactive")
        private val LYRICS_SHOW_TRANSLATION_KEY = booleanPreferencesKey("lyrics_show_translation")
        private val LYRICS_SHOW_ROMANIZATION_KEY = booleanPreferencesKey("lyrics_show_romanization")

        // Sort order — one option + direction key per view (keyed by the view's enum name).
        private fun sortOptionKey(view: SortView) = stringPreferencesKey("sort_option_${view.name}")
        private fun sortDirectionKey(view: SortView) = stringPreferencesKey("sort_direction_${view.name}")

        // Library tabs config (order + visibility), serialized by LibraryTabsConfig.
        private val LIBRARY_TABS_CONFIG_KEY = stringPreferencesKey("library_tabs_config")

        // Custom accent/seed color (packed ARGB int), absent when unset. Appended at the end.
        private val ACCENT_COLOR_KEY = intPreferencesKey("accent_color")

        // Social (backend-gated UI)
        private val HOME_SIGN_IN_CARD_DISMISSED_KEY = booleanPreferencesKey("home_sign_in_card_dismissed")

        // Recommendations (on-device, opt-in)
        private val RECOMMENDATIONS_ENABLED_KEY = booleanPreferencesKey("recommendations_enabled")
    }

    /** map + distinctUntilChanged — DataStore emits the whole snapshot on any edit, so de-dupe each setting. */
    private fun <T> Flow<Preferences>.mapDistinct(transform: suspend (Preferences) -> T): Flow<T> =
        map(transform).distinctUntilChanged()

    // Appearance
    override val dynamicThemeMode: Flow<DynamicThemeMode> = dataStore.data.mapDistinct { preferences ->
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

    override val themeMode: Flow<ThemeMode> = dataStore.data.mapDistinct { preferences ->
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

    override val pureBlack: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[PURE_BLACK_KEY] ?: false // Default to disabled
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PURE_BLACK_KEY] = enabled
        }
    }

    override val accentColor: Flow<Int?> = dataStore.data.mapDistinct { preferences ->
        preferences[ACCENT_COLOR_KEY] // null when unset → use the brand default
    }

    override suspend fun setAccentColor(argb: Int?) {
        dataStore.edit { preferences ->
            if (argb == null) {
                preferences.remove(ACCENT_COLOR_KEY)
            } else {
                preferences[ACCENT_COLOR_KEY] = argb
            }
        }
    }

    // Player/Audio
    override val audioQuality: Flow<AudioQuality> = dataStore.data.mapDistinct { preferences ->
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

    override val historyDuration: Flow<HistoryDuration> = dataStore.data.mapDistinct { preferences ->
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

    override val skipSilence: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[SKIP_SILENCE_KEY] ?: false // Default to disabled
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SKIP_SILENCE_KEY] = enabled
        }
    }

    override val dspBypass: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[DSP_BYPASS_KEY] ?: false // Default to disabled
    }

    override suspend fun setDspBypass(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DSP_BYPASS_KEY] = enabled
        }
    }

    override val replayGainAlbumMode: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_ALBUM_MODE_KEY] ?: false // Default to track gain
    }

    override suspend fun setReplayGainAlbumMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_ALBUM_MODE_KEY] = enabled
        }
    }

    override val replayGainEnabled: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_ENABLED_KEY] ?: true
    }

    override suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_ENABLED_KEY] = enabled
        }
    }

    override val replayGainPreampDb: Flow<Float> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_PREAMP_DB_KEY] ?: 0f
    }

    override suspend fun setReplayGainPreampDb(preampDb: Float) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_PREAMP_DB_KEY] = preampDb
        }
    }

    override val replayGainMode: Flow<ReplayGainMode> = dataStore.data.mapDistinct { preferences ->
        // The new mode key (may be absent on upgrade); an unparseable value is treated as unset.
        val explicit = preferences[REPLAY_GAIN_MODE_KEY]?.let {
            try {
                ReplayGainMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        // Raw legacy toggle: null when the key was never written (distinguishes fresh install from
        // an existing user who left album mode off — the latter must stay on per-track, not flip to SMART).
        val legacyAlbumMode = preferences[REPLAY_GAIN_ALBUM_MODE_KEY]
        deriveReplayGainMode(explicit, legacyAlbumMode)
    }

    override suspend fun setReplayGainMode(mode: ReplayGainMode) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_MODE_KEY] = mode.name
        }
    }

    override val replayGainUntaggedPreampDb: Flow<Float> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_UNTAGGED_PREAMP_DB_KEY] ?: 0f
    }

    override suspend fun setReplayGainUntaggedPreampDb(preampDb: Float) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_UNTAGGED_PREAMP_DB_KEY] = preampDb
        }
    }

    override val replayGainDrcEnabled: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_DRC_ENABLED_KEY] ?: false
    }

    override suspend fun setReplayGainDrcEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_DRC_ENABLED_KEY] = enabled
        }
    }

    override val replayGainPostAmpDb: Flow<Float> = dataStore.data.mapDistinct { preferences ->
        preferences[REPLAY_GAIN_POST_AMP_DB_KEY] ?: 0f
    }

    override suspend fun setReplayGainPostAmpDb(postAmpDb: Float) {
        dataStore.edit { preferences ->
            preferences[REPLAY_GAIN_POST_AMP_DB_KEY] = postAmpDb
        }
    }

    override val autoLoadMore: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[AUTO_LOAD_MORE_KEY] ?: false // Default to disabled
    }

    override suspend fun setAutoLoadMore(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_LOAD_MORE_KEY] = enabled
        }
    }

    override val crossfadeDurationSeconds: Flow<Int> = dataStore.data.mapDistinct { preferences ->
        preferences[CROSSFADE_DURATION_SECONDS_KEY] ?: 0 // Default 0 = off
    }

    override suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[CROSSFADE_DURATION_SECONDS_KEY] = seconds
        }
    }

    override val skipOnError: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[SKIP_ON_ERROR_KEY] ?: true // Default to enabled: don't stall on a broken track
    }

    override suspend fun setSkipOnError(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SKIP_ON_ERROR_KEY] = enabled
        }
    }

    override val seekIncrementSeconds: Flow<Int> = dataStore.data.mapDistinct { preferences ->
        (preferences[SEEK_INCREMENT_SECONDS_KEY] ?: SEEK_INCREMENT_DEFAULT_SECONDS)
            .coerceIn(SEEK_INCREMENT_MIN_SECONDS, SEEK_INCREMENT_MAX_SECONDS)
    }

    override suspend fun setSeekIncrementSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[SEEK_INCREMENT_SECONDS_KEY] =
                seconds.coerceIn(SEEK_INCREMENT_MIN_SECONDS, SEEK_INCREMENT_MAX_SECONDS)
        }
    }

    override val stopOnTaskRemoved: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[STOP_ON_TASK_REMOVED_KEY] ?: false // Default to disabled: keep playing after swipe-away
    }

    override suspend fun setStopOnTaskRemoved(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[STOP_ON_TASK_REMOVED_KEY] = enabled
        }
    }

    override val keepScreenOnPlayer: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[KEEP_SCREEN_ON_PLAYER_KEY] ?: false // Default to disabled
    }

    override suspend fun setKeepScreenOnPlayer(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON_PLAYER_KEY] = enabled
        }
    }

    override val swipeToChangeSong: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[SWIPE_TO_CHANGE_SONG_KEY] ?: true // Default to enabled
    }

    override suspend fun setSwipeToChangeSong(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SWIPE_TO_CHANGE_SONG_KEY] = enabled
        }
    }

    override val swipeSensitivity: Flow<SwipeSensitivity> = dataStore.data.mapDistinct { preferences ->
        preferences[SWIPE_SENSITIVITY_KEY]?.let {
            try {
                SwipeSensitivity.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SwipeSensitivity.MEDIUM
            }
        } ?: SwipeSensitivity.MEDIUM
    }

    override suspend fun setSwipeSensitivity(sensitivity: SwipeSensitivity) {
        dataStore.edit { preferences ->
            preferences[SWIPE_SENSITIVITY_KEY] = sensitivity.name
        }
    }

    override val pauseWhenMuted: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[PAUSE_WHEN_MUTED_KEY] ?: false // Default to disabled
    }

    override suspend fun setPauseWhenMuted(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PAUSE_WHEN_MUTED_KEY] = enabled
        }
    }

    override val resumeOnBluetooth: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[RESUME_ON_BLUETOOTH_KEY] ?: false // Default to disabled
    }

    override suspend fun setResumeOnBluetooth(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RESUME_ON_BLUETOOTH_KEY] = enabled
        }
    }

    override val sleepTimerFadeOut: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[SLEEP_TIMER_FADE_OUT_KEY] ?: false // Default to disabled
    }

    override suspend fun setSleepTimerFadeOut(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SLEEP_TIMER_FADE_OUT_KEY] = enabled
        }
    }

    override val preventDuplicateQueue: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[PREVENT_DUPLICATE_QUEUE_KEY] ?: false // Default to disabled: allow duplicates
    }

    override suspend fun setPreventDuplicateQueue(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PREVENT_DUPLICATE_QUEUE_KEY] = enabled
        }
    }

    override val persistentQueueEnabled: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[PERSISTENT_QUEUE_ENABLED_KEY] ?: true // Default to enabled: restore last queue on cold start
    }

    override suspend fun setPersistentQueueEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PERSISTENT_QUEUE_ENABLED_KEY] = enabled
        }
    }

    override val blockScreenshots: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[BLOCK_SCREENSHOTS_KEY] ?: false // Default to disabled
    }

    override suspend fun setBlockScreenshots(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLOCK_SCREENSHOTS_KEY] = enabled
        }
    }

    // Content
    override val showExplicitContent: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[SHOW_EXPLICIT_CONTENT_KEY] ?: true // Default to showing explicit content
    }

    override suspend fun setShowExplicitContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_EXPLICIT_CONTENT_KEY] = enabled
        }
    }

    override val offlineMode: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[OFFLINE_MODE_KEY] ?: false // Default to disabled: remote fetches allowed
    }

    override suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[OFFLINE_MODE_KEY] = enabled
        }
    }

    override val autoDownloadOnLike: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[AUTO_DOWNLOAD_ON_LIKE_KEY] ?: false // Default to disabled
    }

    override suspend fun setAutoDownloadOnLike(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_DOWNLOAD_ON_LIKE_KEY] = enabled
        }
    }

    // Storage (settings only - cache operations are in CacheRepository)
    override val maxSongCacheSize: Flow<Long> = dataStore.data.mapDistinct { preferences ->
        preferences[MAX_SONG_CACHE_SIZE_KEY] ?: 500L * 1024 * 1024 // Default 500MB
    }

    override suspend fun setMaxSongCacheSize(size: Long) {
        dataStore.edit { preferences ->
            preferences[MAX_SONG_CACHE_SIZE_KEY] = size
        }
    }

    override val maxImageCacheSize: Flow<Long> = dataStore.data.mapDistinct { preferences ->
        preferences[MAX_IMAGE_CACHE_SIZE_KEY] ?: 200L * 1024 * 1024 // Default 200MB
    }

    override suspend fun setMaxImageCacheSize(size: Long) {
        dataStore.edit { preferences ->
            preferences[MAX_IMAGE_CACHE_SIZE_KEY] = size
        }
    }

    // Lyrics (style + behavior)
    private val defaultLyricsSettings = LyricsSettings()

    override val lyricsFontSize: Flow<LyricsFontSize> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_FONT_SIZE_KEY].toEnum(defaultLyricsSettings.fontSize)
    }

    override suspend fun setLyricsFontSize(size: LyricsFontSize) {
        dataStore.edit { it[LYRICS_FONT_SIZE_KEY] = size.name }
    }

    override val lyricsAlignment: Flow<LyricsAlignment> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_ALIGNMENT_KEY].toEnum(defaultLyricsSettings.alignment)
    }

    override suspend fun setLyricsAlignment(alignment: LyricsAlignment) {
        dataStore.edit { it[LYRICS_ALIGNMENT_KEY] = alignment.name }
    }

    override val lyricsFontWeight: Flow<LyricsFontWeight> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_FONT_WEIGHT_KEY].toEnum(defaultLyricsSettings.fontWeight)
    }

    override suspend fun setLyricsFontWeight(weight: LyricsFontWeight) {
        dataStore.edit { it[LYRICS_FONT_WEIGHT_KEY] = weight.name }
    }

    override val lyricsHighlightColor: Flow<LyricsHighlightColor> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_HIGHLIGHT_COLOR_KEY].toEnum(defaultLyricsSettings.highlightColor)
    }

    override suspend fun setLyricsHighlightColor(color: LyricsHighlightColor) {
        dataStore.edit { it[LYRICS_HIGHLIGHT_COLOR_KEY] = color.name }
    }

    override val lyricsActiveLineScale: Flow<Float> = dataStore.data.mapDistinct { preferences ->
        LyricsBehavior.clampActiveLineScale(
            preferences[LYRICS_ACTIVE_LINE_SCALE_KEY] ?: defaultLyricsSettings.activeLineScale
        )
    }

    override suspend fun setLyricsActiveLineScale(scale: Float) {
        dataStore.edit { it[LYRICS_ACTIVE_LINE_SCALE_KEY] = LyricsBehavior.clampActiveLineScale(scale) }
    }

    override val lyricsAutoScroll: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_AUTO_SCROLL_KEY] ?: defaultLyricsSettings.autoScroll
    }

    override suspend fun setLyricsAutoScroll(enabled: Boolean) {
        dataStore.edit { it[LYRICS_AUTO_SCROLL_KEY] = enabled }
    }

    override val lyricsTapToSeek: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_TAP_TO_SEEK_KEY] ?: defaultLyricsSettings.tapToSeek
    }

    override suspend fun setLyricsTapToSeek(enabled: Boolean) {
        dataStore.edit { it[LYRICS_TAP_TO_SEEK_KEY] = enabled }
    }

    override val lyricsDimInactiveLines: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_DIM_INACTIVE_KEY] ?: defaultLyricsSettings.dimInactiveLines
    }

    override suspend fun setLyricsDimInactiveLines(enabled: Boolean) {
        dataStore.edit { it[LYRICS_DIM_INACTIVE_KEY] = enabled }
    }

    override val lyricsShowTranslationByDefault: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_SHOW_TRANSLATION_KEY] ?: defaultLyricsSettings.showTranslationByDefault
    }

    override suspend fun setLyricsShowTranslationByDefault(enabled: Boolean) {
        dataStore.edit { it[LYRICS_SHOW_TRANSLATION_KEY] = enabled }
    }

    override val lyricsShowRomanizationByDefault: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[LYRICS_SHOW_ROMANIZATION_KEY] ?: defaultLyricsSettings.showRomanizationByDefault
    }

    override suspend fun setLyricsShowRomanizationByDefault(enabled: Boolean) {
        dataStore.edit { it[LYRICS_SHOW_ROMANIZATION_KEY] = enabled }
    }

    override val lyricsSettings: Flow<LyricsSettings> = dataStore.data.mapDistinct { preferences ->
        LyricsSettings(
            fontSize = preferences[LYRICS_FONT_SIZE_KEY].toEnum(defaultLyricsSettings.fontSize),
            alignment = preferences[LYRICS_ALIGNMENT_KEY].toEnum(defaultLyricsSettings.alignment),
            fontWeight = preferences[LYRICS_FONT_WEIGHT_KEY].toEnum(defaultLyricsSettings.fontWeight),
            highlightColor = preferences[LYRICS_HIGHLIGHT_COLOR_KEY].toEnum(defaultLyricsSettings.highlightColor),
            activeLineScale = LyricsBehavior.clampActiveLineScale(
                preferences[LYRICS_ACTIVE_LINE_SCALE_KEY] ?: defaultLyricsSettings.activeLineScale
            ),
            autoScroll = preferences[LYRICS_AUTO_SCROLL_KEY] ?: defaultLyricsSettings.autoScroll,
            tapToSeek = preferences[LYRICS_TAP_TO_SEEK_KEY] ?: defaultLyricsSettings.tapToSeek,
            dimInactiveLines = preferences[LYRICS_DIM_INACTIVE_KEY] ?: defaultLyricsSettings.dimInactiveLines,
            showTranslationByDefault = preferences[LYRICS_SHOW_TRANSLATION_KEY]
                ?: defaultLyricsSettings.showTranslationByDefault,
            showRomanizationByDefault = preferences[LYRICS_SHOW_ROMANIZATION_KEY]
                ?: defaultLyricsSettings.showRomanizationByDefault,
        )
    }

    // Sort order per view — persisted as two string keys (option + direction), decoded leniently so a
    // stale/unknown name falls back to the passthrough default rather than crashing.
    override fun sortOrder(view: SortView): Flow<SortOrder> = dataStore.data.mapDistinct { preferences ->
        val option = preferences[sortOptionKey(view)]?.let {
            try {
                SortOption.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SortOption.DEFAULT
            }
        } ?: SortOption.DEFAULT
        val direction = preferences[sortDirectionKey(view)]?.let {
            try {
                SortDirection.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SortDirection.ASCENDING
            }
        } ?: SortDirection.ASCENDING
        SortOrder(option, direction)
    }

    override suspend fun setSortOrder(view: SortView, order: SortOrder) {
        dataStore.edit { preferences ->
            preferences[sortOptionKey(view)] = order.option.name
            preferences[sortDirectionKey(view)] = order.direction.name
        }
    }

    // Library tabs config — stored as the compact string LibraryTabsConfig.serialize() produces, decoded
    // leniently (blank/corrupt → EMPTY, i.e. the app falls back to the default all-visible tab set).
    override val libraryTabsConfig: Flow<LibraryTabsConfig> = dataStore.data.mapDistinct { preferences ->
        LibraryTabsConfig.deserialize(preferences[LIBRARY_TABS_CONFIG_KEY])
    }

    override suspend fun setLibraryTabsConfig(config: LibraryTabsConfig) {
        dataStore.edit { preferences ->
            preferences[LIBRARY_TABS_CONFIG_KEY] = config.serialize()
        }
    }

    // Social (backend-gated UI)
    override val homeSignInCardDismissed: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[HOME_SIGN_IN_CARD_DISMISSED_KEY] ?: false // Default: card not dismissed
    }

    override suspend fun setHomeSignInCardDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[HOME_SIGN_IN_CARD_DISMISSED_KEY] = dismissed
        }
    }

    // Recommendations (on-device, opt-in) — default OFF.
    override val recommendationsEnabled: Flow<Boolean> = dataStore.data.mapDistinct { preferences ->
        preferences[RECOMMENDATIONS_ENABLED_KEY] ?: false // Default: recommendations off (opt-in)
    }

    override suspend fun setRecommendationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RECOMMENDATIONS_ENABLED_KEY] = enabled
        }
    }

    /** Parse a stored enum name, falling back to [default] on null/unknown values. */
    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T {
        if (this == null) return default
        return try {
            enumValueOf<T>(this)
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
