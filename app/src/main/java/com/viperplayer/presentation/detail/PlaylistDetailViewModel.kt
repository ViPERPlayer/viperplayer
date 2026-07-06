package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.PlaylistDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for Playlist Detail screen.
 */
sealed class PlaylistDetailUiState {
    data class Loading(val initialPlaylist: Playlist) : PlaylistDetailUiState()
    data class Success(
        val playlist: Playlist,
        val songs: List<Song>
    ) : PlaylistDetailUiState()

    data class Error(val message: String) : PlaylistDetailUiState()
}

/**
 * ViewModel for Playlist Detail screen.
 */
@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistDetail: PlaylistDetail,
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistDetail: PlaylistDetail): PlaylistDetailViewModel
    }

    private val playlistId = playlistDetail.playlistId

    /** The plugin backing this screen — used to match pending plugin actions on errors. */
    val pluginId: String get() = playlistId.pluginId

    // Minimal placeholder shown while the full playlist is (re)fetched by id.
    private val placeholderPlaylist = Playlist(
        id = playlistDetail.playlistId,
        name = playlistDetail.initialName,
        artworkUrl = playlistDetail.initialArtworkUrl,
    )

    private val _uiState =
        MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading(placeholderPlaylist))
    val uiState = _uiState.asStateFlow()

    // Expose current song and playing state from player repository
    val currentSong = playerRepository.currentSong
    val isPlaying = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Track if we're already observing the liked songs playlist
    private var isObservingLikedSongs = false

    init {
        loadPlaylistDetails()
    }

    private fun loadPlaylistDetails() {
        viewModelScope.launch {
            // Liked Songs is backed by a perpetual DB collector started on the first load; a later
            // refresh must not reset to Loading (the collector won't necessarily re-emit), or the
            // screen sticks on the spinner forever.
            if (playlistId.pluginId == "local" && playlistId.sourceId == "liked_songs" && isObservingLikedSongs) {
                return@launch
            }
            _uiState.update {
                val initialPlaylist = when (it) {
                    is PlaylistDetailUiState.Success -> it.playlist
                    else -> placeholderPlaylist
                }

                PlaylistDetailUiState.Loading(initialPlaylist)
            }

            try {
                // Check if this is the "Liked Songs" playlist
                if (playlistId.pluginId == "local" && playlistId.sourceId == "liked_songs") {
                    // Observe reactively from MediaLibraryRepository for real-time updates
                    // Only start observing once to avoid multiple collectors
                    if (!isObservingLikedSongs) {
                        isObservingLikedSongs = true
                        mediaLibraryRepository.getLikedSongsPlaylist()
                            .collect { playlist ->
                                val songs = playlist.songs ?: emptyList()
                                _uiState.value = PlaylistDetailUiState.Success(
                                    playlist = playlist,
                                    songs = songs
                                )
                            }
                    }
                } else {
                    // Load from PluginRepository (one-time load for plugin playlists)
                    val playlistResult = pluginRepository.getPlaylist(playlistId)
                    if (playlistResult.isFailure) {
                        _uiState.value = PlaylistDetailUiState.Error(
                            playlistResult.exceptionOrNull()?.message ?: "Failed to load playlist"
                        )
                        return@launch
                    }

                    val playlist = playlistResult.getOrNull()!!

                    // Load playlist songs (use songs from playlist if available, otherwise fetch)
                    val songs = if (playlist.songs != null && playlist.songs.isNotEmpty()) {
                        playlist.songs
                    } else {
                        val songsResult = pluginRepository.getPlaylistSongs(playlistId, limit = 100)
                        songsResult.getOrNull()?.items.orEmpty()
                    }

                    _uiState.value = PlaylistDetailUiState.Success(
                        playlist = playlist,
                        songs = songs
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PlaylistDetailUiState.Error(
                    e.message ?: "Failed to load playlist details"
                )
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                // Only play if song is playable
                if (song.isPlayable) {
                    val state = _uiState.value
                    if (state !is PlaylistDetailUiState.Success) return@launch

                    val songs = state.songs.filter { it.isPlayable }

                    if (songs.isNotEmpty()) {
                        val index = songs.indexOfFirst { it.id == song.id }
                        val context =
                            PlaybackContext.Playlist(state.playlist.id, state.playlist.name)
                        if (index != -1) {
                            playerRepository.playAll(songs, index, context)
                        } else {
                            playerRepository.play(song, context)
                        }
                    } else {
                        val context =
                            PlaybackContext.Playlist(state.playlist.id, state.playlist.name)
                        playerRepository.play(song, context)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is PlaylistDetailUiState.Success -> state.songs.filter { it.isPlayable }
                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is PlaylistDetailUiState.Success -> state.songs.filter { it.isPlayable }
                        .shuffled()

                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                if (song.isPlayable) {
                    playerRepository.playNext(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                if (song.isPlayable) {
                    playerRepository.addToQueue(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadPlaylistDetails()
    }
}

