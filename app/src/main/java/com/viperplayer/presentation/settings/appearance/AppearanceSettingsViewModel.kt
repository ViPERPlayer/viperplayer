package com.viperplayer.presentation.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceSettingsUiState(
    val dynamicThemeMode: DynamicThemeMode = DynamicThemeMode.DYNAMIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    /** User-picked accent seed (packed ARGB), or `null` for the brand default. */
    val accentColor: Int? = null
)

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceSettingsUiState())
    val uiState: StateFlow<AppearanceSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.dynamicThemeMode.collect { mode ->
                _uiState.update { it.copy(dynamicThemeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pureBlack.collect { enabled ->
                _uiState.update { it.copy(pureBlack = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.accentColor.collect { argb ->
                _uiState.update { it.copy(accentColor = argb) }
            }
        }
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        viewModelScope.launch {
            settingsRepository.setDynamicThemeMode(mode)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlack(enabled)
        }
    }

    /** Persist a custom accent seed; pass `null` to clear it and fall back to the brand default. */
    fun setAccentColor(argb: Int?) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(argb)
        }
    }
}

