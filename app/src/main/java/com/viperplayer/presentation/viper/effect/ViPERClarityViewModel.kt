package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ViPERClarityUiState(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val gain: Int = 0,
)

@HiltViewModel
class ViPERClarityViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(ViPERClarityUiState())
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

    fun setGain(gain: Int) {
        _state.update { it.copy(gain = gain) }
    }

    fun resetGain() {
        // TODO
    }
}
