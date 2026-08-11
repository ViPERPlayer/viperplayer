package com.viperplayer.presentation.detail

import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedPluginRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import com.viperplayer.follows.data.FollowedArtistsRepository
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import com.viperplayer.presentation.navigation.ArtistDetail as ArtistDetailRoute

/**
 * Two pieces of real logic live here, and both are invisible from the screen:
 *
 *  * the backfill — some plugins return artist metadata with empty inline lists and serve the
 *    catalog from separate paged endpoints, so the profile has to be topped up or it renders empty;
 *  * the follow toggle — it re-reads the follow state rather than trusting the UI's snapshot, and
 *    refuses to persist a nameless follow.
 */
class ArtistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val artistId = MediaId.Plugin("testsource", "artist-1")

    private val zebra = Song(id = MediaId.Plugin("testsource", "3"), title = "Zebra")
    private val alpha = Song(id = MediaId.Plugin("testsource", "1"), title = "Alpha")
    private val album = Album(id = MediaId.Plugin("testsource", "al-1"), name = "First")

    private class FakePlayer : UnsupportedPlayerRepository() {
        val played = mutableListOf<Pair<List<Song>, Int>>()
        override val playbackState = MutableStateFlow(PlaybackInfo())
        override val currentSong = MutableStateFlow<Song?>(null)
        override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
            played += songs to startIndex
        }
    }

    private class FakeSettings(initialOrder: SortOrder = SortOrder.DEFAULT) : UnsupportedSettingsRepository() {
        private val orders = MutableStateFlow(initialOrder)
        override fun sortOrder(view: SortView): Flow<SortOrder> = orders
        override suspend fun setSortOrder(view: SortView, order: SortOrder) {
            orders.value = order
        }
    }

    private class FakeFollows(following: Boolean = false) : FollowedArtistsRepository {
        val followed = MutableStateFlow(following)
        val persisted = mutableListOf<FollowedArtist>()
        var unfollowCount = 0

        override fun followedArtists(sort: FollowedArtistSort): Flow<List<FollowedArtist>> =
            followed.map { emptyList() }

        override fun isFollowing(mediaId: MediaId): Flow<Boolean> = followed
        override fun count(): Flow<Int> = followed.map { if (it) 1 else 0 }

        override suspend fun follow(artist: FollowedArtist) {
            persisted += artist
            followed.value = true
        }

        override suspend fun unfollow(mediaId: MediaId) {
            unfollowCount++
            followed.value = false
        }
    }

    private class FakePlugins(
        private val artist: Result<ArtistDetail>,
        private val songs: Result<PagedResult<Song>> = Result.success(PagedResult(emptyList(), null)),
        private val albums: Result<PagedResult<Album>> = Result.success(PagedResult(emptyList(), null)),
    ) : UnsupportedPluginRepository() {
        var songsCalls = 0
        var albumsCalls = 0

        override suspend fun getArtist(mediaId: MediaId): Result<ArtistDetail> = artist

        override suspend fun getArtistSongs(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> {
            songsCalls++
            return songs
        }

        override suspend fun getArtistAlbums(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Album>> {
            albumsCalls++
            return albums
        }
    }

    private fun viewModel(
        plugins: FakePlugins,
        follows: FakeFollows = FakeFollows(),
        player: FakePlayer = FakePlayer(),
        settings: FakeSettings = FakeSettings(),
        initialName: String = "Test Artist",
    ) = ArtistDetailViewModel(
        artistDetail = ArtistDetailRoute(artistId = artistId, initialName = initialName, initialImageUrl = null),
        pluginRepository = plugins,
        mediaLibraryRepository = UnsupportedMediaLibraryRepository(),
        playerRepository = player,
        settingsRepository = settings,
        followedArtistsRepository = follows,
        stringProvider = StringProvider { id, _ -> "res:$id" },
    )

    private fun artist(
        topSongs: List<Song> = emptyList(),
        albums: List<Album> = emptyList(),
    ) = ArtistDetail(id = artistId, name = "Test Artist", topSongs = topSongs, albums = albums)

    // ---- loading + backfill --------------------------------------------------------------------

    @Test
    fun `loads an artist that already carries its catalog inline`() = runTest(dispatcher) {
        val plugins = FakePlugins(Result.success(artist(topSongs = listOf(zebra, alpha), albums = listOf(album))))
        val viewModel = viewModel(plugins)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ArtistDetailUiState.Success
        assertEquals(listOf("Zebra", "Alpha"), state.artist.topSongs.map { it.title })
        // Nothing to top up, so the paged endpoints are left alone.
        assertEquals(0, plugins.songsCalls)
        assertEquals(0, plugins.albumsCalls)
    }

    // Some plugins (a plugin) return only metadata from getArtist and serve the catalog from the
    // paged endpoints. Without the backfill the profile renders empty.
    @Test
    fun `backfills songs and albums when the inline lists are empty`() = runTest(dispatcher) {
        val plugins = FakePlugins(
            artist = Result.success(artist()),
            songs = Result.success(PagedResult(listOf(zebra, alpha), null)),
            albums = Result.success(PagedResult(listOf(album), null)),
        )
        val viewModel = viewModel(plugins)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ArtistDetailUiState.Success
        assertEquals(listOf("Zebra", "Alpha"), state.artist.topSongs.map { it.title })
        assertEquals(listOf("First"), state.artist.albums.map { it.name })
    }

    @Test
    fun `backfills only the list that is actually empty`() = runTest(dispatcher) {
        val plugins = FakePlugins(
            artist = Result.success(artist(topSongs = listOf(zebra))),
            albums = Result.success(PagedResult(listOf(album), null)),
        )
        val viewModel = viewModel(plugins)
        advanceUntilIdle()

        assertEquals(0, plugins.songsCalls)
        assertEquals(1, plugins.albumsCalls)
    }

    // An unsupported or failing paged endpoint must leave the profile partly empty, not turn the
    // whole screen into an error — the artist's metadata loaded fine.
    @Test
    fun `a failed backfill leaves the list empty rather than failing the screen`() = runTest(dispatcher) {
        val plugins = FakePlugins(
            artist = Result.success(artist()),
            songs = Result.failure(UnsupportedOperationException("not supported")),
            albums = Result.failure(UnsupportedOperationException("not supported")),
        )
        val viewModel = viewModel(plugins)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ArtistDetailUiState.Success
        assertTrue(state.artist.topSongs.isEmpty())
        assertTrue(state.artist.albums.isEmpty())
    }

    @Test
    fun `a failed artist fetch surfaces the plugin's message`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlugins(Result.failure(IllegalStateException("no such artist"))))
        advanceUntilIdle()

        assertEquals("no such artist", (viewModel.uiState.value as ArtistDetailUiState.Error).message)
    }

    // ---- sorting -------------------------------------------------------------------------------

    @Test
    fun `applies the persisted top-songs order`() = runTest(dispatcher) {
        val plugins = FakePlugins(Result.success(artist(topSongs = listOf(zebra, alpha))))
        val viewModel = viewModel(
            plugins,
            settings = FakeSettings(SortOrder(SortOption.TITLE, SortDirection.ASCENDING)),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as ArtistDetailUiState.Success
        assertEquals(listOf("Alpha", "Zebra"), state.sortedSongs.map { it.title })
        // The unsorted list stays as the plugin returned it.
        assertEquals(listOf("Zebra", "Alpha"), state.artist.topSongs.map { it.title })
    }

    @Test
    fun `playing a song follows the order on screen`() = runTest(dispatcher) {
        val player = FakePlayer()
        val viewModel = viewModel(
            FakePlugins(Result.success(artist(topSongs = listOf(zebra, alpha)))),
            player = player,
            settings = FakeSettings(SortOrder(SortOption.TITLE, SortDirection.ASCENDING)),
        )
        advanceUntilIdle()

        viewModel.playSong(zebra)
        advanceUntilIdle()

        val (songs, startIndex) = player.played.single()
        assertEquals(listOf("Alpha", "Zebra"), songs.map { it.title })
        assertEquals(1, startIndex)
    }

    // ---- follow --------------------------------------------------------------------------------

    @Test
    fun `following persists the loaded name and artwork`() = runTest(dispatcher) {
        val follows = FakeFollows(following = false)
        val viewModel = viewModel(FakePlugins(Result.success(artist())), follows = follows)
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertEquals(artistId, follows.persisted.single().mediaId)
        assertEquals("Test Artist", follows.persisted.single().name)
    }

    // The toggle re-reads the repository rather than trusting the UI's snapshot, so a follow that
    // landed from elsewhere (another screen, a sync) does not get flipped the wrong way.
    @Test
    fun `toggling an already-followed artist unfollows`() = runTest(dispatcher) {
        val follows = FakeFollows(following = true)
        val viewModel = viewModel(FakePlugins(Result.success(artist())), follows = follows)
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertEquals(1, follows.unfollowCount)
        assertTrue(follows.persisted.isEmpty())
    }

    // Only reachable by tapping Follow while still Loading on a screen opened without a name. A
    // nameless row in the Following list has nothing to render, so the tap no-ops until the name
    // arrives.
    @Test
    fun `following is refused while the artist has no name yet`() = runTest(dispatcher) {
        val follows = FakeFollows(following = false)
        val viewModel = viewModel(
            FakePlugins(Result.failure(IllegalStateException("still loading"))),
            follows = follows,
            initialName = "",
        )
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertTrue(follows.persisted.isEmpty())
    }
}
