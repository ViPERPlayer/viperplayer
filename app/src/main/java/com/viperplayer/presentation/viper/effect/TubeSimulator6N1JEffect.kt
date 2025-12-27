package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.viper.component.Effect

@Composable
fun TubeSimulator6N1JEffect(
    viewModel: TubeSimulator6N1JViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_tube),
        title = stringResource(R.string.tube_simulator_6n1j),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    )
}