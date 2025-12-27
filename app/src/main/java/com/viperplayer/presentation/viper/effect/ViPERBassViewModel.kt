package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ViPERBassUiState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 0,
    val gain: Int = 0,
)

@HiltViewModel
class ViPERBassViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(ViPERBassUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }

    fun setMode(mode: Int) {
        _state.update { it.copy(mode = mode) }
    }

    fun resetMode() {
        // TODO
    }

    fun setFrequency(frequency: Int) {
        _state.update { it.copy(frequency = frequency) }
    }

    fun resetFrequency() {
        // TODO
    }

    fun setGain(gain: Int) {
        _state.update { it.copy(gain = gain) }
    }

    fun resetGain() {
        // TODO
    }
}
