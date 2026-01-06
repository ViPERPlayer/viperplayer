package com.viperplayer.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.CacheRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val replayGainEnabled: Boolean = true,
    val replayGainPreampDb: Float = 0f,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cacheSizeBytes: Long = 0L,
    val isClearingCache: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Collect each setting independently for better type safety and maintainability
        viewModelScope.launch {
            settingsRepository.replayGainEnabled.collect { enabled ->
                _uiState.update { it.copy(replayGainEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainPreampDb.collect { preampDb ->
                _uiState.update { it.copy(replayGainPreampDb = preampDb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { theme ->
                _uiState.update { it.copy(themeMode = theme) }
            }
        }
        viewModelScope.launch {
            // Combine song and image cache sizes
            kotlinx.coroutines.flow.combine(
                settingsRepository.maxSongCacheSize,
                settingsRepository.maxImageCacheSize
            ) { songSize, imageSize ->
                songSize + imageSize
            }.collect { totalSize ->
                _uiState.update { it.copy(cacheSizeBytes = totalSize) }
            }
        }
    }
    
    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReplayGainEnabled(enabled)
        }
    }
    
    fun setReplayGainPreampDb(preampDb: Float) {
        viewModelScope.launch {
            // Clamp preamp to reasonable range: -12 dB to +6 dB
            val clampedPreamp = preampDb.coerceIn(-12f, 6f)
            settingsRepository.setReplayGainPreampDb(clampedPreamp)
        }
    }
    
    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepository.setAudioQuality(quality)
        }
    }
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingCache = true)
            try {
                // Clear both song and image cache
                cacheRepository.clearSongCache()
                cacheRepository.clearImageCache()
                // Refresh cache size after clearing
                val newSongSize = settingsRepository.maxSongCacheSize.first()
                val newImageSize = settingsRepository.maxImageCacheSize.first()
                _uiState.value = _uiState.value.copy(
                    isClearingCache = false,
                    cacheSizeBytes = newSongSize + newImageSize
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isClearingCache = false)
            }
        }
    }
}

