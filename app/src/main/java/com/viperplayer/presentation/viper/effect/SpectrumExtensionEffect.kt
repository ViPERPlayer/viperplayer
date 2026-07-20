package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.SpectrumExtensionState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun SpectrumExtensionEffect(
    state: SpectrumExtensionState,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onStrengthReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_spectrum),
        title = stringResource(R.string.spectrum_extension),
        summary = stringResource(R.string.viper_summary_spectrum, state.strength),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.spectrum_extension_strength),
            summaryUnit = "%",
            value = state.strength,
            onValueChange = onStrengthChange,
            onValueReset = onStrengthReset,
            valueRange = 0..100
        )
    }
}