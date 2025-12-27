package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValuePicker
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun DynamicSystemEffect(
    viewModel: DynamicSystemViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_dynamic),
        title = stringResource(R.string.dynamic_system),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    ) {
        ValuePicker(
            title = stringResource(R.string.dynamic_system_device_type),
            values = DynamicSystemDeviceType.entries.map { it.name }.toTypedArray(),
            selectedIndex = state.deviceType.ordinal,
            onSelectedIndexChange = { viewModel.setDeviceType(DynamicSystemDeviceType.entries[it]) },
            onSelectedIndexReset = {}
        )
        ValueSlider(
            title = stringResource(R.string.dynamic_system_dynamic_bass_strength),
            summaryUnit = "%",
            value = state.dynamicBassStrength,
            onValueChange = viewModel::setDynamicBassStrength,
            onValueReset = viewModel::resetDynamicBassStrength,
            valueRange = 0..100
        )
    }
}