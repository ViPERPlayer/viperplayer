package com.viperplayer.presentation.library

import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import com.viperplayer.domain.sort.MediaSorter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the library cold-start sort seed (issue #3).
 *
 * The Library screen persists a sort order per tab. On a cold start the content flow can emit before
 * DataStore's first emission, which — without a seed — would render the list in DEFAULT order and then
 * flip to the persisted order one frame later. [seedLibrarySortOrders] prefetches the persisted orders
 * so the first Success state is already correctly ordered. These tests verify the seed reflects the
 * persisted (possibly non-Default) orders and that applying it to raw items yields the persisted
 * ordering on the FIRST pass (no DEFAULT-then-flip).
 */
class LibrarySortSeedTest {

    private var nextId = 0
    private fun song(title: String): Song =
        Song(id = MediaId.Plugin("test", "song${nextId++}"), title = title)

    /** A [SettingsRepository] fake that only implements the per-view sort order; the rest is unused. */
    private class FakeSettingsRepository(
        private val orders: Map<SortView, SortOrder>,
    ) : SettingsRepository {
        override fun sortOrder(view: SortView): Flow<SortOrder> =
            flowOf(orders[view] ?: SortOrder.DEFAULT)

        override suspend fun setSortOrder(view: SortView, order: SortOrder) = Unit

        // --- Unused by these tests ---
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
        override val homeSignInCardDismissed: Flow<Boolean> get() = unused()
        override suspend fun setHomeSignInCardDismissed(dismissed: Boolean) = unused()
        override suspend fun setAutoLoadMore(enabled: Boolean) = unused()
        override val crossfadeDurationSeconds: Flow<Int> get() = unused()
        override suspend fun setCrossfadeDurationSeconds(seconds: Int) = unused()
        override val skipOnError: Flow<Boolean> get() = unused()
        override suspend fun setSkipOnError(enabled: Boolean) = unused()
        override val seekIncrementSeconds: Flow<Int> get() = unused()
        override suspend fun setSeekIncrementSeconds(seconds: Int) = unused()
        override val stopOnTaskRemoved: Flow<Boolean> get() = unused()
        override suspend fun setStopOnTaskRemoved(enabled: Boolean) = unused()
        override val keepScreenOnPlayer: Flow<Boolean> get() = unused()
        override suspend fun setKeepScreenOnPlayer(enabled: Boolean) = unused()
        override val pauseWhenMuted: Flow<Boolean> get() = unused()
        override suspend fun setPauseWhenMuted(enabled: Boolean) = unused()
        override val resumeOnBluetooth: Flow<Boolean> get() = unused()
        override suspend fun setResumeOnBluetooth(enabled: Boolean) = unused()
        override val sleepTimerFadeOut: Flow<Boolean> get() = unused()
        override suspend fun setSleepTimerFadeOut(enabled: Boolean) = unused()
        override val preventDuplicateQueue: Flow<Boolean> get() = unused()
        override suspend fun setPreventDuplicateQueue(enabled: Boolean) = unused()
        override val persistentQueueEnabled: Flow<Boolean> get() = unused()
        override suspend fun setPersistentQueueEnabled(enabled: Boolean) = unused()
        override val blockScreenshots: Flow<Boolean> get() = unused()
        override suspend fun setBlockScreenshots(enabled: Boolean) = unused()
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
        override val libraryTabsConfig: Flow<LibraryTabsConfig> get() = unused()
        override suspend fun setLibraryTabsConfig(config: LibraryTabsConfig) = unused()

        private fun unused(): Nothing = error("not used in LibrarySortSeedTest")
    }

    @Test
    fun seed_reflectsPersistedNonDefaultOrderPerView() = runTest {
        val titleDesc = SortOrder(SortOption.TITLE, SortDirection.DESCENDING)
        val titleAsc = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)
        val repo = FakeSettingsRepository(
            mapOf(
                SortView.LIBRARY_SONGS to titleDesc,
                SortView.LIBRARY_ALBUMS to titleAsc,
                // ARTISTS + PLAYLISTS left unset → DEFAULT.
            )
        )

        val seed = seedLibrarySortOrders(repo)

        assertEquals(titleDesc, seed.songs)
        assertEquals(titleAsc, seed.albums)
        assertEquals(SortOrder.DEFAULT, seed.artists)
        assertEquals(SortOrder.DEFAULT, seed.playlists)
    }

    @Test
    fun firstFrame_appliesPersistedOrder_noDefaultThenFlip() = runTest {
        // Songs persisted as TITLE ascending. The first sorted frame must already be alphabetical,
        // NOT the raw load order (which would be the DEFAULT passthrough).
        val repo = FakeSettingsRepository(
            mapOf(SortView.LIBRARY_SONGS to SortOrder(SortOption.TITLE, SortDirection.ASCENDING))
        )
        val rawSongs = listOf(song("Zebra"), song("Alpha"), song("Mango"))

        // Cold-start sequence the ViewModel follows: seed first, then sort the first loaded list with it.
        val seed = seedLibrarySortOrders(repo)
        val firstFrame = MediaSorter.sortSongs(rawSongs, seed.songs).map { it.title }

        // Already sorted A→Z on the first frame — proves no DEFAULT-order flash.
        assertEquals(listOf("Alpha", "Mango", "Zebra"), firstFrame)
        // And it differs from the raw/DEFAULT order, so the assertion is meaningful.
        assertEquals(listOf("Zebra", "Alpha", "Mango"), rawSongs.map { it.title })
    }
}
