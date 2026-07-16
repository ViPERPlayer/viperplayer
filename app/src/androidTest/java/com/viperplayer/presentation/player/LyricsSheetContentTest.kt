package com.viperplayer.presentation.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.LyricsWord
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

    @Test
    fun syncedLyrics_renderActiveAndUpcomingLines() {
        // At position 0 the first line is active; both timed lines should be present in the list.
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
        composeRule.onNodeWithText("世界").assertIsDisplayed()
    }

    @Test
    fun tappingSyncedLine_seeksToItsStart() {
        val wordLyrics = Lyrics(
            synced = true,
            lines = listOf(
                LyricsLine(startMs = 0, text = "one two", words = listOf(
                    LyricsWord(startMs = 0, text = "one "),
                    LyricsWord(startMs = 500, text = "two"),
                )),
                LyricsLine(startMs = 4000, text = "later line"),
            ),
            plainText = null,
        )
        var seekedTo = -1L
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = wordLyrics,
                position = 100,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = false,
                romanizedLines = null,
                onToggleTranslation = {},
                onToggleRomanization = {},
                onSeek = { seekedTo = it },
            )
        }

        composeRule.onNodeWithText("later line").performClick()
        assertEquals(4000L, seekedTo)
    }

    @Test
    fun noLyrics_showsEmptyState() {
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = null,
                position = 0,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = false,
                romanizedLines = null,
                onToggleTranslation = {},
                onToggleRomanization = {},
                onSeek = {},
                loading = false,
            )
        }

        composeRule.onNodeWithText("No lyrics available for this track").assertIsDisplayed()
        composeRule.onNodeWithTag(LYRICS_LOADING_TAG).assertDoesNotExist()
    }

    @Test
    fun loading_showsShimmerNotEmptyState() {
        // The shimmer runs an infinite animation; pausing the clock keeps the test from waiting on it.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            LyricsSheetContent(
                lyrics = null,
                position = 0,
                translationEnabled = false,
                translatedLines = null,
                romanizationEnabled = false,
                romanizedLines = null,
                onToggleTranslation = {},
                onToggleRomanization = {},
                onSeek = {},
                loading = true,
            )
        }

        // While loading we show the shimmer placeholder, not the "no lyrics" empty text.
        composeRule.onNodeWithTag(LYRICS_LOADING_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("No lyrics available for this track").assertDoesNotExist()
    }
}
