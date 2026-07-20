package com.viperplayer.presentation.common.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/**
 * A grouped-section header in the redesign's visual language: 12sp, weight 700, +1sp letter-spacing,
 * in the primary color. Used above hub/list/settings cards (the "SOCIAL" / "MUSIC" / "SETUP" labels
 * in the mockups).
 *
 * Text is emitted verbatim — callers pass the already-localized (and, if they want the mockup's
 * all-caps look, already-uppercased) string.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun SectionLabelPreview() {
    ViPERPlayerTheme(darkTheme = true) {
        SectionLabel(
            text = "SOCIAL",
            modifier = Modifier.padding(16.dp),
        )
    }
}
