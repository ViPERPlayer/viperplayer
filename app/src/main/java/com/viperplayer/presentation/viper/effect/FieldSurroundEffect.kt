package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.FieldSurroundState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun FieldSurroundEffect(
    state: FieldSurroundState,
    onEnabledChange: (Boolean) -> Unit,
    onSurroundStrengthChange: (Int) -> Unit,
    onSurroundStrengthReset: () -> Unit,
    onMidImageStrengthChange: (Int) -> Unit,
    onMidImageStrengthReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_field_surround),
        title = stringResource(R.string.field_surround),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.field_surround_surround_strength),
            summary = (state.surroundStrength + 1).toString(),
            value = state.surroundStrength,
            onValueChange = onSurroundStrengthChange,
            onValueReset = onSurroundStrengthReset,
            valueRange = 0..8
        )
        ValueSlider(
            title = stringResource(R.string.field_surround_mid_image_strength),
            summary = (state.midImageStrength + 1).toString(),
            value = state.midImageStrength,
            onValueChange = onMidImageStrengthChange,
            onValueReset = onMidImageStrengthReset,
            valueRange = 0..10
        )
    }
}