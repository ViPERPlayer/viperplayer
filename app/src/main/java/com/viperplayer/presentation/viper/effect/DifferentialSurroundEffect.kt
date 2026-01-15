package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.DifferentialSurroundState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun DifferentialSurroundEffect(
    state: DifferentialSurroundState,
    onEnabledChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onDelayReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_diff_surround),
        title = stringResource(R.string.differential_surround),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.differential_surround_delay),
            summary = (state.delay + 1).toString(),
            summaryUnit = "ms",
            value = state.delay,
            onValueChange = onDelayChange,
            onValueReset = onDelayReset,
            valueRange = 0..19
        )
    }
}