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
import com.viperplayer.domain.usecase.detail.GetArtistAlbumsUseCase
import com.viperplayer.domain.usecase.detail.GetArtistSongsUseCase
import com.viperplayer.domain.usecase.detail.GetArtistUseCase
import com.viperplayer.presentation.navigation.ArtistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for Artist Detail screen.
 */
data class ArtistDetailUiState(
    val isLoading: Boolean = true,
    val artist: Artist? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedTab: ArtistTab = ArtistTab.SONGS,
    val error: String? = null
)

enum class ArtistTab {
    SONGS, ALBUMS
}

/**
 * ViewModel for Artist Detail screen.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArtistUseCase: GetArtistUseCase,
    private val getArtistSongsUseCase: GetArtistSongsUseCase,
    private val getArtistAlbumsUseCase: GetArtistAlbumsUseCase,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val artistDetail = savedStateHandle.toRoute<ArtistDetail>()
    private val artistId: String = artistDetail.artistId

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val mediaId = MediaId.fromString(artistId)

                // Load artist details
                val artistResult = getArtistUseCase(mediaId)
                if (artistResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = artistResult.exceptionOrNull()?.message ?: "Failed to load artist"
                        )
                    }
                    return@launch
                }

                val artist = artistResult.getOrNull()!!

                // Load artist songs
                val songsResult = getArtistSongsUseCase(mediaId, limit = 20)
                val topSongs = songsResult.getOrNull()?.items ?: emptyList()

                // Load artist albums
                val albumsResult = getArtistAlbumsUseCase(mediaId, limit = 20)
                val albums = albumsResult.getOrNull()?.items ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        artist = artist,
                        topSongs = topSongs,
                        albums = albums
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load artist details"
                    )
                }
            }
        }
    }

    fun selectTab(tab: ArtistTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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
                val songs = _uiState.value.topSongs
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

