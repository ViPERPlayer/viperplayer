package com.viperplayer.presentation.viper.effect

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValuePicker
import com.viperplayer.presentation.viper.component.ValueSlider

private val gainSummaryValues = arrayOf(
    "3.5",
    "6.0",
    "8.0",
    "10.0",
    "11.0",
    "12.0",
    "13.0",
    "14.0",
    "14.8",
    "15.6",
    "16.3",
    "17.0",
)

@Composable
fun ViPERBassEffect(
    viewModel: ViPERBassViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Effect(
        icon = painterResource(R.drawable.ic_bass),
        title = stringResource(R.string.viper_bass),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValuePicker(
            title = stringResource(R.string.bass_mode),
            values = arrayOf(
                stringResource(R.string.natural_bass),
                stringResource(R.string.pure_bass_plus),
                stringResource(R.string.subwoofer),
            ),
            selectedIndex = state.mode,
            onSelectedIndexChange = viewModel::setMode,
            onSelectedIndexReset = viewModel::resetMode
        )
        Spacer(modifier = Modifier.height(8.dp))
        ValueSlider(
            title = stringResource(R.string.bass_frequency),
            summary = state.frequency.toString(),
            summaryUnit = "Hz",
            value = state.frequency,
            onValueChange = viewModel::setFrequency,
            onValueReset = viewModel::resetFrequency,
            valueRange = 15..150
        )
        ValueSlider(
            title = stringResource(R.string.bass_gain),
            summary = gainSummaryValues[state.gain - 1],
            summaryUnit = "dB",
            value = state.gain,
            onValueChange = viewModel::setGain,
            onValueReset = viewModel::resetGain,
            valueRange = 1..12
        )
    }
}