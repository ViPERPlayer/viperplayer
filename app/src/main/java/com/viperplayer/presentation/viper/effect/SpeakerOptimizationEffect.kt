package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.SpeakerOptimizationState
import com.viperplayer.presentation.viper.component.Effect

@Composable
fun SpeakerOptimizationEffect(
    state: SpeakerOptimizationState,
    onEnabledChange: (Boolean) -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_speaker),
        title = stringResource(R.string.speaker_optimization),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    )
}