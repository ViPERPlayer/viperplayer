package com.viperplayer.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.presentation.theme.Spacing
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/** Fully-rounded pill radius shared by every pill/chip in the visual language. */
private val PillShape = CircleShape

/** Minimum size on both axes so every pill clears the 44dp touch-target floor, even a 1-glyph pill. */
private val PillMinTouchTarget = 44.dp

/** Leading-icon size inside a pill. */
private val PillIconSize = 18.dp

/**
 * The primary call-to-action pill: filled with the primary colour, fully rounded, at least 44dp tall
 * (the "Join" / "Sign in" buttons in the mockups).
 */
@Composable
fun FilledPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = PillMinTouchTarget, minHeight = PillMinTouchTarget),
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        PillContent(text = text, leadingIcon = leadingIcon)
    }
}

/**
 * A tonal chip: secondaryContainer fill with onSecondaryContainer content (the "Manage account" and
 * selected filter chips in the mockups, when used purely as an action).
 */
@Composable
fun TonalChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = PillMinTouchTarget, minHeight = PillMinTouchTarget),
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        PillContent(text = text, leadingIcon = leadingIcon)
    }
}

/**
 * A secondary-action pill: outlined with the outline colour, primary text (the "Dismiss" button and
 * unselected library filter chips in the mockups).
 */
@Composable
fun OutlinedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = PillMinTouchTarget, minHeight = PillMinTouchTarget),
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        PillContent(text = text, leadingIcon = leadingIcon)
    }
}

/**
 * A selectable filter chip. When [selected] it fills with secondaryContainer and shows a leading
 * check (the "Songs" chip in the Library mockup); when unselected it's an outlined pill. Any
 * caller-supplied [leadingIcon] shows only in the unselected state — the check replaces it when
 * selected, matching the mockup.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    if (selected) {
        containerColor = MaterialTheme.colorScheme.secondaryContainer
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        border = null
    } else {
        containerColor = Color.Transparent
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = PillMinTouchTarget, minHeight = PillMinTouchTarget),
        enabled = enabled,
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = if (selected) Icons.Rounded.Check else leadingIcon
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(PillIconSize),
                )
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/** Shared label + optional leading icon layout for the button-backed pills. */
@Composable
private fun PillContent(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(PillIconSize),
        )
    }
    Text(
        text = text,
        modifier = Modifier.padding(start = if (leadingIcon != null) Spacing.sm else 0.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun PillsPreview() {
    ViPERPlayerTheme(darkTheme = true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            FilledPill(text = "Join", onClick = {}, leadingIcon = Icons.Rounded.GraphicEq)
            TonalChip(text = "Invite", onClick = {}, leadingIcon = Icons.Rounded.PersonAdd)
            OutlinedPill(text = "Dismiss", onClick = {})
            SelectableChip(text = "Songs", selected = true, onClick = {})
            SelectableChip(text = "Albums", selected = false, onClick = {})
        }
    }
}
