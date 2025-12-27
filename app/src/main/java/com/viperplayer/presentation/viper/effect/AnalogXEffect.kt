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
fun AnalogXEffect(
    viewModel: AnalogXViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_analogx),
        title = stringResource(R.string.analogx),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValueSlider(
            title = stringResource(R.string.analogx_level),
            summary = (state.level + 1).toString(),
            value = state.level,
            onValueChange = viewModel::setLevel,
            onValueReset = viewModel::resetLevel,
            valueRange = 0..2
        )
    }
}