package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SpeakerOptimizationUiState(
    val enabled: Boolean = false,
    val delay: Int = 0,
)

@HiltViewModel
class SpeakerOptimizationViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(SpeakerOptimizationUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }
}
