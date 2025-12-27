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

private val outputGainSummaryValues = arrayOf(
    "-40.0",
    "-26.0",
    "-20.0",
    "-14.0",
    "-10.5",
    "-8.0",
    "-6.0",
    "-4.4",
    "-3.0",
    "-1.9",
    "-1.0",
    "0.0",
    "0.8",
    "1.6",
    "2.3",
    "2.9",
    "3.5",
    "4.1",
    "4.6",
    "5.1",
    "5.6",
    "6.0"
)

private val thresholdLimitSummaryValues = arrayOf(
    "-10.5",
    "-6.0",
    "-3.0",
    "-1.9",
    "-1.0",
    "0.0"
)

@Composable
fun MasterLimiterEffect(
    viewModel: MasterLimiterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Effect(
        icon = painterResource(R.drawable.ic_master_limiter),
        title = stringResource(R.string.master_limiter),
    ) {
        ValueSlider(
            title = stringResource(R.string.master_limiter_output_gain),
            summary = outputGainSummaryValues[state.outputGain],
            summaryUnit = "dB",
            value = state.outputGain,
            onValueChange = viewModel::setOutputGain,
            onValueReset = viewModel::resetOutputGain,
            valueRange = outputGainSummaryValues.indices
        )
        ValueSlider(
            title = stringResource(R.string.master_limiter_output_pan),
            summary = "${100 - state.outputPan}:${state.outputPan}",
            value = state.outputPan,
            onValueChange = viewModel::setOutputPan,
            onValueReset = viewModel::resetOutputPan,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.master_limiter_threshold_limit),
            value = state.thresholdLimit,
            summary = thresholdLimitSummaryValues[state.thresholdLimit],
            summaryUnit = "dB",
            onValueChange = viewModel::setThresholdLimit,
            onValueReset = viewModel::resetThresholdLimit,
            valueRange = thresholdLimitSummaryValues.indices
        )
    }
}