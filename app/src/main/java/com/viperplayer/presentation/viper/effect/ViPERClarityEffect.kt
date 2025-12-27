package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValuePicker
import com.viperplayer.presentation.viper.component.ValueSlider

private val gainSummaryValues = arrayOf(
    "0.0",
    "3.5",
    "6.0",
    "8.0",
    "10.0",
    "11.0",
    "12.0",
    "13.0",
    "14.0",
    "14.8",
)

@Composable
fun ViPERClarityEffect(
    viewModel: ViPERClarityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Effect(
        icon = painterResource(R.drawable.ic_clarity),
        title = stringResource(R.string.viper_clarity),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValuePicker(
            title = stringResource(R.string.clarity_mode),
            values = arrayOf(
                stringResource(R.string.natural),
                stringResource(R.string.ozone_plus),
                stringResource(R.string.xhifi),
            ),
            selectedIndex = state.mode,
            onSelectedIndexChange = viewModel::setMode,
            onSelectedIndexReset = viewModel::resetMode
        )
        ValueSlider(
            title = stringResource(R.string.clarity_gain),
            summary = gainSummaryValues[state.gain],
            summaryUnit = "dB",
            value = state.gain,
            onValueChange = viewModel::setGain,
            onValueReset = viewModel::resetGain,
            valueRange = gainSummaryValues.indices
        )
    }
}