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
fun SpeakerOptimizationEffect(
    viewModel: SpeakerOptimizationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Effect(
        icon = painterResource(R.drawable.ic_speaker),
        title = stringResource(R.string.speaker_optimization),
        checked = state.enabled,
        onCheckedChange = viewModel::setEnabled
    )
}