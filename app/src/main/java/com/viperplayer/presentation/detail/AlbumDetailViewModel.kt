package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.AlbumDetail
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
 * UI state for Album Detail screen.
 */
sealed class AlbumDetailUiState {
    data class Loading(val initialAlbum: Album) : AlbumDetailUiState()
    data class Success(val album: Album) : AlbumDetailUiState()
    data class Error(val message: String) : AlbumDetailUiState()
}

/**
 * ViewModel for Album Detail screen.
 */
@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val albumDetail: AlbumDetail,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(albumDetail: AlbumDetail): AlbumDetailViewModel
    }

    private val albumId = albumDetail.albumId

    /** The plugin backing this screen — used to match pending plugin actions on errors. */
    val pluginId: String get() = albumId.pluginId

    // Minimal placeholder shown while the full album is (re)fetched by id.
    private val placeholderAlbum = Album(
        id = albumDetail.albumId,
        name = albumDetail.initialName,
        artworkUrl = albumDetail.initialArtworkUrl,
    )

    private val _uiState =
        MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading(placeholderAlbum))
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

    init {
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        viewModelScope.launch {
            _uiState.update {
                val initialAlbum = when (it) {
                    is AlbumDetailUiState.Success -> it.album
                    else -> placeholderAlbum
                }

                AlbumDetailUiState.Loading(initialAlbum)
            }

            try {
                // Load album details
                val albumResult = pluginRepository.getAlbum(albumId)
                if (albumResult.isFailure) {
                    _uiState.value = AlbumDetailUiState.Error(
                        albumResult.exceptionOrNull()?.message ?: "Failed to load album"
                    )
                    return@launch
                }

                val album = albumResult.getOrThrow()

                _uiState.value = AlbumDetailUiState.Success(album)
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

                val songs = state.album.songs.orEmpty()

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
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun playAlbum() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is AlbumDetailUiState.Success -> state.album.songs.orEmpty()
                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val songs = when (val state = _uiState.value) {
                    is AlbumDetailUiState.Success -> state.album.songs.orEmpty().shuffled()
                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.playNext(song)
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.addToQueue(song)
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadAlbumDetails()
    }
}

