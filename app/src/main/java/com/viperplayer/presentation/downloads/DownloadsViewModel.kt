package com.viperplayer.presentation.downloads

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.mapper.mediaIdFromColumns
import com.viperplayer.domain.download.DownloadProgress
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Per-completed-download file facts derived on read from its on-disk file: the human-formatted byte
 * size is done in the UI (needs a Context/Locale), so only the raw [sizeBytes] and a short uppercase
 * container [formatLabel] (e.g. `FLAC`, `M4A`) are carried here.
 */
data class DownloadFileInfo(
    val sizeBytes: Long,
    val formatLabel: String?,
)

/**
 * ViewModel for the Downloads screen. Surfaces the persisted, completed downloads plus the live
 * in-progress map from [DownloadManager], the total on-disk storage used, per-completed-song file
 * facts (size + format) and resolved titles for in-flight ids, and forwards remove/retry actions.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val songDao: SongDao,
) : ViewModel() {

    /**
     * Total internal-storage capacity in bytes (a real device figure via [StatFs], read once).
     * Backs the storage-usage bar fraction (downloads used / total), so the bar reflects a genuine
     * proportion rather than a fabricated percentage; `0` when it can't be read (bar then omits its
     * fill, keeping just the "<size> used" text).
     */
    val totalStorageBytes: Long =
        runCatching { StatFs(context.filesDir.absolutePath).totalBytes }.getOrDefault(0L)

    /** Songs already downloaded (persisted, playable offline). */
    val downloadedSongs: StateFlow<List<Song>> =
        mediaLibraryRepository.getAllDownloadedSongs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /** In-flight / failed / unsupported download progress, keyed by media id. */
    val downloads: StateFlow<Map<MediaId, DownloadProgress>> = downloadManager.downloads

    /**
     * Downloaded entities re-read reactively so file size/format update whenever a download completes
     * or is removed. Statful IO (File(path).length()) is done downstream on [Dispatchers.IO].
     */
    private val downloadedEntities: Flow<List<SongEntity>> = songDao.getAllDownloaded()

    /**
     * Total bytes currently on disk across all completed downloads. Reactive: recomputed on
     * [Dispatchers.IO] whenever the downloaded set changes (complete / remove).
     */
    val storageBytes: StateFlow<Long> =
        downloadedEntities
            .map { entities ->
                entities.sumOf { entity ->
                    entity.downloadPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.length() else 0L
                    } ?: 0L
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0L
            )

    /**
     * Per-completed-song file facts (size + format), keyed by media id, derived on read from each
     * song's [SongEntity.downloadPath] on [Dispatchers.IO]. Only rows with a real on-disk file appear.
     */
    val fileInfo: StateFlow<Map<MediaId, DownloadFileInfo>> =
        downloadedEntities
            .map { entities ->
                entities.mapNotNull { entity ->
                    val path = entity.downloadPath ?: return@mapNotNull null
                    val file = File(path)
                    if (!file.exists()) return@mapNotNull null
                    val format = path.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.uppercase()
                    mediaIdFromColumns(entity.idType, entity.pluginId, entity.sourceId) to
                        DownloadFileInfo(file.length(), format)
                }.toMap()
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * Resolved real titles for the in-flight download ids, keyed by media id. Looked up LOCALLY from
     * the library (no network) so a queued/failed row can show its true title instead of the raw
     * source id. Recomputed whenever the set of in-flight ids changes.
     */
    val inFlightTitles: StateFlow<Map<MediaId, String>> =
        downloads
            .map { it.keys }
            .distinctUntilChanged()
            .map { ids ->
                ids.mapNotNull { id ->
                    val song = mediaLibraryRepository.getSong(id).first()
                    song?.title?.let { id to it }
                }.toMap()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

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
