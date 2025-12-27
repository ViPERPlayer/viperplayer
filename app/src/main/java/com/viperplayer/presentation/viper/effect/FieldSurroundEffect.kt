package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun FieldSurroundEffect(
    viewModel: FieldSurroundViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Effect(
        icon = painterResource(R.drawable.ic_field_surround),
        title = stringResource(R.string.field_surround),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValueSlider(
            title = stringResource(R.string.field_surround_surround_strength),
            summary = (state.surroundStrength + 1).toString(),
            value = state.surroundStrength,
            onValueChange = viewModel::setSurroundStrength,
            onValueReset = viewModel::resetSurroundStrength,
            valueRange = 0..8
        )
        ValueSlider(
            title = stringResource(R.string.field_surround_mid_image_strength),
            summary = (state.midImageStrength + 1).toString(),
            value = state.midImageStrength,
            onValueChange = viewModel::setMidImageStrength,
            onValueReset = viewModel::resetMidImageStrength,
            valueRange = 0..10
        )
    }
}