package com.viperplayer.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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

    /** Re-queue a song for download (e.g. after a failure). */
    fun retry(song: Song) {
        downloadManager.enqueue(song)
    }
}
