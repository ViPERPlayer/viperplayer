package com.viperplayer.presentation.viper.effect

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.domain.model.ViperBassState
import com.viperplayer.presentation.theme.Spacing
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
    state: ViperBassState,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (Int) -> Unit,
    onModeReset: () -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onFrequencyReset: () -> Unit,
    onGainChange: (Int) -> Unit,
    onGainReset: () -> Unit
) {
    val modeNames = arrayOf(
        stringResource(R.string.natural_bass),
        stringResource(R.string.pure_bass_plus),
        stringResource(R.string.subwoofer),
    )
    val gainLabel = gainSummaryValues.getOrElse(state.gain) { "" }
    Effect(
        icon = painterResource(R.drawable.ic_bass),
        title = stringResource(R.string.viper_bass),
        summary = stringResource(
            R.string.viper_summary_bass,
            modeNames.getOrElse(state.mode) { "" },
            state.frequency,
            if (gainLabel.startsWith("-")) gainLabel else "+$gainLabel",
        ),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValuePicker(
            title = stringResource(R.string.bass_mode),
            values = modeNames,
            selectedIndex = state.mode,
            onSelectedIndexChange = onModeChange,
            onSelectedIndexReset = onModeReset
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        ValueSlider(
            title = stringResource(R.string.bass_frequency),
            summary = state.frequency.toString(),
            summaryUnit = "Hz",
            value = state.frequency,
            onValueChange = onFrequencyChange,
            onValueReset = onFrequencyReset,
            valueRange = 15..150
        )
        ValueSlider(
            title = stringResource(R.string.bass_gain),
            summary = gainSummaryValues.getOrElse(state.gain) { "" },
            summaryUnit = "dB",
            value = state.gain,
            onValueChange = onGainChange,
            onValueReset = onGainReset,
            valueRange = 0..11
        )
    }
}