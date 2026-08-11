package com.viperplayer.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import com.viperplayer.presentation.theme.Spacing
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/** Counts above this render as "N+" so the badge never grows wider than two glyphs. */
private const val MaxDisplayedCount = 99

/**
 * A small pill count/alert badge — errorContainer fill with onErrorContainer text — for unread
 * counts and update alerts (the "3" on Shared-with-you, the "2" on the notification bell).
 *
 * Renders nothing when [count] is 0 or negative, so callers can pass a raw count unconditionally.
 */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Spacing.xl, minHeight = Spacing.xl)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MaxDisplayedCount) {
                stringResource(R.string.badge_overflow_count, MaxDisplayedCount)
            } else {
                count.toString()
            },
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The small solid "live activity" dot used at the trailing edge of hub rows and beside friend names
 * (the pink dots in the mockup). Drawn in the tertiary colour so it reads as a subtle presence
 * signal rather than an error/alert.
 */
@Composable
fun ActivityDot(
    modifier: Modifier = Modifier,
    size: Dp = Spacing.sm,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1D1B20)
@Composable
private fun CountBadgePreview() {
    ViPERPlayerTheme(darkTheme = true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            CountBadge(count = 2)
            CountBadge(count = 3)
            CountBadge(count = 128)
            CountBadge(count = 0) // renders nothing
            ActivityDot()
        }
    }
}
