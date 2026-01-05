package com.viperplayer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
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
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository
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
                        val result = pluginRepository.getLibrarySongs(limit = 50)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                songs = result.getOrNull()?.items.orEmpty()
                            )
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
                        val result = pluginRepository.getLibraryPlaylists(limit = 50)
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
            playerRepository.play(song)
        }
    }
    
    fun refresh() {
        loadContent(_uiState.value.selectedTab)
    }
}

