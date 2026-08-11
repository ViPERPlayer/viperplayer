package com.viperplayer.presentation.detail

import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedPluginRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import com.viperplayer.presentation.navigation.AlbumDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * The album screen's job beyond fetching: keep the visible track order and the playback order in
 * agreement. Tapping track 3 of a list the user has re-sorted must start at track 3 *as shown*, not
 * at whatever sits third in the plugin's original order — so most of these tests are about the
 * relationship between `sortedSongs` and what reaches the player.
 */
class AlbumDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val albumId = MediaId.Plugin("testsource", "album-1")

    // Deliberately not in alphabetical order, so a sort is observable.
    private val zebra = Song(id = MediaId.Plugin("testsource", "3"), title = "Zebra", trackNumber = 1)
    private val alpha = Song(id = MediaId.Plugin("testsource", "1"), title = "Alpha", trackNumber = 2)
    private val mango = Song(id = MediaId.Plugin("testsource", "2"), title = "Mango", trackNumber = 3)

    private val album = Album(
        id = albumId,
        name = "Test Album",
        songs = listOf(zebra, alpha, mango),
    )

    private class FakePlayer : UnsupportedPlayerRepository() {
        val played = mutableListOf<Pair<List<Song>, Int>>()
        val playedSingle = mutableListOf<Song>()
        val queuedNext = mutableListOf<Song>()
        val contexts = mutableListOf<PlaybackContext?>()

        override val playbackState = MutableStateFlow(PlaybackInfo())
        override val currentSong = MutableStateFlow<Song?>(null)

        override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
            played += songs to startIndex
            contexts += context
        }

        override suspend fun play(song: Song, context: PlaybackContext?) {
            playedSingle += song
            contexts += context
        }

        override suspend fun playNext(song: Song) {
            queuedNext += song
        }
    }

    private class FakeSettings(initialOrder: SortOrder = SortOrder.DEFAULT) : UnsupportedSettingsRepository() {
        val orders = MutableStateFlow(initialOrder)
        val persisted = mutableListOf<Pair<SortView, SortOrder>>()

        override fun sortOrder(view: SortView): Flow<SortOrder> = orders
        override suspend fun setSortOrder(view: SortView, order: SortOrder) {
            persisted += view to order
            orders.value = order
        }
    }

    private class FakePlugins(private val result: Result<Album>) : UnsupportedPluginRepository() {
        override suspend fun getAlbum(mediaId: MediaId): Result<Album> = result
    }

    private fun viewModel(
        albumResult: Result<Album> = Result.success(album),
        player: FakePlayer = FakePlayer(),
        settings: FakeSettings = FakeSettings(),
    ) = AlbumDetailViewModel(
        albumDetail = AlbumDetail(albumId = albumId, initialName = "Test Album", initialArtworkUrl = null),
        pluginRepository = FakePlugins(albumResult),
        mediaLibraryRepository = com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository(),
        playerRepository = player,
        settingsRepository = settings,
        stringProvider = StringProvider { id, _ -> "res:$id" },
    )

    @Test
    fun `shows a placeholder album while loading so the screen is not blank`() = runTest(dispatcher) {
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertTrue(state is AlbumDetailUiState.Loading)
        // The name the caller already knew, carried through from navigation.
        assertEquals("Test Album", (state as AlbumDetailUiState.Loading).initialAlbum.name)
    }

    @Test
    fun `loads the album and keeps the plugin's track order by default`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AlbumDetailUiState.Success
        assertEquals(listOf("Zebra", "Alpha", "Mango"), state.sortedSongs.map { it.title })
        assertEquals(SortOrder.DEFAULT, state.sortOrder)
    }

    @Test
    fun `a failed fetch surfaces the plugin's own message`() = runTest(dispatcher) {
        val viewModel = viewModel(albumResult = Result.failure(IllegalStateException("region locked")))
        advanceUntilIdle()

        assertEquals("region locked", (viewModel.uiState.value as AlbumDetailUiState.Error).message)
    }

    @Test
    fun `a failure with no message falls back to a localized string`() = runTest(dispatcher) {
        val viewModel = viewModel(albumResult = Result.failure(RuntimeException()))
        advanceUntilIdle()

        assertTrue((viewModel.uiState.value as AlbumDetailUiState.Error).message.startsWith("res:"))
    }

    // The persisted order has to be applied even when it arrives before the album does — otherwise a
    // fast settings read and a slow network fetch race, and the album lands unsorted.
    @Test
    fun `applies the persisted sort order to a freshly loaded album`() = runTest(dispatcher) {
        val viewModel = viewModel(settings = FakeSettings(initialOrder = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)))
        advanceUntilIdle()

        val state = viewModel.uiState.value as AlbumDetailUiState.Success
        assertEquals(listOf("Alpha", "Mango", "Zebra"), state.sortedSongs.map { it.title })
    }

    @Test
    fun `re-sorts an already loaded album when the order changes`() = runTest(dispatcher) {
        val settings = FakeSettings()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        viewModel.setSortOrder(SortOrder(SortOption.TITLE, SortDirection.DESCENDING))
        advanceUntilIdle()

        val state = viewModel.uiState.value as AlbumDetailUiState.Success
        assertEquals(listOf("Zebra", "Mango", "Alpha"), state.sortedSongs.map { it.title })
        // …and it is remembered for next time, under the album-tracks key specifically.
        assertEquals(
            listOf(SortView.ALBUM_TRACKS to SortOrder(SortOption.TITLE, SortDirection.DESCENDING)),
            settings.persisted,
        )
    }

    // REGRESSION GUARD: playback must follow the list the user is looking at. Playing the album's
    // unsorted `songs` here would start the queue at the wrong track whenever a sort is applied.
    @Test
    fun `tapping a track plays the sorted list starting at that track`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(player = player, settings = FakeSettings(initialOrder = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)))
        advanceUntilIdle()

        viewModel.playSong(mango)
        advanceUntilIdle()

        val (songs, startIndex) = player.played.single()
        assertEquals(listOf("Alpha", "Mango", "Zebra"), songs.map { it.title })
        assertEquals(1, startIndex) // Mango's position in the SORTED list
    }

    @Test
    fun `playing the album starts at the first visible track`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(player = player, settings = FakeSettings(initialOrder = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)))
        advanceUntilIdle()

        viewModel.playAlbum()
        advanceUntilIdle()

        val (songs, startIndex) = player.played.single()
        assertEquals("Alpha", songs.first().title)
        assertEquals(0, startIndex)
    }

    @Test
    fun `playback is tagged with the album context so the player can show where it came from`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(player = player)
        advanceUntilIdle()

        viewModel.playAlbum()
        advanceUntilIdle()

        val context = player.contexts.single() as PlaybackContext.Album
        assertEquals(albumId, context.mediaId)
        assertEquals("Test Album", context.name)
    }

    @Test
    fun `shuffle plays every track exactly once`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(player = player)
        advanceUntilIdle()

        viewModel.shuffle()
        advanceUntilIdle()

        val (songs, startIndex) = player.played.single()
        assertEquals(0, startIndex)
        assertEquals(
            listOf("Alpha", "Mango", "Zebra"),
            songs.map { it.title }.sorted(),
        )
    }

    // Actions dispatched before the fetch resolves still work — they run on viewModelScope after
    // the load coroutine, so they see the loaded album. What must NOT happen is playing against a
    // failed load, where there is no track list to play.
    @Test
    fun `playback actions do nothing when the album failed to load`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(
            albumResult = Result.failure(IllegalStateException("offline")),
            player = player,
        )
        advanceUntilIdle()

        viewModel.playAlbum()
        viewModel.shuffle()
        viewModel.playSong(alpha)
        advanceUntilIdle()

        assertTrue(player.played.isEmpty())
        assertTrue(player.playedSingle.isEmpty())
    }

    @Test
    fun `queue actions reach the player without needing a loaded album`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(player = player)

        viewModel.playNext(alpha)
        advanceUntilIdle()

        assertEquals(listOf(alpha), player.queuedNext)
    }
}
