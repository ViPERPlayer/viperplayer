package com.viperplayer.presentation.viper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.ViperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for ViPER screen.
 */
data class ViperUiState(
    val enabled: Boolean = false,
)

/**
 * ViewModel for ViPER screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ViperViewModel @Inject constructor(
    private val viperRepository: ViperRepository
) : ViewModel() {
    val uiState: StateFlow<ViperUiState> = viperRepository.enabled
        .map { enabled -> ViperUiState(enabled = enabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViperUiState(enabled = false)
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.setEnabled(enabled)
        }
    }
}

