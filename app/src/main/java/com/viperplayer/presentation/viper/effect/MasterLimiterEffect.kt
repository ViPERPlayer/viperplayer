package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.MasterLimiterState
import com.viperplayer.domain.model.ViperSteppedValues
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider
import kotlin.math.roundToInt

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
    state: MasterLimiterState,
    onOutputGainChange: (Int) -> Unit,
    onOutputGainReset: () -> Unit,
    onOutputPanChange: (Float) -> Unit,
    onOutputPanReset: () -> Unit,
    onThresholdLimitChange: (Int) -> Unit,
    onThresholdLimitReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_master_limiter),
        title = stringResource(R.string.master_limiter),
    ) {
        ValueSlider(
            title = stringResource(R.string.master_limiter_output_gain),
            summary = outputGainSummaryValues.getOrElse(state.outputGain) { "" },
            summaryUnit = "dB",
            value = state.outputGain,
            onValueChange = onOutputGainChange,
            onValueReset = onOutputGainReset,
            valueRange = ViperSteppedValues.outputGainValues.indices
        )
        ValueSlider(
            title = stringResource(R.string.master_limiter_output_pan),
            summary = "${((1f - state.outputPan) * 50f).roundToInt()}:${((1f + state.outputPan) * 50f).roundToInt()}",
            value = state.outputPan,
            onValueChange = onOutputPanChange,
            onValueReset = onOutputPanReset,
            valueRange = -1f..1f
        )
        ValueSlider(
            title = stringResource(R.string.master_limiter_threshold_limit),
            value = state.thresholdLimit,
            summary = thresholdLimitSummaryValues.getOrElse(state.thresholdLimit) { "" },
            summaryUnit = "dB",
            onValueChange = onThresholdLimitChange,
            onValueReset = onThresholdLimitReset,
            valueRange = ViperSteppedValues.thresholdLimitValues.indices
        )
    }
}