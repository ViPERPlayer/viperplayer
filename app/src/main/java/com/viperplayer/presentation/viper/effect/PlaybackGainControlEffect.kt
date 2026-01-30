package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.viperplayer.R
import com.viperplayer.domain.model.DifferentialSurroundState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun PlaybackGainControlEffect(
    state: DifferentialSurroundState,
    onEnabledChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onDelayReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_playback_gain),
        title = "Playback gain control",
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = "Strength",
            summary = state.delay.toString(),
            value = state.delay,
            onValueChange = onDelayChange,
            onValueReset = onDelayReset,
            valueRange = 1..20
        )
        ValueSlider(
            title = "Maximum gain",
            summary = state.delay.toString(),
            summaryUnit = "x",
            value = state.delay,
            onValueChange = onDelayChange,
            onValueReset = onDelayReset,
            valueRange = 1..20
        )
        ValueSlider(
            title = "Output threshold",
            summary = state.delay.toString(),
            summaryUnit = "dB",
            value = state.delay,
            onValueChange = onDelayChange,
            onValueReset = onDelayReset,
            valueRange = 1..20
        )
    }
}