package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SpectrumExtensionUiState(
    val enabled: Boolean = false,
    val strength: Int = 0,
)

@HiltViewModel
class SpectrumExtensionViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(SpectrumExtensionUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }

    fun setStrength(strength: Int) {
        _state.update { it.copy(strength = strength) }
    }

    fun resetStrength() {
        // TODO
    }
}
