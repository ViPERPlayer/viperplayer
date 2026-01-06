package com.viperplayer.presentation.settings.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.CacheRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageSettingsUiState(
    val downloadedSongsSize: Long = 0L,
    val maxSongCacheSize: Long = 500L * 1024 * 1024,
    val songCacheSize: Long = 0L,
    val maxImageCacheSize: Long = 200L * 1024 * 1024,
    val imageCacheSize: Long = 0L,
    val isClearing: Boolean = false
)

@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StorageSettingsUiState())
    val uiState: StateFlow<StorageSettingsUiState> = _uiState.asStateFlow()
    
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
            _uiState.update { it.copy(
                downloadedSongsSize = cacheRepository.getDownloadedSongsSize(),
                songCacheSize = cacheRepository.getSongCacheSize(),
                imageCacheSize = cacheRepository.getImageCacheSize()
            ) }
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
}

