package com.viperplayer.domain.repository

import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
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

/**
 * How easily an artwork swipe on the player commits a track change. Maps to the pager fling's
 * [snapPositionalThresholdFor] — the fraction of a page the user must drag past before it settles
 * onto the next page. [LOW] needs a larger swipe; [HIGH] flips on a small one.
 */
enum class SwipeSensitivity {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * The positional snap threshold (0f..1f) for the player's artwork pager at [sensitivity]: the
 * fraction of a page's width the drag must pass before the pager settles onto the adjacent page.
 * A higher threshold means the user must swipe further, i.e. lower sensitivity.
 *
 * Pure and Android-free so it can be unit-tested directly. Compose's default is 0.5f, which we use
 * for [SwipeSensitivity.MEDIUM].
 */
fun snapPositionalThresholdFor(sensitivity: SwipeSensitivity): Float = when (sensitivity) {
    SwipeSensitivity.LOW -> 0.65f
    SwipeSensitivity.MEDIUM -> 0.5f
    SwipeSensitivity.HIGH -> 0.3f
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

/** Seek-increment bounds and default (seconds), shared by the repository, ViewModel, and the slider. */
const val SEEK_INCREMENT_MIN_SECONDS = 5
const val SEEK_INCREMENT_MAX_SECONDS = 60
const val SEEK_INCREMENT_DEFAULT_SECONDS = 10

interface SettingsRepository {
    // Appearance
    val dynamicThemeMode: Flow<DynamicThemeMode>
    suspend fun setDynamicThemeMode(mode: DynamicThemeMode)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val pureBlack: Flow<Boolean>
    suspend fun setPureBlack(enabled: Boolean)

    /**
     * User-picked accent/seed color as a packed ARGB [Int], or `null` when unset (use the app's
     * brand default). Only takes effect when dynamic color is OFF; on Android 12+ Material You and
     * color-from-album-art take precedence when their modes are selected.
     */
    val accentColor: Flow<Int?>
    suspend fun setAccentColor(argb: Int?)

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

    /** On a playback error, skip to the next queue item instead of stalling on the broken one. Default on. */
    val skipOnError: Flow<Boolean>
    suspend fun setSkipOnError(enabled: Boolean)

    /**
     * Forward/back seek increment in seconds (5..60). Read once when the player is built
     * ([SEEK_INCREMENT_MIN_SECONDS]..[SEEK_INCREMENT_MAX_SECONDS]); a change takes effect on the next
     * service start. Default [SEEK_INCREMENT_DEFAULT_SECONDS].
     */
    val seekIncrementSeconds: Flow<Int>
    suspend fun setSeekIncrementSeconds(seconds: Int)

    /** Stop playback and the media service when the app is swiped from Recents. Default off. */
    val stopOnTaskRemoved: Flow<Boolean>
    suspend fun setStopOnTaskRemoved(enabled: Boolean)

    /** Keep the screen awake while the full-screen player is expanded. Default off. */
    val keepScreenOnPlayer: Flow<Boolean>
    suspend fun setKeepScreenOnPlayer(enabled: Boolean)

    /**
     * Whether swiping the full-screen player's artwork changes the track. When off, the artwork pager
     * still shows the queue but a swipe never commits a track change. Default on.
     */
    val swipeToChangeSong: Flow<Boolean>
    suspend fun setSwipeToChangeSong(enabled: Boolean)

    /**
     * How easily an artwork swipe commits a track change (tunes the pager's snap threshold).
     * Only meaningful when [swipeToChangeSong] is on. Default [SwipeSensitivity.MEDIUM].
     */
    val swipeSensitivity: Flow<SwipeSensitivity>
    suspend fun setSwipeSensitivity(sensitivity: SwipeSensitivity)

    /** Pause playback when the media (music) stream volume drops to zero. Default off. */
    val pauseWhenMuted: Flow<Boolean>
    suspend fun setPauseWhenMuted(enabled: Boolean)

    /** Resume paused playback when a Bluetooth audio device connects. Default off. */
    val resumeOnBluetooth: Flow<Boolean>
    suspend fun setResumeOnBluetooth(enabled: Boolean)

    /** Fade the volume out over the last few seconds before the sleep timer pauses playback. Default off. */
    val sleepTimerFadeOut: Flow<Boolean>
    suspend fun setSleepTimerFadeOut(enabled: Boolean)

    /** Skip adding a track to the queue when the same MediaId is already queued. Default off. */
    val preventDuplicateQueue: Flow<Boolean>
    suspend fun setPreventDuplicateQueue(enabled: Boolean)

    /**
     * Restore the last playback queue (all tracks, in order, at the last-played track and position,
     * paused) when the app is reopened after being fully closed. When off, cold start begins with an
     * empty player and the queue is not persisted. Default on.
     */
    val persistentQueueEnabled: Flow<Boolean>
    suspend fun setPersistentQueueEnabled(enabled: Boolean)

    /** Block screenshots / screen recording by setting FLAG_SECURE on the window. Default off. */
    val blockScreenshots: Flow<Boolean>
    suspend fun setBlockScreenshots(enabled: Boolean)

    // Content
    val showExplicitContent: Flow<Boolean>
    suspend fun setShowExplicitContent(enabled: Boolean)

    /**
     * Randomize the order of Home feed sections, with stable per-session weights (the order only
     * re-rolls on an explicit Home refresh). When off, sections keep the order the plugins provide.
     * Default ON. A "randomize home order" setting.
     */
    val randomizeHomeOrder: Flow<Boolean>
    suspend fun setRandomizeHomeOrder(enabled: Boolean)

    /**
     * Offline mode: when on, remote plugin fetches (browse/search/home/stream resolution) are gated at
     * the plugin boundary so the app degrades gracefully to on-device (local + downloaded) content
     * instead of hitting the network. Does not suppress background queues (scrobble/library-sync outbox).
     * Default off.
     */
    val offlineMode: Flow<Boolean>
    suspend fun setOfflineMode(enabled: Boolean)

    /**
     * Auto-download a track when it is liked, if its source is downloadable (not the local plugin and
     * not already downloaded). Un-liking never deletes the download. Default off.
     */
    val autoDownloadOnLike: Flow<Boolean>
    suspend fun setAutoDownloadOnLike(enabled: Boolean)

    // Storage (settings only - cache operations are in CacheRepository)
    val maxSongCacheSize: Flow<Long> // In bytes
    suspend fun setMaxSongCacheSize(size: Long)

    val maxImageCacheSize: Flow<Long> // In bytes
    suspend fun setMaxImageCacheSize(size: Long)

    // Lyrics (style + behavior) — appended; defaults preserve prior rendering.
    val lyricsFontSize: Flow<LyricsFontSize>
    suspend fun setLyricsFontSize(size: LyricsFontSize)

    val lyricsAlignment: Flow<LyricsAlignment>
    suspend fun setLyricsAlignment(alignment: LyricsAlignment)

    val lyricsFontWeight: Flow<LyricsFontWeight>
    suspend fun setLyricsFontWeight(weight: LyricsFontWeight)

    val lyricsHighlightColor: Flow<LyricsHighlightColor>
    suspend fun setLyricsHighlightColor(color: LyricsHighlightColor)

    /** Font-size multiplier applied to the active line (1.0 = no scale-up). */
    val lyricsActiveLineScale: Flow<Float>
    suspend fun setLyricsActiveLineScale(scale: Float)

    val lyricsAutoScroll: Flow<Boolean>
    suspend fun setLyricsAutoScroll(enabled: Boolean)

    val lyricsTapToSeek: Flow<Boolean>
    suspend fun setLyricsTapToSeek(enabled: Boolean)

    val lyricsDimInactiveLines: Flow<Boolean>
    suspend fun setLyricsDimInactiveLines(enabled: Boolean)

    val lyricsShowTranslationByDefault: Flow<Boolean>
    suspend fun setLyricsShowTranslationByDefault(enabled: Boolean)

    val lyricsShowRomanizationByDefault: Flow<Boolean>
    suspend fun setLyricsShowRomanizationByDefault(enabled: Boolean)

    /** All lyrics settings combined into one snapshot for the renderer/ViewModel. */
    val lyricsSettings: Flow<LyricsSettings>

    // Library / detail list sort order, persisted independently per [SortView].
    /** The persisted [SortOrder] for [view]. Defaults to [SortOrder.DEFAULT] (original order). */
    fun sortOrder(view: SortView): Flow<SortOrder>
    suspend fun setSortOrder(view: SortView, order: SortOrder)

    /**
     * The user's library-tabs configuration (order + per-tab visibility). Defaults to
     * [LibraryTabsConfig.EMPTY] when never customized; the Library reconciles it against the tabs the
     * app currently ships via [resolveVisibleTabIds], so an empty/absent config means "all tabs, default
     * order, all visible".
     */
    val libraryTabsConfig: Flow<LibraryTabsConfig>
    suspend fun setLibraryTabsConfig(config: LibraryTabsConfig)

    // Social (backend-gated UI)
    /**
     * Whether the user has dismissed the Home "sign in" promo card. Persists across process restarts so
     * a dismissed card stays hidden. Defaults to `false` (not dismissed).
     */
    val homeSignInCardDismissed: Flow<Boolean>
    suspend fun setHomeSignInCardDismissed(dismissed: Boolean)

    // Recommendations (on-device, opt-in)
    /**
     * Whether audio-based smart recommendations are enabled. **Default OFF** — it downloads a large
     * on-device model (Wi-Fi only) and analyzes the user's library, so it must be opted into. Enabling
     * it triggers the CLAP model download; disabling cancels/removes it. Persists across restarts.
     */
    val recommendationsEnabled: Flow<Boolean>
    suspend fun setRecommendationsEnabled(enabled: Boolean)

    /**
     * Whether to contribute the user's **anonymized** on-device taste vector to the backend to improve
     * global discovery (P3, opt-in). **Default OFF** — only an L2-normalized taste vector is ever sent
     * (never audio, never identity, never track ids); nothing is shared unless this is explicitly turned
     * on. Persists across restarts.
     */
    val contributeAnonymizedTaste: Flow<Boolean>
    suspend fun setContributeAnonymizedTaste(enabled: Boolean)
}

