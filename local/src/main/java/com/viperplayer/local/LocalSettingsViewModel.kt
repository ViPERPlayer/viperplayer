package com.viperplayer.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.local.data.LocalMediaScanner
import com.viperplayer.local.model.LocalSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalSettingsUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<LocalSong> = emptyList(),
)

/**
 * Holds all state for the settings screen: permission status, scan progress and the scanned song
 * list. The view only renders [state] and forwards events (including the system permission dialog
 * result, since launching that dialog is inherently a view-layer concern).
 */
class LocalSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMediaScanner(application)

    private val _state = MutableStateFlow(
        LocalSettingsUiState(hasPermission = scanner.hasAudioPermission())
    )
    val state: StateFlow<LocalSettingsUiState> = _state.asStateFlow()

    val requiredPermission: String get() = LocalMediaScanner.requiredPermission

    init {
        if (_state.value.hasPermission) scan(force = false)
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) scan(force = false)
    }

    /** Re-check on resume: the user may have granted the permission from system settings. */
    fun refreshPermissionState() {
        val granted = scanner.hasAudioPermission()
        if (granted != _state.value.hasPermission) onPermissionResult(granted)
    }

    fun rescan() = scan(force = true)

    private fun scan(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            if (force) scanner.clearCache()
            scanner.scanMedia()
            _state.update { it.copy(isLoading = false, songs = scanner.getAllSongs()) }
        }
    }
}
