package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.PlaylistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for Playlist Detail screen.
 */
sealed class PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState()
    data class Success(
        val playlist: Playlist,
        val songs: List<Song>
    ) : PlaylistDetailUiState()
    data class Error(val message: String) : PlaylistDetailUiState()
}

/**
 * ViewModel for Playlist Detail screen.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository
) : ViewModel() {

    private val playlistDetail = savedStateHandle.toRoute<PlaylistDetail>()
    private val playlistId: MediaId = MediaId(
        pluginId = playlistDetail.pluginId,
        sourceId = playlistDetail.sourceId
    )

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    // Expose current song and playing state from player repository
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
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
            _uiState.value = PlaylistDetailUiState.Loading

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
                    playerRepository.play(song)
                }
            } catch (e: Exception) {
                // Handle error
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
                // Handle error
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is PlaylistDetailUiState.Success -> state.songs.filter { it.isPlayable }.shuffled()
                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun refresh() {
        loadPlaylistDetails()
    }
}

