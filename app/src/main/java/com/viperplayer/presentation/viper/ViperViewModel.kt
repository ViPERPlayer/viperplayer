package com.viperplayer.presentation.viper

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * UI State for Search screen.
 */
data class ViperUiState(
    val enabled: Boolean = false,
)

/**
 * ViewModel for Search screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ViperViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ViperUiState())
    val uiState: StateFlow<ViperUiState> = _uiState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
    }
}

