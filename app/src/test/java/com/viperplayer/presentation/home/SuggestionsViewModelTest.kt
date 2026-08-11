package com.viperplayer.presentation.home

import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.domain.rec.ClapModelState
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.RecommendationRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the "Suggested for you" surface: the pure [mapToState] fold and the
 * [SuggestionsViewModel]'s state derivation (OFF → enable, ON-not-ready → personalizing with progress,
 * ON-ready-results → carousel, ON-ready-empty → hidden) plus the inline-enable and play paths, all over
 * fakes (no Android).
 */
class SuggestionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun song(id: String) = Song(id = MediaId.Local(id), title = id)
    private fun readiness(
        enabled: Boolean = true,
        modelReady: Boolean = true,
        indexedCount: Int = 5,
        processed: Int? = null,
        total: Int? = null,
    ) = RecReadiness(enabled, modelReady, indexedCount, processed, total)

    // ---- Pure mapToState fold -------------------------------------------------------------------

    @Test
    fun map_localWithSongs_isReadyWithReasons() {
        val reasons: Map<MediaId, RecReason> =
            mapOf(MediaId.Local("a") to RecReason(RecReason.Kind.SOUNDS_LIKE, "Anchor"))
        val state = mapToState(RecResult.Local(listOf(song("a")), reasons), readiness())
        assertTrue(state is SuggestionsUiState.Ready)
        state as SuggestionsUiState.Ready
        assertEquals(listOf("a"), state.songs.map { it.title })
        assertEquals(reasons, state.reasons)
    }

    @Test
    fun map_fallbackWithSongs_isReady() {
        val state = mapToState(RecResult.Fallback(listOf(song("r"))), readiness())
        assertTrue(state is SuggestionsUiState.Ready)
    }

    @Test
    fun map_emptyLocal_isHidden() {
        val state = mapToState(RecResult.Local(emptyList()), readiness())
        assertEquals(SuggestionsUiState.Hidden, state)
    }

    @Test
    fun map_disabledEmpty_isDisabled() {
        val state = mapToState(RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED), readiness(enabled = false))
        assertEquals(SuggestionsUiState.Disabled, state)
    }

    @Test
    fun map_notIndexedEmpty_isPersonalizingWithProgress() {
        val state = mapToState(
            RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET),
            readiness(modelReady = true, indexedCount = 0, processed = 3, total = 20),
        )
        assertTrue(state is SuggestionsUiState.Personalizing)
        state as SuggestionsUiState.Personalizing
        assertTrue(state.hasProgress)
        assertEquals(3, state.processed)
        assertEquals(20, state.total)
    }

    @Test
    fun map_notIndexedEmpty_noProgress_isPersonalizingGeneric() {
        val state = mapToState(
            RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET),
            readiness(modelReady = false, indexedCount = 0),
        )
        assertTrue(state is SuggestionsUiState.Personalizing)
        assertTrue(!(state as SuggestionsUiState.Personalizing).hasProgress)
    }

    @Test
    fun map_noResultsEmpty_isHidden() {
        assertEquals(SuggestionsUiState.Hidden, mapToState(RecResult.Empty(RecEmptyReason.NO_RESULTS), readiness()))
    }

    @Test
    fun map_errorEmpty_isHidden() {
        assertEquals(SuggestionsUiState.Hidden, mapToState(RecResult.Empty(RecEmptyReason.ERROR), readiness()))
    }

    // ---- End-to-end ViewModel derivation --------------------------------------------------------

    @Test
    fun vm_recommendationsOff_showsEnableCard() = runTest {
        val vm = vm(FakeRec(RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED), readiness(enabled = false)))
        advanceUntilIdle()
        assertEquals(SuggestionsUiState.Disabled, vm.uiState.value)
    }

    @Test
    fun vm_onNotReady_showsPersonalizing() = runTest {
        val vm = vm(
            FakeRec(
                RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET),
                readiness(modelReady = true, indexedCount = 0, processed = 7, total = 30),
            )
        )
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is SuggestionsUiState.Personalizing)
        assertEquals(7, (s as SuggestionsUiState.Personalizing).processed)
        assertEquals(30, s.total)
    }

    @Test
    fun vm_onReadyWithResults_showsCarousel() = runTest {
        val vm = vm(FakeRec(RecResult.Local(listOf(song("x"), song("y"))), readiness()))
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is SuggestionsUiState.Ready)
        assertEquals(listOf("x", "y"), (s as SuggestionsUiState.Ready).songs.map { it.title })
    }

    @Test
    fun vm_onReadyButEmpty_isHidden() = runTest {
        val vm = vm(FakeRec(RecResult.Empty(RecEmptyReason.NO_RESULTS), readiness()))
        advanceUntilIdle()
        assertEquals(SuggestionsUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun vm_dismiss_hidesEnableCard() = runTest {
        val vm = vm(FakeRec(RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED), readiness(enabled = false)))
        advanceUntilIdle()
        assertEquals(SuggestionsUiState.Disabled, vm.uiState.value)

        vm.dismiss()
        advanceUntilIdle()
        assertEquals(SuggestionsUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun vm_enable_persistsOptInAndStartsDownload() = runTest {
        val settings = FakeSettings()
        val clap = FakeClap()
        val vm = vm(
            FakeRec(RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED), readiness(enabled = false)),
            settings = settings,
            clap = clap,
        )
        advanceUntilIdle()

        vm.enable()
        advanceUntilIdle()

        assertEquals(true, settings.enabledValue.value)
        assertTrue(clap.ensureDownloadedCalls > 0)
    }

    @Test
    fun vm_play_usesSuggestionsContextAndSeedsQueue() = runTest {
        val player = RecordingPlayer()
        val vm = vm(FakeRec(RecResult.Local(listOf(song("a"), song("b"))), readiness()), player = player)
        advanceUntilIdle()

        vm.play(song("b"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), player.playedAll?.map { it.title })
        assertEquals(1, player.startIndex)
        assertTrue(player.context is PlaybackContext.Suggestions)
    }

    // ---- Fakes ----------------------------------------------------------------------------------

    private fun vm(
        rec: RecommendationRepository,
        settings: SettingsRepository = FakeSettings(),
        clap: ClapModelRepository = FakeClap(),
        player: PlayerRepository = RecordingPlayer(),
    ) = SuggestionsViewModel(rec, settings, clap, player)

    /** Serves a fixed result + readiness for `forYouFromLibrary`/`readiness`. */
    private class FakeRec(
        private val result: RecResult,
        readinessValue: RecReadiness,
    ) : RecommendationRepository {
        private val readinessFlow = MutableStateFlow(readinessValue)
        override suspend fun moreLikeThis(seed: MediaId, limit: Int): RecResult = result
        override suspend fun forYouFromLibrary(limit: Int): RecResult = result
        override val readiness: Flow<RecReadiness> get() = readinessFlow
        override suspend fun sendFeedback(mediaId: MediaId, positive: Boolean) {}
    }

    private class FakeSettings : SettingsRepository by UnsupportedSettingsRepository() {
        val enabledValue = MutableStateFlow(false)
        override val recommendationsEnabled: Flow<Boolean> get() = enabledValue
        override suspend fun setRecommendationsEnabled(enabled: Boolean) { enabledValue.value = enabled }
    }

    private class FakeClap : ClapModelRepository {
        var ensureDownloadedCalls = 0
        override val modelState: Flow<ClapModelState> = flowOf(ClapModelState.Absent)
        override suspend fun installedVersion(): String? = null
        override suspend fun installedFile(): java.io.File? = null
        override suspend fun isInstalledStale(): Boolean = false
        override fun enqueueDownload() {}
        override suspend fun ensureDownloaded() { ensureDownloadedCalls++ }
        override fun cancel() {}
        override suspend fun deleteModel() {}
    }

    private class RecordingPlayer : PlayerRepository by UnsupportedPlayerRepository() {
        var playedAll: List<Song>? = null
        var startIndex: Int = -1
        var context: PlaybackContext? = null

        override val playbackState: StateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())
        override val currentSong: StateFlow<Song?> = MutableStateFlow(null)

        override suspend fun play(song: Song, context: PlaybackContext?) {
            playedAll = listOf(song)
            startIndex = 0
            this.context = context
        }

        override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
            playedAll = songs
            this.startIndex = startIndex
            this.context = context
        }
    }
}
