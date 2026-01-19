package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.ArtistDetail
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
 * UI state for Artist Detail screen.
 */
sealed class ArtistDetailUiState {
    data object Loading : ArtistDetailUiState()
    data class Success(
        val artist: Artist
    ) : ArtistDetailUiState()
    data class Error(val message: String) : ArtistDetailUiState()
}

/**
 * ViewModel for Artist Detail screen.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val artistDetail = savedStateHandle.toRoute<ArtistDetail>()
    private val artistId: MediaId = MediaId(
        pluginId = artistDetail.pluginId,
        sourceId = artistDetail.sourceId
    )

    private val _uiState = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

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
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.value = ArtistDetailUiState.Loading

            try {
                // Load artist details - the artist object from AIDL already contains topSongs and albums
                val artistResult = pluginRepository.getArtist(artistId)
                if (artistResult.isFailure) {
                    _uiState.value = ArtistDetailUiState.Error(
                        artistResult.exceptionOrNull()?.message ?: "Failed to load artist"
                    )
                    return@launch
                }

                val artist = artistResult.getOrNull()!!

                // Save artist and all related data to database
                mediaLibraryRepository.saveArtist(artist)

                // Use all data directly from the artist object
                _uiState.value = ArtistDetailUiState.Success(
                    artist = artist
                )
            } catch (e: Exception) {
                _uiState.value = ArtistDetailUiState.Error(
                    e.message ?: "Failed to load artist details"
                )
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is ArtistDetailUiState.Success -> state.artist.topSongs
                    else -> emptyList()
                }

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    if (index != -1) {
                        playerRepository.playAll(songs, index)
                    } else {
                        playerRepository.play(song)
                    }
                } else {
                    playerRepository.play(song)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun playAllSongs() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is ArtistDetailUiState.Success -> state.artist.topSongs
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
        loadArtistDetails()
    }
}

