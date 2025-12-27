package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TubeSimulator6N1JUiState(
    val enabled: Boolean = false,
)

@HiltViewModel
class TubeSimulator6N1JViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(TubeSimulator6N1JUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }
}
