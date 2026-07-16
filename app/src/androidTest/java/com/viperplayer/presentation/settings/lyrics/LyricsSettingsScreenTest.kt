package com.viperplayer.presentation.settings.lyrics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the Lyrics settings controls. Drives the stateless body directly (no Hilt) and
 * verifies that toggling a switch and picking a style chip fire the correct callbacks/state.
 */
class LyricsSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        settings: LyricsSettings,
        onFontSize: (LyricsFontSize) -> Unit = {},
        onAlignment: (LyricsAlignment) -> Unit = {},
        onAutoScroll: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            LyricsSettingsContent(
                settings = settings,
                rootPadding = PaddingValues(0.dp),
                onNavigateBack = {},
                onFontSize = onFontSize,
                onAlignment = onAlignment,
                onFontWeight = {},
                onHighlightColor = {},
                onActiveLineScale = {},
                onAutoScroll = onAutoScroll,
                onTapToSeek = {},
                onDimInactiveLines = {},
                onShowTranslationByDefault = {},
                onShowRomanizationByDefault = {},
            )
        }
    }

    @Test
    fun rendersStyleAndBehaviorControls() {
        content(LyricsSettings())
        composeRule.onNodeWithText("Font size").assertIsDisplayed()
        composeRule.onNodeWithText("Text alignment").assertIsDisplayed()
        composeRule.onNodeWithText("Auto-scroll").assertIsDisplayed()
    }

    @Test
    fun togglingAutoScroll_firesCallbackWithNewValue() {
        var newValue: Boolean? = null
        // Default autoScroll is on; tapping the switch should request turning it off.
        content(LyricsSettings(autoScroll = true), onAutoScroll = { newValue = it })
        composeRule.onNodeWithText("Auto-scroll").performClick()
        assertEquals(false, newValue)
    }

    @Test
    fun pickingFontSizeChip_firesCallbackWithThatSize() {
        var picked: LyricsFontSize? = null
        content(LyricsSettings(fontSize = LyricsFontSize.MEDIUM), onFontSize = { picked = it })
        composeRule.onNodeWithText("Large").performClick()
        assertEquals(LyricsFontSize.LARGE, picked)
    }

    @Test
    fun pickingAlignmentChip_firesCallbackWithThatAlignment() {
        var picked: LyricsAlignment? = null
        content(LyricsSettings(alignment = LyricsAlignment.LEFT), onAlignment = { picked = it })
        composeRule.onNodeWithText("Center").performClick()
        assertEquals(LyricsAlignment.CENTER, picked)
        assertFalse(picked == LyricsAlignment.LEFT)
    }
}
