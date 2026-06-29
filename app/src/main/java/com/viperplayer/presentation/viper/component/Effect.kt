package com.viperplayer.presentation.viper.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R

@Composable
fun Effect(
    icon: Painter,
    title: String,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    Effect(
        icon = icon,
        title = title,
        checked = null,
        onCheckedChange = null,
        content = content,
    )
}

@Composable
fun Effect(
    icon: Painter,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Effect(
        icon = icon,
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        content = null,
    )
}

@Composable
fun Effect(
    icon: Painter,
    title: String,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
                .clickable(enabled = (content != null) || (checked != null && onCheckedChange != null)) {
                    if (content != null) {
                        expanded = !expanded
                    } else if (checked != null && onCheckedChange != null) {
                        onCheckedChange.invoke(!checked)
                    }
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                letterSpacing = 0.5.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (content != null) {
                // Expand affordance: chevron rotates 0 -> 180 as the section opens.
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(250),
                    label = "effectChevron"
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (checked != null && onCheckedChange != null) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
        if (content != null) {
            AnimatedVisibility(visible = expanded) {
                // Dim the controls when the effect is toggled off, so the section visibly reflects
                // its enabled state without collapsing.
                val contentAlpha by animateFloatAsState(
                    targetValue = if (checked == false) 0.4f else 1f,
                    animationSpec = tween(250),
                    label = "effectContentAlpha"
                )
                Column(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
private fun EffectPreview() {
    Surface {
        Effect(
            icon = painterResource(R.drawable.ic_protection),
            title = "Title",
            checked = true,
            onCheckedChange = {},
        ) {
            Text("Content")
        }
    }
}