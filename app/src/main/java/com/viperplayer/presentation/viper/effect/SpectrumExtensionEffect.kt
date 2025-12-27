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

@Composable
fun SpectrumExtensionEffect(
    viewModel: SpectrumExtensionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_spectrum),
        title = stringResource(R.string.spectrum_extension),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValueSlider(
            title = stringResource(R.string.spectrum_extension_strength),
            summaryUnit = "%",
            value = state.strength,
            onValueChange = viewModel::setStrength,
            onValueReset = viewModel::resetStrength,
            valueRange = 0..100
        )
    }
}