package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.ArtistDetail as ArtistDetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for Artist Detail screen.
 */
sealed class ArtistDetailUiState {
    data class Loading(val initialArtist: ArtistDetail) : ArtistDetailUiState()
    data class Success(
        val artist: ArtistDetail
    ) : ArtistDetailUiState()

    data class Error(val message: String) : ArtistDetailUiState()
}

/**
 * ViewModel for Artist Detail screen.
 */
@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val artistDetail: ArtistDetailRoute,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(artistDetail: ArtistDetailRoute): ArtistDetailViewModel
    }

    private val artistId = artistDetail.artistId

    // Minimal placeholder shown while the full artist is (re)fetched by id.
    private val placeholderArtist = ArtistDetail(
        id = artistDetail.artistId,
        name = artistDetail.initialName,
        imageUrl = artistDetail.initialImageUrl,
    )

    private val _uiState =
        MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading(placeholderArtist))
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
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.update {
                val initialArtist = when (it) {
                    is ArtistDetailUiState.Success -> it.artist
                    else -> placeholderArtist
                }

                ArtistDetailUiState.Loading(initialArtist)
            }

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

                // Some plugins (e.g. a plugin) return only artist metadata from getArtist and serve
                // the catalog through the dedicated paged endpoints, leaving the inline lists empty —
                // which rendered as an empty profile. Backfill from getArtistSongs / getArtistAlbums
                // when the inline lists are empty so the screen actually shows the artist's content.
                _uiState.value = ArtistDetailUiState.Success(
                    artist = enrichArtistContent(artist)
                )
            } catch (e: Exception) {
                _uiState.value = ArtistDetailUiState.Error(
                    e.message ?: "Failed to load artist details"
                )
            }
        }
    }

    /**
     * Backfill an artist's songs/albums from the paged endpoints when [PluginRepository.getArtist]
     * didn't inline them (it's optional in the plugin API). Both are fetched concurrently and only
     * when their inline list is empty; failures or unsupported endpoints simply leave them empty.
     */
    private suspend fun enrichArtistContent(artist: ArtistDetail): ArtistDetail = coroutineScope {
        val songsDeferred = if (artist.topSongs.isEmpty()) {
            async { pluginRepository.getArtistSongs(artist.id).getOrNull()?.items.orEmpty() }
        } else null
        val albumsDeferred = if (artist.albums.isEmpty()) {
            async { pluginRepository.getArtistAlbums(artist.id).getOrNull()?.items.orEmpty() }
        } else null

        artist.copy(
            topSongs = songsDeferred?.await() ?: artist.topSongs,
            albums = albumsDeferred?.await() ?: artist.albums
        )
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is ArtistDetailUiState.Success) return@launch

                val songs = state.artist.topSongs

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    val context = PlaybackContext.Artist(state.artist.id, state.artist.name)
                    if (index != -1) {
                        playerRepository.playAll(songs, index, context)
                    } else {
                        playerRepository.play(song, context)
                    }
                } else {
                    val context = PlaybackContext.Artist(state.artist.id, state.artist.name)
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
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
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.playNext(song)
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.addToQueue(song)
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadArtistDetails()
    }
}

