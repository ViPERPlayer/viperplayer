package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.ReverberationState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

/**
 * Reverberation (Freeverb-style room reverb). All parameters are normalised
 * 0-100 % values mapped to the native model's [0,1] range.
 */
@Composable
fun ReverberationEffect(
    state: ReverberationState,
    onEnabledChange: (Boolean) -> Unit,
    onRoomSizeChange: (Int) -> Unit,
    onRoomSizeReset: () -> Unit,
    onWidthChange: (Int) -> Unit,
    onWidthReset: () -> Unit,
    onDampChange: (Int) -> Unit,
    onDampReset: () -> Unit,
    onWetChange: (Int) -> Unit,
    onWetReset: () -> Unit,
    onDryChange: (Int) -> Unit,
    onDryReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_field_surround),
        title = stringResource(R.string.reverberation),
        summary = stringResource(R.string.viper_summary_reverb, state.roomSize, state.wet),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.reverb_room_size),
            summaryUnit = "%",
            value = state.roomSize,
            onValueChange = onRoomSizeChange,
            onValueReset = onRoomSizeReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.reverb_width),
            summaryUnit = "%",
            value = state.width,
            onValueChange = onWidthChange,
            onValueReset = onWidthReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.reverb_damping),
            summaryUnit = "%",
            value = state.damp,
            onValueChange = onDampChange,
            onValueReset = onDampReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.reverb_wet),
            summaryUnit = "%",
            value = state.wet,
            onValueChange = onWetChange,
            onValueReset = onWetReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.reverb_dry),
            summaryUnit = "%",
            value = state.dry,
            onValueChange = onDryChange,
            onValueReset = onDryReset,
            valueRange = 0..100
        )
    }
}
