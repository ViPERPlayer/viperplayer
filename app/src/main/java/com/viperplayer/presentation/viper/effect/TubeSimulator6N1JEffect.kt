package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.TubeSimulatorState
import com.viperplayer.presentation.viper.component.Effect

@Composable
fun TubeSimulator6N1JEffect(
    state: TubeSimulatorState,
    onEnabledChange: (Boolean) -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_tube),
        title = stringResource(R.string.tube_simulator_6n1j),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    )
}