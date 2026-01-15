package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.AuditorySystemProtectionState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun AuditorySystemProtectionEffect(
    state: AuditorySystemProtectionState,
    onEnabledChange: (Boolean) -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_protection),
        title = stringResource(R.string.auditory_system_protection),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        var value by remember { mutableIntStateOf(1) }
        ValueSlider(
            title = "Binaural level",
            value = value,
            onValueChange = { value = it },
            onValueReset = { value = 1 },
            valueRange = 1..3,
        )
    }
}