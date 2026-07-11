package com.viperplayer.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.local.data.LocalMediaScanner
import com.viperplayer.local.data.LocalScanSettings
import com.viperplayer.local.model.LocalSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A distinct scanned folder plus the songs it contains, for the folder-browse view. */
data class LocalFolder(
    val path: String,
    val name: String,
    val songs: List<LocalSong>,
)

data class LocalSettingsUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<LocalSong> = emptyList(),
    val folders: List<LocalFolder> = emptyList(),
    val minTrackLengthSeconds: Int = 0,
    val blacklistedFolders: Set<String> = emptySet(),
)

/**
 * Holds all state for the settings screen: permission status, scan progress, the scanned song
 * list, folder groupings and the scan-filter settings. The view only renders [state] and forwards
 * events (including the system permission dialog result, since launching that dialog is inherently
 * a view-layer concern).
 */
class LocalSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = LocalScanSettings(application)
    private val scanner = LocalMediaScanner(application, settings)

    private val _state = MutableStateFlow(
        LocalSettingsUiState(
            hasPermission = scanner.hasAudioPermission(),
            minTrackLengthSeconds = settings.minTrackLengthSeconds,
            blacklistedFolders = settings.blacklistedFolders,
        )
    )
    val state: StateFlow<LocalSettingsUiState> = _state.asStateFlow()

    val requiredPermission: String get() = LocalMediaScanner.requiredPermission

    init {
        if (_state.value.hasPermission) scan(force = false)
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) {
            // Same process as the host: clear the pending PERMISSION action immediately.
            LocalPluginRuntime.host?.notifyStatusChanged()
            scan(force = false)
        }
    }

    /** Re-check on resume: the user may have granted the permission from system settings. */
    fun refreshPermissionState() {
        val granted = scanner.hasAudioPermission()
        if (granted != _state.value.hasPermission) onPermissionResult(granted)
    }

    fun rescan() = scan(force = true)

    /** Set the minimum track length filter (seconds; 0 = off), persist it and rescan. */
    fun setMinTrackLength(seconds: Int) {
        settings.minTrackLengthSeconds = seconds
        _state.update { it.copy(minTrackLengthSeconds = seconds) }
        scan(force = true)
    }

    /** Toggle whether a folder is blacklisted (excluded from scanning), persist and rescan. */
    fun setFolderBlacklisted(folderPath: String, blacklisted: Boolean) {
        val updated = settings.blacklistedFolders.toMutableSet().apply {
            if (blacklisted) add(folderPath) else remove(folderPath)
        }
        settings.blacklistedFolders = updated
        _state.update { it.copy(blacklistedFolders = updated) }
        scan(force = true)
    }

    private fun scan(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            if (force) scanner.clearCache()
            scanner.scanMedia()
            val songs = scanner.getAllSongs()
            _state.update {
                it.copy(
                    isLoading = false,
                    songs = songs,
                    folders = groupByFolder(songs),
                )
            }
        }
    }

    private fun groupByFolder(songs: List<LocalSong>): List<LocalFolder> =
        songs.filter { it.folderPath != null }
            .groupBy { it.folderPath!! }
            .map { (path, folderSongs) ->
                LocalFolder(
                    path = path,
                    name = path.substringAfterLast('/').ifEmpty { path },
                    songs = folderSongs.sortedBy { it.title.lowercase() },
                )
            }
            .sortedBy { it.name.lowercase() }
}
