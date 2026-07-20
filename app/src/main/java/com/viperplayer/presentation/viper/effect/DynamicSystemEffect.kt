package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.DynamicSystemState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValuePicker
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun DynamicSystemEffect(
    state: DynamicSystemState,
    onEnabledChange: (Boolean) -> Unit,
    onDeviceTypeChange: (DynamicSystemDeviceType) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onBassStrengthReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_dynamic),
        title = stringResource(R.string.dynamic_system),
        summary = stringResource(
            R.string.viper_summary_dynamic,
            state.deviceType.name,
            state.dynamicBassStrength,
        ),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValuePicker(
            title = stringResource(R.string.dynamic_system_device_type),
            values = DynamicSystemDeviceType.entries.map { it.name }.toTypedArray(),
            selectedIndex = state.deviceType.ordinal,
            onSelectedIndexChange = { onDeviceTypeChange(DynamicSystemDeviceType.entries[it]) },
            onSelectedIndexReset = {}
        )
        ValueSlider(
            title = stringResource(R.string.dynamic_system_dynamic_bass_strength),
            summaryUnit = "%",
            value = state.dynamicBassStrength,
            onValueChange = onBassStrengthChange,
            onValueReset = onBassStrengthReset,
            valueRange = 0..100
        )
    }
}