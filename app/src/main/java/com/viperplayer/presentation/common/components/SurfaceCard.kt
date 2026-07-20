package com.viperplayer.presentation.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/** Corner radius shared by every card in the visual language. */
val SurfaceCardCornerRadius = 20.dp

/**
 * Leading-icon slot size for a [SurfaceCardRow]. Chosen so 16dp horizontal padding + this + the 14dp
 * icon→text gap equals the default 52dp [InsetDivider] inset, keeping the divider flush with the text.
 */
private val RowLeadingSize = 22.dp

/**
 * The base container for every grouped card in the redesign — hub cards, list cards, settings cards.
 * A flat surfaceContainerLow panel with a 20dp radius and no elevation; children (rows, plus
 * [InsetDivider]s between them) are stacked in a [Column].
 *
 * [contentPadding] defaults to none so callers can put edge-to-edge rows (whose own padding provides
 * the touch inset) directly inside; pass a value for free-form card content.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SurfaceCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * A single settings/hub row for use inside a [SurfaceCard]: a leading icon (primary-tinted), a
 * title with optional subtitle, and a trailing slot (chevron, [CountBadge], activity dot, switch…).
 *
 * Vertical padding is 13dp — the mockup's row rhythm — and the leading icon + 16dp horizontal
 * padding line up with the default 52dp [InsetDivider] inset. When [trailing] is left null a
 * "navigates onward" chevron is shown only for clickable rows (`onClick != null`); pass an explicit
 * [trailing] (a [CountBadge], [ActivityDot], switch…) to override it, or `{}` for no trailing slot.
 */
@Composable
fun SurfaceCardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RowLeadingSize),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun SurfaceCardPreview() {
    ViPERPlayerTheme(darkTheme = true) {
        SurfaceCard(modifier = Modifier.padding(16.dp)) {
            SurfaceCardRow(
                title = "Friend activity",
                subtitle = "Maya and 2 others listening now",
                leadingIcon = Icons.Rounded.Group,
                trailing = { ActivityDot() },
            )
            InsetDivider()
            SurfaceCardRow(
                title = "Plugins",
                subtitle = "4 connected · 1 update available",
                leadingIcon = Icons.Rounded.Extension,
                trailing = { CountBadge(count = 3) },
            )
            InsetDivider()
            SurfaceCardRow(
                title = "History",
                leadingIcon = Icons.Rounded.History,
                onClick = {},
            )
        }
    }
}
