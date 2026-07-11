package com.viperplayer.presentation.settings.storage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.backup.BackupManager
import com.viperplayer.domain.repository.CacheRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageSettingsUiState(
    val downloadedSongsSize: Long = 0L,
    val maxSongCacheSize: Long = 500L * 1024 * 1024,
    val songCacheSize: Long = 0L,
    val maxImageCacheSize: Long = 200L * 1024 * 1024,
    val imageCacheSize: Long = 0L,
    val isClearing: Boolean = false
)

/** One-shot messages surfaced as a Toast/snackbar by the screen. */
sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data object ExportFailure : BackupEvent
    data object RestoreSuccess : BackupEvent
    data object RestoreFailure : BackupEvent
}

@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageSettingsUiState())
    val uiState: StateFlow<StorageSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.maxSongCacheSize.collect { size ->
                _uiState.update { it.copy(maxSongCacheSize = size) }
            }
        }
        viewModelScope.launch {
            settingsRepository.maxImageCacheSize.collect { size ->
                _uiState.update { it.copy(maxImageCacheSize = size) }
            }
        }
    }

    fun refreshSizes() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadedSongsSize = cacheRepository.getDownloadedSongsSize(),
                    songCacheSize = cacheRepository.getSongCacheSize(),
                    imageCacheSize = cacheRepository.getImageCacheSize()
                )
            }
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                cacheRepository.clearAllDownloads()
                refreshSizes()
            } finally {
                _uiState.update { it.copy(isClearing = false) }
            }
        }
    }

    fun setMaxSongCacheSize(size: Long) {
        viewModelScope.launch {
            settingsRepository.setMaxSongCacheSize(size)
        }
    }

    fun clearSongCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                cacheRepository.clearSongCache()
                refreshSizes()
            } finally {
                _uiState.update { it.copy(isClearing = false) }
            }
        }
    }

    fun setMaxImageCacheSize(size: Long) {
        viewModelScope.launch {
            settingsRepository.setMaxImageCacheSize(size)
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                cacheRepository.clearImageCache()
                refreshSizes()
            } finally {
                _uiState.update { it.copy(isClearing = false) }
            }
        }
    }

    /** Gather a backup and write it as JSON to the SAF-provided [uri]. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val event = runCatching {
                withContext(Dispatchers.IO) {
                    val bundle = backupManager.export()
                    val serialized = backupManager.serialize(bundle)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(serialized.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open output stream for $uri")
                }
                BackupEvent.ExportSuccess
            }.getOrDefault(BackupEvent.ExportFailure)
            _events.emit(event)
        }
    }

    /** Read a backup JSON from the SAF-provided [uri], deserialize it and restore it. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val event = runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Could not open input stream for $uri")
                    val bundle = backupManager.deserialize(json)
                    backupManager.restore(bundle)
                }
                BackupEvent.RestoreSuccess
            }.getOrDefault(BackupEvent.RestoreFailure)
            refreshSizes()
            _events.emit(event)
        }
    }
}

