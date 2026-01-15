package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.AnalogXState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun AnalogXEffect(
    state: AnalogXState,
    onEnabledChange: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    onLevelReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_analogx),
        title = stringResource(R.string.analogx),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.analogx_level),
            summary = (state.level + 1).toString(),
            value = state.level,
            onValueChange = onLevelChange,
            onValueReset = onLevelReset,
            valueRange = 0..2
        )
    }
}