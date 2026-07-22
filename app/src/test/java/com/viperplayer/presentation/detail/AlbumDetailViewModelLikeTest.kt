package com.viperplayer.presentation.detail

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedPluginRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import com.viperplayer.presentation.navigation.AlbumDetail
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
 * Unit test for [AlbumDetailViewModel.toggleSongLike] over a fake [MediaLibraryRepository]. Asserts the
 * per-song like button (from a track-row options sheet) persists the song and flips its stored liked
 * state through the repository, and that a second toggle un-likes it.
 *
 * The other repositories are inert doubles — the ViewModel's load path errors out (no album) and never
 * touches them for the like flow — so this stays focused on the like interaction.
 */
class AlbumDetailViewModelLikeTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val song = Song(id = MediaId.Plugin("testsource", "s1"), title = "Song")

    private fun viewModel(media: FakeMediaLibraryRepository) = AlbumDetailViewModel(
        albumDetail = AlbumDetail(albumId = MediaId.Plugin("testsource", "a1")),
        pluginRepository = UnusedPluginRepository(),
        mediaLibraryRepository = media,
        playerRepository = InertPlayerRepository(),
        settingsRepository = InertSettingsRepository(),
        stringProvider = { _, _ -> "" },
    )

    @Test
    fun toggleSongLike_persistsSong_andLikesIt() = runTest {
        val media = FakeMediaLibraryRepository()
        val vm = viewModel(media)

        vm.toggleSongLike(song)
        advanceUntilIdle()

        assertTrue("song must be saved before liking", media.savedSongs.contains(song.id))
        assertEquals(true, media.liked[song.id])
        assertTrue("liking also adds to saved library", media.saved.contains(song.id))
    }

    @Test
    fun toggleSongLike_twice_unlikes() = runTest {
        val media = FakeMediaLibraryRepository()
        val vm = viewModel(media)

        vm.toggleSongLike(song)
        advanceUntilIdle()
        vm.toggleSongLike(song)
        advanceUntilIdle()

        assertEquals(false, media.liked[song.id])
    }

    /** Records the like/save interaction and reflects it back through [getSong] like the real DAO. */
    private class FakeMediaLibraryRepository : MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        val savedSongs = mutableSetOf<MediaId>()
        val liked = mutableMapOf<MediaId, Boolean>()
        val saved = mutableSetOf<MediaId>()

        override suspend fun saveSong(song: Song) {
            savedSongs.add(song.id)
        }

        override fun getSong(mediaId: MediaId): Flow<Song?> =
            flowOf(Song(id = mediaId, title = "x", isLiked = liked[mediaId] ?: false))

        override suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean) {
            liked[mediaId] = isLiked
        }

        override suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean) {
            if (isSaved) saved.add(mediaId) else saved.remove(mediaId)
        }
    }

    /** getAlbum fails so the load path settles into Error without exercising real album logic. */
    private class UnusedPluginRepository : PluginRepository by UnsupportedPluginRepository() {
        override suspend fun getAlbum(mediaId: MediaId): Result<Album> =
            Result.failure(IllegalStateException("no album in test"))
    }

    private class InertPlayerRepository : PlayerRepository by UnsupportedPlayerRepository() {
        override val playbackState: StateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())
        override val currentSong: StateFlow<Song?> = MutableStateFlow(null)
    }

    private class InertSettingsRepository : SettingsRepository by UnsupportedSettingsRepository() {
        override fun sortOrder(view: SortView): Flow<SortOrder> = flowOf(SortOrder.DEFAULT)
    }
}
