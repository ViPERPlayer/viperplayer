package com.viperplayer.presentation.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.PlaylistDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * UI state for Playlist Detail screen.
 */
sealed class PlaylistDetailUiState {
    data class Loading(val initialPlaylist: Playlist) : PlaylistDetailUiState()
    data class Success(
        val playlist: Playlist,
        val songs: List<Song>
    ) : PlaylistDetailUiState()

    data class Error(val message: String) : PlaylistDetailUiState()
}

/** One-shot result of an M3U export, surfaced as a Toast by the screen. */
sealed interface ExportEvent {
    data object Success : ExportEvent
    data object Failure : ExportEvent
}

/**
 * ViewModel for Playlist Detail screen.
 */
@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistDetail: PlaylistDetail,
    @ApplicationContext private val context: Context,
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistDetail: PlaylistDetail): PlaylistDetailViewModel
    }

    private val playlistId = playlistDetail.playlistId

    /** The plugin backing this screen — used to match pending plugin actions on errors. */
    val pluginId: String get() = playlistId.pluginId

    /**
     * Whether this playlist lives in the local Room database (the virtual "Liked Songs" list or a
     * user-created local playlist). These are observed reactively; everything else is a remote plugin
     * playlist loaded once via [PluginRepository].
     */
    private val isLocalPlaylist: Boolean
        get() = playlistId.pluginId == "local"

    // Minimal placeholder shown while the full playlist is (re)fetched by id.
    private val placeholderPlaylist = Playlist(
        id = playlistDetail.playlistId,
        name = playlistDetail.initialName,
        artworkUrl = playlistDetail.initialArtworkUrl,
    )

    private val _uiState =
        MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading(placeholderPlaylist))
    val uiState = _uiState.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ExportEvent>(extraBufferCapacity = 1)
    val exportEvents: SharedFlow<ExportEvent> = _exportEvents.asSharedFlow()

    /** Default file name suggested to the SAF "create document" picker. */
    val suggestedExportFileName: String
        get() {
            val name = when (val state = _uiState.value) {
                is PlaylistDetailUiState.Success -> state.playlist.name
                is PlaylistDetailUiState.Loading -> state.initialPlaylist.name
                else -> playlistDetail.initialName
            }
            val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "playlist" }
            return "$safe.m3u"
        }

    // Expose current song and playing state from player repository
    val currentSong = playerRepository.currentSong
    val isPlaying = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Track if we're already observing a Room-backed local playlist (Liked Songs or a user playlist).
    // These are perpetual DB collectors started once; a later refresh must not restart them.
    private var isObservingLocalPlaylist = false

    init {
        loadPlaylistDetails()
    }

    private fun loadPlaylistDetails() {
        viewModelScope.launch {
            // Local playlists (Liked Songs + user-created ones) are backed by a perpetual DB collector
            // started on the first load; a later refresh must not reset to Loading (the collector
            // won't necessarily re-emit) or restart the collector, or the screen sticks on the spinner
            // forever / leaks collectors.
            if (isLocalPlaylist && isObservingLocalPlaylist) {
                return@launch
            }
            _uiState.update {
                val initialPlaylist = when (it) {
                    is PlaylistDetailUiState.Success -> it.playlist
                    else -> placeholderPlaylist
                }

                PlaylistDetailUiState.Loading(initialPlaylist)
            }

            try {
                // Room-backed local playlists (the virtual "Liked Songs" list and user-created ones)
                // are observed reactively so edits (reorder / remove / add) persist and re-render.
                if (isLocalPlaylist) {
                    // Only start observing once to avoid multiple collectors.
                    if (!isObservingLocalPlaylist) {
                        isObservingLocalPlaylist = true
                        val playlistFlow = if (playlistId.sourceId == "liked_songs") {
                            mediaLibraryRepository.getLikedSongsPlaylist()
                        } else {
                            mediaLibraryRepository.getPlaylist(playlistId)
                        }
                        playlistFlow.collect { playlist ->
                            if (playlist == null) {
                                _uiState.value = PlaylistDetailUiState.Error(
                                    context.getString(R.string.playlist_not_found)
                                )
                                return@collect
                            }
                            val songs = playlist.songs ?: emptyList()
                            _uiState.value = PlaylistDetailUiState.Success(
                                playlist = playlist,
                                songs = songs
                            )
                        }
                    }
                } else {
                    // Load from PluginRepository (one-time load for plugin playlists)
                    val playlistResult = pluginRepository.getPlaylist(playlistId)
                    if (playlistResult.isFailure) {
                        _uiState.value = PlaylistDetailUiState.Error(
                            playlistResult.exceptionOrNull()?.message
                                ?: context.getString(R.string.playlist_load_failed)
                        )
                        return@launch
                    }

                    val playlist = playlistResult.getOrNull()!!

                    // Load playlist songs (use songs from playlist if available, otherwise fetch)
                    val songs = if (playlist.songs != null && playlist.songs.isNotEmpty()) {
                        playlist.songs
                    } else {
                        val songsResult = pluginRepository.getPlaylistSongs(playlistId, limit = 100)
                        songsResult.getOrNull()?.items.orEmpty()
                    }

                    _uiState.value = PlaylistDetailUiState.Success(
                        playlist = playlist,
                        songs = songs
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PlaylistDetailUiState.Error(
                    e.message ?: context.getString(R.string.playlist_load_details_failed)
                )
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                // Only play if song is playable
                if (song.isPlayable) {
                    val state = _uiState.value
                    if (state !is PlaylistDetailUiState.Success) return@launch

                    val songs = state.songs.filter { it.isPlayable }

                    if (songs.isNotEmpty()) {
                        val index = songs.indexOfFirst { it.id == song.id }
                        val context =
                            PlaybackContext.Playlist(state.playlist.id, state.playlist.name)
                        if (index != -1) {
                            playerRepository.playAll(songs, index, context)
                        } else {
                            playerRepository.play(song, context)
                        }
                    } else {
                        val context =
                            PlaybackContext.Playlist(state.playlist.id, state.playlist.name)
                        playerRepository.play(song, context)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            try {
                val state = _uiState.value as? PlaylistDetailUiState.Success ?: return@launch
                val songs = state.songs.filter { it.isPlayable }
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Playlist(state.playlist.id, state.playlist.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val state = _uiState.value as? PlaylistDetailUiState.Success ?: return@launch
                val songs = state.songs.filter { it.isPlayable }.shuffled()
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Playlist(state.playlist.id, state.playlist.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                if (song.isPlayable) {
                    playerRepository.playNext(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                if (song.isPlayable) {
                    playerRepository.addToQueue(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "PlaylistDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadPlaylistDetails()
    }

    /**
     * Whether this playlist can be edited in place (reorder / remove). Only user-created local
     * playlists are editable; the virtual "Liked Songs" list and remote plugin playlists are not.
     */
    val isEditable: Boolean
        get() = playlistId.pluginId == "local" && playlistId.sourceId != "liked_songs"

    /**
     * Remove the song at [index] from this playlist and reflect it in the UI immediately. Backed by
     * [MediaLibraryRepository.removeSongFromPlaylist]; only valid for editable local playlists.
     */
    fun removeSongAt(index: Int) {
        if (!isEditable) return
        val state = _uiState.value as? PlaylistDetailUiState.Success ?: return
        val song = state.songs.getOrNull(index) ?: return
        // Optimistically update the list so the row disappears without waiting on the DB round-trip.
        val updatedSongs = state.songs.toMutableList().apply { removeAt(index) }
        _uiState.value = state.copy(
            playlist = state.playlist.copy(songCount = updatedSongs.size),
            songs = updatedSongs
        )
        viewModelScope.launch {
            try {
                mediaLibraryRepository.removeSongFromPlaylist(playlistId, song.id)
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove song from playlist")
            }
        }
    }

    /**
     * Move the song at [fromIndex] to [toIndex] within this playlist, persisting the new order.
     * Only valid for editable local playlists.
     */
    fun moveSong(fromIndex: Int, toIndex: Int) {
        if (!isEditable || fromIndex == toIndex) return
        val state = _uiState.value as? PlaylistDetailUiState.Success ?: return
        if (fromIndex !in state.songs.indices || toIndex !in state.songs.indices) return
        val updatedSongs = state.songs.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        _uiState.value = state.copy(songs = updatedSongs)
        viewModelScope.launch {
            try {
                mediaLibraryRepository.reorderPlaylistSongs(playlistId, fromIndex, toIndex)
            } catch (e: Exception) {
                Timber.w(e, "Failed to reorder playlist songs")
            }
        }
    }

    /**
     * Rename this playlist to [newName], persisting the change. Only valid for editable local
     * playlists; a blank name is ignored. The Room-backed reactive flow re-emits the new name, so the
     * UI updates without an optimistic write here.
     */
    fun rename(newName: String) {
        if (!isEditable) return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                mediaLibraryRepository.renamePlaylist(playlistId, trimmed)
            } catch (e: Exception) {
                Timber.w(e, "Failed to rename playlist")
            }
        }
    }

    /** Serialize the current playlist to M3U and write it to the SAF [uri]. */
    fun exportToM3u(uri: Uri) {
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val content = mediaLibraryRepository.exportPlaylistToM3u(playlistId)
                        ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext false
                    true
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to export playlist to M3U")
                false
            }
            _exportEvents.tryEmit(if (ok) ExportEvent.Success else ExportEvent.Failure)
        }
    }
}

