package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.AlbumDetail
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
 * UI state for Album Detail screen.
 */
sealed class AlbumDetailUiState {
    data object Loading : AlbumDetailUiState()
    data class Success(
        val album: Album,
        val songs: List<Song>
    ) : AlbumDetailUiState()
    data class Error(val message: String) : AlbumDetailUiState()
}

/**
 * ViewModel for Album Detail screen.
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val albumDetail = savedStateHandle.toRoute<AlbumDetail>()
    private val albumId: MediaId = MediaId(
        pluginId = albumDetail.pluginId,
        sourceId = albumDetail.sourceId
    )

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    // Expose current song and playing state from player repository
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Loading

            try {
                // Load album details
                val albumResult = pluginRepository.getAlbum(albumId)
                if (albumResult.isFailure) {
                    _uiState.value = AlbumDetailUiState.Error(
                        albumResult.exceptionOrNull()?.message ?: "Failed to load album"
                    )
                    return@launch
                }

                val album = albumResult.getOrNull()!!

                // Songs should be included in album object
                val songs = album.songs.orEmpty()

                _uiState.value = AlbumDetailUiState.Success(
                    album = album,
                    songs = songs
                )
            } catch (e: Exception) {
                _uiState.value = AlbumDetailUiState.Error(
                    e.message ?: "Failed to load album details"
                )
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is AlbumDetailUiState.Success) return@launch
                
                val songs = state.songs

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    val context = PlaybackContext.Album(state.album.id, state.album.name)
                    if (index != -1) {
                        playerRepository.playAll(songs, index, context)
                    } else {
                        playerRepository.play(song, context)
                    }
                } else {
                    val context = PlaybackContext.Album(state.album.id, state.album.name)
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun playAlbum() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is AlbumDetailUiState.Success -> state.songs
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
                    is AlbumDetailUiState.Success -> state.songs.shuffled()
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

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.playNext(song)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.addToQueue(song)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun refresh() {
        loadAlbumDetails()
    }
}

