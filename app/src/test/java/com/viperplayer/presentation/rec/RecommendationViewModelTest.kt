package com.viperplayer.presentation.rec

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.RecommendationRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.presentation.navigation.SimilarSongs
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the recommendation ViewModels' state mapping (loading → local / fallback / empty /
 * indexing) via the shared [applyResult] fold and the two ViewModels over fake repositories.
 */
class RecommendationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun song(id: String) = Song(id = MediaId.Local(id), title = id)
    private val seed = SimilarSongs(seedMediaId = MediaId.Local("seed"), seedTitle = "Seed")

    @Test
    fun similar_localResult_mapsToSongsNoFallback() = runTest {
        val vm = SimilarSongsViewModel(seed, FakeRec(RecResult.Local(listOf(song("a"), song("b")))), RecordingPlayer())
        advanceUntilIdle()

        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertFalse(s.usingFallback)
        assertNull(s.emptyReason)
        assertEquals(listOf("a", "b"), s.songs.map { it.title })
    }

    @Test
    fun similar_fallbackResult_setsFallbackFlag() = runTest {
        val vm = SimilarSongsViewModel(seed, FakeRec(RecResult.Fallback(listOf(song("r")))), RecordingPlayer())
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.usingFallback)
        assertEquals(listOf("r"), s.songs.map { it.title })
        assertNull(s.emptyReason)
    }

    @Test
    fun similar_emptyResult_setsReasonAndNoSongs() = runTest {
        val vm = SimilarSongsViewModel(seed, FakeRec(RecResult.Empty(RecEmptyReason.NO_RESULTS)), RecordingPlayer())
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.songs.isEmpty())
        assertEquals(RecEmptyReason.NO_RESULTS, s.emptyReason)
    }

    @Test
    fun similar_notIndexedEmpty_surfacesIndexingProgress() = runTest {
        val readiness = RecReadiness(
            recommendationsEnabled = true,
            modelReady = true,
            indexedCount = 0,
            indexingProcessed = 12,
            indexingTotal = 40,
        )
        val vm = SimilarSongsViewModel(
            seed,
            FakeRec(RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET), readiness),
            RecordingPlayer(),
        )
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(RecEmptyReason.NOT_INDEXED_YET, s.emptyReason)
        assertTrue(s.isIndexing)
        assertEquals(12, s.indexingProcessed)
        assertEquals(40, s.indexingTotal)
    }

    @Test
    fun similar_playAll_usesSuggestionsContext() = runTest {
        val player = RecordingPlayer()
        val vm = SimilarSongsViewModel(seed, FakeRec(RecResult.Local(listOf(song("a"), song("b")))), player)
        advanceUntilIdle()

        vm.playAll()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), player.playedAll?.map { it.title })
        assertEquals(0, player.startIndex)
        assertTrue(player.context is PlaybackContext.Suggestions)
    }

    @Test
    fun forYou_localResult_maps() = runTest {
        val vm = ForYouViewModel(FakeRec(RecResult.Local(listOf(song("x")))), RecordingPlayer())
        advanceUntilIdle()

        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertEquals(listOf("x"), s.songs.map { it.title })
    }

    @Test
    fun forYou_disabledEmpty_maps() = runTest {
        val vm = ForYouViewModel(FakeRec(RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED)), RecordingPlayer())
        advanceUntilIdle()

        assertEquals(RecEmptyReason.RECOMMENDATIONS_DISABLED, vm.uiState.value.emptyReason)
    }

    /** Serves a fixed result (+ readiness) for both `moreLikeThis` and `forYouFromLibrary`. */
    private class FakeRec(
        private val result: RecResult,
        private val readinessValue: RecReadiness = RecReadiness(true, true, 0),
    ) : RecommendationRepository {
        override suspend fun moreLikeThis(seed: MediaId, limit: Int): RecResult = result
        override suspend fun forYouFromLibrary(limit: Int): RecResult = result
        override val readiness: Flow<RecReadiness> get() = flowOf(readinessValue)
    }

    private class RecordingPlayer : PlayerRepository by UnsupportedPlayerRepository() {
        var playedAll: List<Song>? = null
        var startIndex: Int = -1
        var context: PlaybackContext? = null

        override val playbackState: StateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())
        override val currentSong: StateFlow<Song?> = MutableStateFlow(null)

        override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
            playedAll = songs
            this.startIndex = startIndex
            this.context = context
        }
    }
}
