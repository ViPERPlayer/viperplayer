package com.viperplayer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.usecase.library.GetLibraryAlbumsUseCase
import com.viperplayer.domain.usecase.library.GetLibraryArtistsUseCase
import com.viperplayer.domain.usecase.library.GetLibraryPlaylistsUseCase
import com.viperplayer.domain.usecase.library.GetLibrarySongsUseCase
import com.viperplayer.domain.usecase.player.PlaySongUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val getLibrarySongsUseCase: GetLibrarySongsUseCase,
    private val getLibraryAlbumsUseCase: GetLibraryAlbumsUseCase,
    private val getLibraryArtistsUseCase: GetLibraryArtistsUseCase,
    private val getLibraryPlaylistsUseCase: GetLibraryPlaylistsUseCase,
    private val playSongUseCase: PlaySongUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    init {
        loadContent(LibraryTab.SONGS)
    }
    
    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        loadContent(tab)
    }
    
    private fun loadContent(tab: LibraryTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                when (tab) {
                    LibraryTab.SONGS -> {
                        val result = getLibrarySongsUseCase(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                songs = result.getOrNull()?.items.orEmpty()
                            )
                        }
                    }
                    LibraryTab.ALBUMS -> {
                        val result = getLibraryAlbumsUseCase(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                albums = result.getOrNull()?.items.orEmpty()
                            )
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        val result = getLibraryArtistsUseCase(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                artists = result.getOrNull()?.items.orEmpty()
                            )
                        }
                    }
                    LibraryTab.PLAYLISTS -> {
                        val result = getLibraryPlaylistsUseCase(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlists = result.getOrNull()?.items.orEmpty()
                            )
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
            playSongUseCase(song)
        }
    }
    
    fun refresh() {
        loadContent(_uiState.value.selectedTab)
    }
}

