package com.viperplayer.presentation.detail

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import com.viperplayer.presentation.navigation.GenreDetail
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GenreDetailViewModel] over fake repositories: the genre's songs load from the library
 * repository and are re-sorted per the persisted order, and the play / shuffle / playSong actions drive
 * the player with a [PlaybackContext.Genre]. Follows the fakes + [StandardTestDispatcher] pattern of the
 * other detail-VM tests.
 */
class GenreDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val genreId = 7L
    private val songB = Song(id = MediaId.Plugin("local", "b"), title = "Beta")
    private val songA = Song(id = MediaId.Plugin("local", "a"), title = "Alpha")

    private fun viewModel(
        media: MediaLibraryRepository,
        player: PlayerRepository,
        settings: SettingsRepository = InertSettingsRepository(),
    ) = GenreDetailViewModel(
        genreDetail = GenreDetail(genreId = genreId, initialName = "Rock"),
        mediaLibraryRepository = media,
        playerRepository = player,
        settingsRepository = settings,
    )

    @Test
    fun load_exposesGenreSongs_sortedByPersistedOrder() = runTest {
        // Repo returns [Beta, Alpha]; the persisted TITLE order should re-sort them to [Alpha, Beta].
        val media = FakeMediaLibraryRepository(songsForGenre = listOf(songB, songA))
        val settings = InertSettingsRepository(
            order = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)
        )
        val vm = viewModel(media, RecordingPlayerRepository(), settings)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Rock", state.name)
        assertEquals(false, state.isLoading)
        assertEquals(listOf("Alpha", "Beta"), state.songs.map { it.title })
    }

    @Test
    fun playAll_playsGenreSongs_withGenreContext() = runTest {
        val media = FakeMediaLibraryRepository(songsForGenre = listOf(songA, songB))
        val player = RecordingPlayerRepository()
        val vm = viewModel(media, player)
        advanceUntilIdle()

        vm.playAll()
        advanceUntilIdle()

        assertEquals(listOf(songA.id, songB.id), player.playedAll?.map { it.id })
        assertEquals(0, player.startIndex)
        val context = player.context as? PlaybackContext.Genre
        assertTrue("context must be a Genre", context != null)
        assertEquals(genreId, context?.genreId)
        assertEquals("Rock", context?.name)
    }

    @Test
    fun playSong_playsWholeGenre_startingAtTappedSong() = runTest {
        val media = FakeMediaLibraryRepository(songsForGenre = listOf(songA, songB))
        val player = RecordingPlayerRepository()
        val vm = viewModel(media, player)
        advanceUntilIdle()

        vm.playSong(songB)
        advanceUntilIdle()

        assertEquals(listOf(songA.id, songB.id), player.playedAll?.map { it.id })
        assertEquals("starts at the tapped song's index", 1, player.startIndex)
    }

    @Test
    fun shuffle_playsAllGenreSongs() = runTest {
        val media = FakeMediaLibraryRepository(songsForGenre = listOf(songA, songB))
        val player = RecordingPlayerRepository()
        val vm = viewModel(media, player)
        advanceUntilIdle()

        vm.shuffle()
        advanceUntilIdle()

        assertEquals(setOf(songA.id, songB.id), player.playedAll?.map { it.id }?.toSet())
        assertEquals(0, player.startIndex)
    }

    /** Serves a fixed genre song list through the reactive [getSongsForGenre] flow. */
    private class FakeMediaLibraryRepository(
        private val songsForGenre: List<Song>,
    ) : MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        override fun getSongsForGenre(genreId: Long): Flow<List<Song>> = flowOf(songsForGenre)
    }

    /** Records the last playAll invocation (songs, index, context) for assertion. */
    private class RecordingPlayerRepository : PlayerRepository by UnsupportedPlayerRepository() {
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

        override suspend fun play(song: Song, context: PlaybackContext?) {
            playedAll = listOf(song)
            startIndex = 0
            this.context = context
        }
    }

    private class InertSettingsRepository(
        private val order: SortOrder = SortOrder.DEFAULT,
    ) : SettingsRepository by UnsupportedSettingsRepository() {
        override fun sortOrder(view: SortView): Flow<SortOrder> = flowOf(order)
        override suspend fun setSortOrder(view: SortView, order: SortOrder) = Unit
    }
}
