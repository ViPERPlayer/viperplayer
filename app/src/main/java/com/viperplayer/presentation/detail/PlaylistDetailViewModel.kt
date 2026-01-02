package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.usecase.detail.GetPlaylistSongsUseCase
import com.viperplayer.domain.usecase.detail.GetPlaylistUseCase
import com.viperplayer.presentation.navigation.PlaylistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for Playlist Detail screen.
 */
data class PlaylistDetailUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Playlist Detail screen.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistUseCase: GetPlaylistUseCase,
    private val getPlaylistSongsUseCase: GetPlaylistSongsUseCase,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val playlistDetail = savedStateHandle.toRoute<PlaylistDetail>()
    private val playlistId: MediaId = MediaId(
        pluginId = playlistDetail.pluginId,
        sourceId = playlistDetail.sourceId
    )

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylistDetails()
    }

    private fun loadPlaylistDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load playlist details
                val playlistResult = getPlaylistUseCase(playlistId)
                if (playlistResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = playlistResult.exceptionOrNull()?.message ?: "Failed to load playlist"
                        )
                    }
                    return@launch
                }

                val playlist = playlistResult.getOrNull()!!

                // Load playlist songs (use songs from playlist if available, otherwise fetch)
                val songs = if (playlist.songs != null && playlist.songs.isNotEmpty()) {
                    playlist.songs
                } else {
                    val songsResult = getPlaylistSongsUseCase(playlistId, limit = 100)
                    songsResult.getOrNull()?.items.orEmpty()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlist = playlist,
                        songs = songs
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load playlist details"
                    )
                }
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.play(song)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            try {
                val songs = _uiState.value.songs
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
                val songs = _uiState.value.songs.shuffled()
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

