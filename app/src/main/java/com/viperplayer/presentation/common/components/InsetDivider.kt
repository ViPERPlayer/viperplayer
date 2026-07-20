package com.viperplayer.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/** Divider colour used across the visual language: outlineVariant softened to ~55% alpha. */
private const val DividerAlpha = 0.55f

/**
 * A 1dp hairline between rows inside a [SurfaceCard], drawn in outlineVariant at ~55% alpha and
 * inset from the leading edge so it aligns under the row's text rather than its icon/artwork.
 *
 * Use the default [startInset] of 52dp for icon-led rows; pass 72dp for rows that lead with artwork.
 */
@Composable
fun InsetDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 52.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = DividerAlpha)),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1D1B20)
@Composable
private fun InsetDividerPreview() {
    ViPERPlayerTheme(darkTheme = true) {
        Column {
            InsetDivider()
            InsetDivider(startInset = 72.dp)
            InsetDivider(startInset = 0.dp)
        }
    }
}
