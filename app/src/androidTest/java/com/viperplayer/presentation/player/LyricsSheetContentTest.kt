package com.viperplayer.presentation.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the stateless lyrics body, focused on the romanize toggle: the romanized text
 * shows beneath each original line when enabled, and tapping the "Romanize lyrics" button forwards
 * the toggle event.
 */
class LyricsSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lyrics = Lyrics(
        synced = true,
        lines = listOf(
            LyricsLine(startMs = 0, text = "こんにちは"),
            LyricsLine(startMs = 1000, text = "世界"),
        ),
        plainText = null,
    )

    @Test
    fun romanizedText_shownBeneathLinesWhenEnabled() {
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = lyrics,
                position = 0,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = true,
                romanizedLines = listOf("konnichiwa", "sekai"),
                onToggleTranslation = {},
                onToggleRomanization = {},
                onSeek = {},
            )
        }

        // Both the original and the romanized text are rendered.
        composeRule.onNodeWithText("こんにちは").assertIsDisplayed()
        composeRule.onNodeWithText("konnichiwa").assertIsDisplayed()
        composeRule.onNodeWithText("sekai").assertIsDisplayed()
    }

    @Test
    fun romanizedText_hiddenWhenNull() {
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = lyrics,
                position = 0,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = false,
                romanizedLines = null,
                onToggleTranslation = {},
                onToggleRomanization = {},
                onSeek = {},
            )
        }

        composeRule.onNodeWithText("こんにちは").assertIsDisplayed()
        composeRule.onNodeWithText("konnichiwa").assertDoesNotExist()
    }

    @Test
    fun tappingRomanizeButton_firesToggle() {
        var toggles = 0
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = lyrics,
                position = 0,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = false,
                romanizedLines = null,
                onToggleTranslation = {},
                onToggleRomanization = { toggles++ },
                onSeek = {},
            )
        }

        composeRule.onNodeWithContentDescription("Romanize lyrics").performClick()

        assertEquals(1, toggles)
    }
}
