package com.viperplayer.presentation.library

import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [SettingsRepository] fake for library-tab unit tests. Only [libraryTabsConfig] /
 * [setLibraryTabsConfig] are meaningful; the rest error if touched (tests must not depend on them).
 *
 * [setLibraryTabsConfig] optionally delays before re-emitting to model DataStore's write→re-emit
 * latency — the window a lost-update bug would hide in. The exposed [libraryTabsConfig] is a
 * [MutableStateFlow] seeded from [initialConfig].
 */
class FakeSettingsRepository(
    initialConfig: LibraryTabsConfig = LibraryTabsConfig.EMPTY,
    private val writeReEmitDelayMs: Long = 0,
) : SettingsRepository {

    private val _libraryTabsConfig = MutableStateFlow(initialConfig)
    override val libraryTabsConfig: Flow<LibraryTabsConfig> = _libraryTabsConfig.asStateFlow()

    /** The number of persisted writes, so a test can assert how many round-trips actually happened. */
    var writeCount: Int = 0
        private set

    override suspend fun setLibraryTabsConfig(config: LibraryTabsConfig) {
        writeCount++
        if (writeReEmitDelayMs > 0) delay(writeReEmitDelayMs)
        _libraryTabsConfig.value = config
    }

    // --- Everything below is unused by the library-tab tests. ---
    override fun sortOrder(view: SortView): Flow<SortOrder> = flowOf(SortOrder.DEFAULT)
    override suspend fun setSortOrder(view: SortView, order: SortOrder) = unused()
    override val dynamicThemeMode: Flow<DynamicThemeMode> get() = unused()
    override suspend fun setDynamicThemeMode(mode: DynamicThemeMode) = unused()
    override val themeMode: Flow<ThemeMode> get() = unused()
    override suspend fun setThemeMode(mode: ThemeMode) = unused()
    override val pureBlack: Flow<Boolean> get() = unused()
    override suspend fun setPureBlack(enabled: Boolean) = unused()
    override val accentColor: Flow<Int?> get() = unused()
    override suspend fun setAccentColor(argb: Int?) = unused()
    override val audioQuality: Flow<AudioQuality> get() = unused()
    override suspend fun setAudioQuality(quality: AudioQuality) = unused()
    override val historyDuration: Flow<HistoryDuration> get() = unused()
    override suspend fun setHistoryDuration(duration: HistoryDuration) = unused()
    override val skipSilence: Flow<Boolean> get() = unused()
    override suspend fun setSkipSilence(enabled: Boolean) = unused()
    override val replayGainEnabled: Flow<Boolean> get() = unused()
    override suspend fun setReplayGainEnabled(enabled: Boolean) = unused()
    override val replayGainPreampDb: Flow<Float> get() = unused()
    override suspend fun setReplayGainPreampDb(preampDb: Float) = unused()
    override val replayGainAlbumMode: Flow<Boolean> get() = unused()
    override suspend fun setReplayGainAlbumMode(enabled: Boolean) = unused()
    override val replayGainMode: Flow<ReplayGainMode> get() = unused()
    override suspend fun setReplayGainMode(mode: ReplayGainMode) = unused()
    override val replayGainUntaggedPreampDb: Flow<Float> get() = unused()
    override suspend fun setReplayGainUntaggedPreampDb(preampDb: Float) = unused()
    override val replayGainDrcEnabled: Flow<Boolean> get() = unused()
    override suspend fun setReplayGainDrcEnabled(enabled: Boolean) = unused()
    override val replayGainPostAmpDb: Flow<Float> get() = unused()
    override suspend fun setReplayGainPostAmpDb(postAmpDb: Float) = unused()
    override val dspBypass: Flow<Boolean> get() = unused()
    override suspend fun setDspBypass(enabled: Boolean) = unused()
    override val autoLoadMore: Flow<Boolean> get() = unused()
    override suspend fun setAutoLoadMore(enabled: Boolean) = unused()
    override val homeSignInCardDismissed: Flow<Boolean> get() = unused()
    override suspend fun setHomeSignInCardDismissed(dismissed: Boolean) = unused()
    override val crossfadeDurationSeconds: Flow<Int> get() = unused()
    override suspend fun setCrossfadeDurationSeconds(seconds: Int) = unused()
    override val showExplicitContent: Flow<Boolean> get() = unused()
    override suspend fun setShowExplicitContent(enabled: Boolean) = unused()
    override val maxSongCacheSize: Flow<Long> get() = unused()
    override suspend fun setMaxSongCacheSize(size: Long) = unused()
    override val maxImageCacheSize: Flow<Long> get() = unused()
    override suspend fun setMaxImageCacheSize(size: Long) = unused()
    override val lyricsFontSize: Flow<LyricsFontSize> get() = unused()
    override suspend fun setLyricsFontSize(size: LyricsFontSize) = unused()
    override val lyricsAlignment: Flow<LyricsAlignment> get() = unused()
    override suspend fun setLyricsAlignment(alignment: LyricsAlignment) = unused()
    override val lyricsFontWeight: Flow<LyricsFontWeight> get() = unused()
    override suspend fun setLyricsFontWeight(weight: LyricsFontWeight) = unused()
    override val lyricsHighlightColor: Flow<LyricsHighlightColor> get() = unused()
    override suspend fun setLyricsHighlightColor(color: LyricsHighlightColor) = unused()
    override val lyricsActiveLineScale: Flow<Float> get() = unused()
    override suspend fun setLyricsActiveLineScale(scale: Float) = unused()
    override val lyricsAutoScroll: Flow<Boolean> get() = unused()
    override suspend fun setLyricsAutoScroll(enabled: Boolean) = unused()
    override val lyricsTapToSeek: Flow<Boolean> get() = unused()
    override suspend fun setLyricsTapToSeek(enabled: Boolean) = unused()
    override val lyricsDimInactiveLines: Flow<Boolean> get() = unused()
    override suspend fun setLyricsDimInactiveLines(enabled: Boolean) = unused()
    override val lyricsShowTranslationByDefault: Flow<Boolean> get() = unused()
    override suspend fun setLyricsShowTranslationByDefault(enabled: Boolean) = unused()
    override val lyricsShowRomanizationByDefault: Flow<Boolean> get() = unused()
    override suspend fun setLyricsShowRomanizationByDefault(enabled: Boolean) = unused()
    override val lyricsSettings: Flow<LyricsSettings> get() = unused()

    private fun unused(): Nothing = error("not used in library-tab tests")
}
