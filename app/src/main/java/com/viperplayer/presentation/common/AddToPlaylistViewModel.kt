package com.viperplayer.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Outcome of an "add to playlist" action, surfaced as a one-shot event so the sheet can show a
 * confirmation toast only when the song actually landed in the playlist.
 */
data class AddToPlaylistResult(
    val playlistName: String,
    val success: Boolean,
)

/**
 * Backs the "Add to playlist" picker sheet: exposes the user's local playlists and performs the
 * create-new / add-to-existing mutations. Adding a song to a brand-new playlist is a single atomic
 * action from the caller's perspective (create → then append the song).
 */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
) : ViewModel() {

    /** The user-created local playlists that can be targeted, newest first. */
    val playlists: StateFlow<List<Playlist>> = mediaLibraryRepository.getLocalPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _results = MutableSharedFlow<AddToPlaylistResult>(extraBufferCapacity = 1)

    /** One-shot results of add/create actions; the sheet toasts on success and warns on failure. */
    val results: SharedFlow<AddToPlaylistResult> = _results.asSharedFlow()

    /** Append [song] to the existing playlist [playlist], emitting the outcome on [results]. */
    fun addToExisting(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            val success = runAdd(playlist.id, song)
            _results.tryEmit(AddToPlaylistResult(playlist.name, success))
        }
    }

    /** Create a new local playlist named [name], add [song] to it, and emit the outcome. */
    fun createAndAdd(name: String, song: Song) {
        viewModelScope.launch {
            val success = try {
                val playlistId = mediaLibraryRepository.createLocalPlaylist(name)
                runAdd(playlistId, song)
            } catch (e: Exception) {
                Timber.w(e, "Failed to create playlist and add song")
                false
            }
            _results.tryEmit(AddToPlaylistResult(name, success))
        }
    }

    /**
     * Add [song] to [playlistId] and confirm it actually landed there (the repository no-ops when the
     * playlist row is missing), returning whether the add succeeded.
     */
    private suspend fun runAdd(playlistId: MediaId, song: Song): Boolean {
        return try {
            mediaLibraryRepository.addSongToPlaylist(playlistId, song)
            val playlist = mediaLibraryRepository.getPlaylist(playlistId).first()
            playlist?.songs?.any { it.id == song.id } == true
        } catch (e: Exception) {
            Timber.w(e, "Failed to add song to playlist")
            false
        }
    }
}
