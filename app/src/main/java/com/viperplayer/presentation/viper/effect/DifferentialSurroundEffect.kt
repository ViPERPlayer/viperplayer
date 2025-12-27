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
fun DifferentialSurroundEffect(
    viewModel: DifferentialSurroundViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_diff_surround),
        title = stringResource(R.string.differential_surround),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValueSlider(
            title = stringResource(R.string.differential_surround_delay),
            summary = (state.delay + 1).toString(),
            summaryUnit = "ms",
            value = state.delay,
            onValueChange = viewModel::setDelay,
            onValueReset = viewModel::resetDelay,
            valueRange = 0..19
        )
    }
}