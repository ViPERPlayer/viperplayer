package com.viperplayer.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Downloads screen. Surfaces the persisted, completed downloads plus the live
 * in-progress map from [DownloadManager], and forwards remove/retry actions.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val mediaLibraryRepository: MediaLibraryRepository,
) : ViewModel() {

    /** Songs already downloaded (persisted, playable offline). */
    val downloadedSongs: StateFlow<List<Song>> =
        mediaLibraryRepository.getAllDownloadedSongs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /** In-flight / failed / unsupported download progress, keyed by media id. */
    val downloads: StateFlow<Map<MediaId, DownloadManager.DownloadProgress>> = downloadManager.downloads

    /** Delete a downloaded song's file and clear its downloaded flag. */
    fun remove(mediaId: MediaId) {
        viewModelScope.launch { downloadManager.remove(mediaId) }
    }

    /**
     * Re-queue a failed/removed download by id. Resolves the song's real metadata from the library
     * first, so a successful retry never persists a placeholder title (falls back to a minimal Song
     * only if the id isn't in the library).
     */
    fun retry(mediaId: MediaId) {
        viewModelScope.launch {
            val song = mediaLibraryRepository.getSong(mediaId).first()
                ?: Song(id = mediaId, title = mediaId.sourceId)
            downloadManager.enqueue(song)
        }
    }
}
