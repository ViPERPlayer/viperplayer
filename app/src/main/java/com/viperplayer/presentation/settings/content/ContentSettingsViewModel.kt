package com.viperplayer.presentation.settings.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContentSettingsUiState(
    val showExplicitContent: Boolean = true,
    val offlineMode: Boolean = false,
    val autoDownloadOnLike: Boolean = false
)

@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentSettingsUiState())
    val uiState: StateFlow<ContentSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.showExplicitContent.collect { enabled ->
                _uiState.update { it.copy(showExplicitContent = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.offlineMode.collect { enabled ->
                _uiState.update { it.copy(offlineMode = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoDownloadOnLike.collect { enabled ->
                _uiState.update { it.copy(autoDownloadOnLike = enabled) }
            }
        }
    }

    fun setShowExplicitContent(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowExplicitContent(enabled)
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineMode(enabled)
        }
    }

    fun setAutoDownloadOnLike(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadOnLike(enabled)
        }
    }
}
