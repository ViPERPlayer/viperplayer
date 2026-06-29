package com.viperplayer.presentation.settings.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerSettingsUiState(
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val historyDuration: HistoryDuration = HistoryDuration.FOREVER,
    val skipSilence: Boolean = false,
    val replayGainEnabled: Boolean = true,
    val replayGainPreampDb: Float = 0f,
    val autoLoadMore: Boolean = false,
    val dspBypass: Boolean = false
)

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        viewModelScope.launch {
            settingsRepository.historyDuration.collect { duration ->
                _uiState.update { it.copy(historyDuration = duration) }
            }
        }
        viewModelScope.launch {
            settingsRepository.skipSilence.collect { enabled ->
                _uiState.update { it.copy(skipSilence = enabled) }
            }
        }
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
            settingsRepository.autoLoadMore.collect { enabled ->
                _uiState.update { it.copy(autoLoadMore = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.dspBypass.collect { enabled ->
                _uiState.update { it.copy(dspBypass = enabled) }
            }
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepository.setAudioQuality(quality)
        }
    }

    fun setHistoryDuration(duration: HistoryDuration) {
        viewModelScope.launch {
            settingsRepository.setHistoryDuration(duration)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSkipSilence(enabled)
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainPreampDb(preampDb: Float) {
        viewModelScope.launch {
            val clampedPreamp = preampDb.coerceIn(-12f, 6f)
            settingsRepository.setReplayGainPreampDb(clampedPreamp)
        }
    }

    fun setAutoLoadMore(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoLoadMore(enabled)
        }
    }

    fun setDspBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDspBypass(enabled)
        }
    }
}

