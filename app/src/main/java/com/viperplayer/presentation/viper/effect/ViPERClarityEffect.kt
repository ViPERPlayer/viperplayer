package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.ViperClarityState
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
    state: ViperClarityState,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (Int) -> Unit,
    onModeReset: () -> Unit,
    onGainChange: (Int) -> Unit,
    onGainReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_clarity),
        title = stringResource(R.string.viper_clarity),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValuePicker(
            title = stringResource(R.string.clarity_mode),
            values = arrayOf(
                stringResource(R.string.natural),
                stringResource(R.string.ozone_plus),
                stringResource(R.string.xhifi),
            ),
            selectedIndex = state.mode,
            onSelectedIndexChange = onModeChange,
            onSelectedIndexReset = onModeReset
        )
        ValueSlider(
            title = stringResource(R.string.clarity_gain),
            summary = gainSummaryValues[state.gain],
            summaryUnit = "dB",
            value = state.gain,
            onValueChange = onGainChange,
            onValueReset = onGainReset,
            valueRange = gainSummaryValues.indices
        )
    }
}