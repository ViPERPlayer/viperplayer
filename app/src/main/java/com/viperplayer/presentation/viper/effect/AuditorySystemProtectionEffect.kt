package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.AuditorySystemProtectionState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun AuditorySystemProtectionEffect(
    state: AuditorySystemProtectionState,
    onEnabledChange: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    onLevelReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_protection),
        title = stringResource(R.string.auditory_system_protection),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.binaural_level),
            value = state.level,
            onValueChange = onLevelChange,
            onValueReset = onLevelReset,
            valueRange = 1..3,
        )
    }
}