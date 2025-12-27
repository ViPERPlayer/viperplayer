package com.viperplayer.presentation.viper.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SwitchBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = if (checked)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    ) {
        Row(
            modifier = Modifier
                .height(71.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    title()
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors().copy(
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Preview
@Composable
fun SwitchBarPreview() {
    SwitchBar(
        title = {
            Text("Enabled")
                },
        checked = true,
        onCheckedChange = {},
    )
}

@Preview
@Composable
fun SwitchBarDisabledPreview() {
    SwitchBar(
        title = {
            Text("Disabled")
                },
        checked = false,
        onCheckedChange = {},
    )
}