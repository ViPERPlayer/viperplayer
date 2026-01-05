package com.viperplayer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
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
        val artist: Artist,
        val topSongs: List<Song>,
        val albums: List<Album>,
        val selectedTab: ArtistTab = ArtistTab.SONGS
    ) : ArtistDetailUiState()
    data class Error(val message: String) : ArtistDetailUiState()
}

enum class ArtistTab {
    SONGS, ALBUMS
}

/**
 * ViewModel for Artist Detail screen.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginRepository: PluginRepository,
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
                // Load artist details
                val artistResult = pluginRepository.getArtist(artistId)
                if (artistResult.isFailure) {
                    _uiState.value = ArtistDetailUiState.Error(
                        artistResult.exceptionOrNull()?.message ?: "Failed to load artist"
                    )
                    return@launch
                }

                val artist = artistResult.getOrNull()!!

                // Load artist songs
                val songsResult = pluginRepository.getArtistSongs(artistId, limit = 20)
                val topSongs = songsResult.getOrNull()?.items.orEmpty()

                // Load artist albums
                val albumsResult = pluginRepository.getArtistAlbums(artistId, limit = 20)
                val albums = albumsResult.getOrNull()?.items.orEmpty()

                _uiState.value = ArtistDetailUiState.Success(
                    artist = artist,
                    topSongs = topSongs,
                    albums = albums
                )
            } catch (e: Exception) {
                _uiState.value = ArtistDetailUiState.Error(
                    e.message ?: "Failed to load artist details"
                )
            }
        }
    }

    fun selectTab(tab: ArtistTab) {
        _uiState.value = when (val state = _uiState.value) {
            is ArtistDetailUiState.Success -> state.copy(selectedTab = tab)
            else -> state
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

    fun playAllSongs() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is ArtistDetailUiState.Success -> state.topSongs
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
        loadArtistDetails()
    }
}

