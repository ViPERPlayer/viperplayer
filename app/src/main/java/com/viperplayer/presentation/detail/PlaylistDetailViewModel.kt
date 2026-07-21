package com.viperplayer.presentation.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.autoplaylist.AutoPlaylistType
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.radio.RadioPlaylist
import com.viperplayer.domain.repository.AutoPlaylistRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.RadioPlaylistRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.sort.MediaSorter
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
import kotlinx.coroutines.flow.first
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

    /**
     * @param songs the playlist's songs in insertion order — the source of truth used by edit mode
     *   (reorder / remove) and all mutations, which must never see a display-sorted order.
     * @param sortOrder the chosen display order for view (non-edit) mode; [SortOrder.DEFAULT] keeps
     *   insertion order.
     * @param sortedSongs [songs] after [sortOrder] is applied — what the non-edit list renders.
     */
    data class Success(
        val playlist: Playlist,
        val songs: List<Song>,
        val sortOrder: SortOrder = SortOrder.DEFAULT,
        val sortedSongs: List<Song> = songs,
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
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
    private val radioPlaylistRepository: RadioPlaylistRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistDetail: PlaylistDetail): PlaylistDetailViewModel
    }

    private val playlistId = playlistDetail.playlistId

    /**
     * The plugin backing this screen — used to match pending plugin actions on errors. Non-plugin
     * playlists (local, radio) have no plugin to reconnect to, so they report an empty id that never
     * matches a pending action; a [MediaId.Radio]'s [MediaId.routingPluginId] would otherwise throw.
     */
    val pluginId: String get() = (playlistId as? MediaId.Plugin)?.pluginId ?: ""

    /**
     * Whether this playlist lives in the local Room database (the virtual "Liked Songs" list or a
     * user-created local playlist). These are observed reactively; everything else is a remote plugin
     * playlist loaded once via [PluginRepository].
     */
    private val isLocalPlaylist: Boolean
        get() = playlistId is MediaId.Local

    /**
     * The dynamic auto-playlist type this screen renders, or null when it is a normal (local/plugin)
     * playlist. Auto-playlists are virtual and computed live from library + play-history, so they are
     * observed reactively (like local playlists) rather than fetched once.
     */
    private val autoPlaylistType: AutoPlaylistType? =
        if (AutoPlaylistType.isAutoPlaylist(playlistId)) AutoPlaylistType.fromId(playlistId.sourceId) else null

    /**
     * The seed song's [MediaId] this screen renders a "Song radio" for, or null when it is not a radio
     * playlist. Radio playlists are virtual (issue #7): the queue is generated once from the seed's
     * related songs and shown in the standard detail screen — no auto-play, the user plays from here.
     */
    private val radioSeedId: MediaId? =
        if (RadioPlaylist.isRadioPlaylist(playlistId)) RadioPlaylist.parseSeedId(playlistId) else null

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

    // Latest persisted display order, tracked even while Loading so a fresh load applies it.
    private var currentSortOrder: SortOrder = SortOrder.DEFAULT

    init {
        loadPlaylistDetails()
        observeSortOrder()
    }

    /** Build a Success state with [order] applied to [songs] for the (non-edit) display list. */
    private fun successState(playlist: Playlist, songs: List<Song>, order: SortOrder) =
        PlaylistDetailUiState.Success(
            playlist = playlist,
            songs = songs,
            sortOrder = order,
            sortedSongs = MediaSorter.sortSongs(songs, order),
        )

    /** Re-apply the persisted display order whenever it changes (or on first load). */
    private fun observeSortOrder() {
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.PLAYLIST_SONGS).collect { order ->
                currentSortOrder = order
                _uiState.update { state ->
                    if (state is PlaylistDetailUiState.Success) {
                        state.copy(
                            sortOrder = order,
                            sortedSongs = MediaSorter.sortSongs(state.songs, order),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Persist a new display [order]; the observer re-sorts the visible list. */
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(SortView.PLAYLIST_SONGS, order)
        }
    }

    private fun loadPlaylistDetails() {
        viewModelScope.launch {
            // Local playlists (Liked Songs + user-created ones) and virtual auto-playlists are backed
            // by a perpetual reactive collector started on the first load; a later refresh must not
            // reset to Loading (the collector won't necessarily re-emit) or restart the collector, or
            // the screen sticks on the spinner forever / leaks collectors.
            if ((isLocalPlaylist || autoPlaylistType != null) && isObservingLocalPlaylist) {
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
                // Virtual "Song radio" (issue #7): generated once from the seed's related songs and
                // rendered in this standard detail screen (no auto-play — the user plays from here).
                if (radioSeedId != null) {
                    val playlist = radioPlaylistRepository.getRadioPlaylist(radioSeedId)
                    _uiState.value = successState(playlist, playlist.songs.orEmpty(), currentSortOrder)
                    return@launch
                }

                // Virtual auto-playlists are computed live from library + play-history; observe the
                // repository so the list re-renders as the library / history change.
                if (autoPlaylistType != null) {
                    if (!isObservingLocalPlaylist) {
                        isObservingLocalPlaylist = true
                        autoPlaylistRepository.getAutoPlaylist(autoPlaylistType).collect { playlist ->
                            val songs = playlist.songs ?: emptyList()
                            _uiState.value = successState(playlist, songs, currentSortOrder)
                        }
                    }
                    return@launch
                }

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
                            _uiState.value = successState(playlist, songs, currentSortOrder)
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

                    _uiState.value = successState(playlist, songs, currentSortOrder)
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

                    // Play in the order the user sees (respects the chosen display sort).
                    val songs = state.sortedSongs.filter { it.isPlayable }

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
                val songs = state.sortedSongs.filter { it.isPlayable }
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
        get() = playlistId is MediaId.Local && playlistId.sourceId != "liked_songs"

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
            songs = updatedSongs,
            sortedSongs = MediaSorter.sortSongs(updatedSongs, state.sortOrder)
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
        _uiState.value = state.copy(
            songs = updatedSongs,
            sortedSongs = MediaSorter.sortSongs(updatedSongs, state.sortOrder)
        )
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

