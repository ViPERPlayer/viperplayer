package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.sort.MediaSorter
import com.viperplayer.presentation.navigation.AlbumDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /**
     * @param album the loaded album (its `songs` remain in the plugin's original track order).
     * @param sortOrder the chosen track order; [SortOrder.DEFAULT] keeps the disc/track grouping.
     * @param sortedSongs the album's songs after [sortOrder] is applied. Equals `album.songs` when the
     *   order is DEFAULT, so the screen can grade the disc grouping only in that case.
     */
    data class Success(
        val album: Album,
        val sortOrder: SortOrder = SortOrder.DEFAULT,
        val sortedSongs: List<Song> = album.songs.orEmpty(),
    ) : AlbumDetailUiState()

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
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository
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

    // The latest persisted track order, updated on every emission (even while Loading) so a fresh load
    // that finishes before/after the first sort emission still applies the persisted order.
    private var currentSortOrder: SortOrder = SortOrder.DEFAULT

    init {
        loadAlbumDetails()
        observeSortOrder()
    }

    /** Re-apply the persisted track order to the loaded album whenever it changes (or on first load). */
    private fun observeSortOrder() {
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.ALBUM_TRACKS).collect { order ->
                currentSortOrder = order
                _uiState.update { state ->
                    if (state is AlbumDetailUiState.Success) {
                        state.copy(
                            sortOrder = order,
                            sortedSongs = MediaSorter.sortSongs(state.album.songs.orEmpty(), order),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Persist a new track [order]; the observer re-sorts the visible list. */
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(SortView.ALBUM_TRACKS, order)
        }
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

                // Apply the persisted/selected track order (available even on a fresh load).
                val order = currentSortOrder
                _uiState.value = AlbumDetailUiState.Success(
                    album = album,
                    sortOrder = order,
                    sortedSongs = MediaSorter.sortSongs(album.songs.orEmpty(), order),
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

                // Play in the order the user sees (respects the chosen sort).
                val songs = state.sortedSongs

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
                val state = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
                val songs = state.sortedSongs
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Album(state.album.id, state.album.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val state = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
                val songs = state.album.songs.orEmpty().shuffled()
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Album(state.album.id, state.album.name))
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

    /**
     * Toggle the "liked" flag on [song] from its track-row options sheet. Persists the song first (a
     * remote plugin track has no Room row yet), then flips its stored liked state; liking also adds it
     * to the saved library. Mirrors [com.viperplayer.presentation.player.PlayerViewModel.toggleLike].
     */
    fun toggleSongLike(song: Song) {
        viewModelScope.launch {
            try {
                mediaLibraryRepository.saveSong(song)
                val newLiked = !(mediaLibraryRepository.getSong(song.id).first()?.isLiked ?: false)
                mediaLibraryRepository.setSongLiked(song.id, newLiked)
                if (newLiked) mediaLibraryRepository.setSongSaved(song.id, true)
            } catch (e: Exception) {
                Timber.w(e, "AlbumDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadAlbumDetails()
    }
}

