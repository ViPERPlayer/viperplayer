package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FieldSurroundUiState(
    val enabled: Boolean = false,
    val surroundStrength: Int = 0,
    val midImageStrength: Int = 0,
)

@HiltViewModel
class FieldSurroundViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(FieldSurroundUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }

    fun setSurroundStrength(surroundStrength: Int) {
        _state.update { it.copy(surroundStrength = surroundStrength) }

    }

    fun resetSurroundStrength() {
        // TODO
    }

    fun setMidImageStrength(midImageStrength: Int) {
        _state.update { it.copy(midImageStrength = midImageStrength) }
    }

    fun resetMidImageStrength() {
        // TODO
    }
}
