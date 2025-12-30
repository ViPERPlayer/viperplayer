package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.usecase.detail.GetAlbumUseCase
import com.viperplayer.presentation.navigation.AlbumDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for Album Detail screen.
 */
data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Album Detail screen.
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAlbumUseCase: GetAlbumUseCase,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val albumDetail = savedStateHandle.toRoute<AlbumDetail>()
    private val albumId: String = albumDetail.albumId

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val mediaId = MediaId.fromString(albumId)

                // Load album details
                val albumResult = getAlbumUseCase(mediaId)
                if (albumResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = albumResult.exceptionOrNull()?.message ?: "Failed to load album"
                        )
                    }
                    return@launch
                }

                val album = albumResult.getOrNull()!!

                // Songs should be included in album object
                val songs = album.songs.orEmpty()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        album = album,
                        songs = songs
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load album details"
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

    fun playAlbum() {
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
        loadAlbumDetails()
    }
}

