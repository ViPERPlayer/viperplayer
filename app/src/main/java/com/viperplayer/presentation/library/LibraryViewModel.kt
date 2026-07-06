package com.viperplayer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.repository.NetworkConnectivityChecker
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Library tabs.
 */
enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

/**
 * UI State for Library screen.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Library screen.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val networkConnectivityChecker: NetworkConnectivityChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Expose current song and playing state from player repository
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Track if we're already observing playlists to avoid multiple collectors
    private var isObservingPlaylists = false

    // The current tab's load coroutine — some tabs collect perpetual flows, so cancel the previous
    // one before each reload, otherwise re-selecting a tab / refreshing leaks a collector per call.
    private var loadJob: Job? = null

    init {
        loadContent(LibraryTab.SONGS)
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        loadContent(tab)
    }

    private fun loadContent(tab: LibraryTab) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                when (tab) {
                    LibraryTab.SONGS -> {
                        val result = pluginRepository.getLibrarySongs(limit = 50)
                        val songs = result.getOrNull()?.items.orEmpty()

                        // Update playability based on plugin connection, download status, and internet availability
                        // Combine with connected plugins and internet availability flows to make it reactive
                        combine(
                            flowOf(songs),
                            pluginRepository.connectedPlugins,
                            networkConnectivityChecker.isInternetAvailable
                        ) { songsList, connectedPlugins, isInternetAvailable ->
                            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()

                            songsList.map { song ->
                                val isPluginConnected = song.id.pluginId in connectedPluginIds
                                // Song is playable if:
                                // 1. Song is downloaded (can play offline), OR
                                // 2. Plugin is connected AND (song doesn't require internet OR internet is available)
                                val isPlayable = song.isDownloaded ||
                                        (isPluginConnected && (!song.requiresInternet || isInternetAvailable))
                                song.copy(isPlayable = isPlayable)
                            }
                        }.collect { songsWithPlayability ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    songs = songsWithPlayability
                                )
                            }
                        }
                    }

                    LibraryTab.ALBUMS -> {
                        val result = pluginRepository.getLibraryAlbums(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                albums = result.getOrNull()?.items.orEmpty()
                            )
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        val result = pluginRepository.getLibraryArtists(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                artists = result.getOrNull()?.items.orEmpty()
                            )
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        // Load plugin playlists
                        val result = pluginRepository.getLibraryPlaylists(limit = 50)
                        val pluginPlaylists = result.getOrNull()?.items.orEmpty()

                        // Get the "Liked Songs" playlist
                        val likedSongsPlaylist = mediaLibraryRepository.getLikedSongsPlaylist()
                            .first()

                        // Combine plugin playlists with "Liked Songs" playlist
                        // Always show "Liked Songs" at the top if it has any songs
                        val allPlaylists = if (likedSongsPlaylist.songCount > 0) {
                            listOf(likedSongsPlaylist) + pluginPlaylists
                        } else {
                            pluginPlaylists
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlists = allPlaylists
                            )
                        }

                        // Observe changes to liked songs playlist reactively (only once)
                        if (!isObservingPlaylists) {
                            isObservingPlaylists = true
                            viewModelScope.launch {
                                mediaLibraryRepository.getLikedSongsPlaylist()
                                    .collect { updatedLikedSongsPlaylist ->
                                        // Only update if we're on playlists tab
                                        if (_uiState.value.selectedTab == LibraryTab.PLAYLISTS) {
                                            // Reuse the already-fetched plugin playlists instead of
                                            // re-hitting the network on every liked-songs change (e.g. a like).
                                            val updatedAllPlaylists =
                                                if (updatedLikedSongsPlaylist.songCount > 0) {
                                                    listOf(updatedLikedSongsPlaylist) + pluginPlaylists
                                                } else {
                                                    pluginPlaylists
                                                }

                                            _uiState.update {
                                                it.copy(playlists = updatedAllPlaylists)
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load library"
                    )
                }
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            playerRepository.play(song)
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            playerRepository.playNext(song)
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            playerRepository.addToQueue(song)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            mediaLibraryRepository.saveSong(song)
            val currentLiked = mediaLibraryRepository.getSong(song.id).first()?.isLiked ?: false
            mediaLibraryRepository.setSongLiked(song.id, !currentLiked)
        }
    }

    fun downloadSong(song: Song) {
        // TODO: Implement download
        viewModelScope.launch {
            // mediaLibraryRepository.setSongDownloaded(song.id, true, downloadPath)
        }
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            // Load album songs and play
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            }
        }
    }

    fun shuffleAlbum(album: Album) {
        viewModelScope.launch {
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    playerRepository.playAll(shuffled, 0)
                }
            }
        }
    }

    fun addAlbumToQueue(album: Album) {
        viewModelScope.launch {
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.forEach { song ->
                playerRepository.addToQueue(song)
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            }
        }
    }

    fun shufflePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    playerRepository.playAll(shuffled, 0)
                }
            }
        }
    }

    fun playPlaylistNext(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.forEach { song ->
                playerRepository.playNext(song)
            }
        }
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.forEach { song ->
                playerRepository.addToQueue(song)
            }
        }
    }

    fun togglePlaylistLike(playlist: Playlist) {
        viewModelScope.launch {
            val currentSaved = mediaLibraryRepository.isPlaylistSaved(playlist.id)
            mediaLibraryRepository.setPlaylistSaved(playlist.id, !currentSaved)
        }
    }

    fun refresh() {
        loadContent(_uiState.value.selectedTab)
    }
}

