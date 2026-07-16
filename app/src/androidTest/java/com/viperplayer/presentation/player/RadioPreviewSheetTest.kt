package com.viperplayer.presentation.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the stateless [RadioPreviewSheet] (issue #7): it SHOWS the built radio
 * playlist rather than auto-playing, and only starts playback (via onPlay) when the user taps a row
 * or "Play all" — never on its own. Also covers the loading and empty states.
 */
class RadioPreviewSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun song(id: String, title: String) =
        Song(id = MediaId("local", id), title = title, durationMs = 1000L)

    private val seed = song("seed", "Seed Song")
    private val ready = RadioPreview.Ready(
        seed = seed,
        songs = listOf(seed, song("a", "Radio A"), song("b", "Radio B")),
    )

    @Test
    fun ready_showsEachRadioSong() {
        composeRule.setContent {
            RadioPreviewSheet(preview = ready, onPlay = {})
        }

        composeRule.onNodeWithText("Seed Song").assertIsDisplayed()
        composeRule.onNodeWithText("Radio A").assertIsDisplayed()
        composeRule.onNodeWithText("Radio B").assertIsDisplayed()
    }

    @Test
    fun ready_doesNotPlayUntilUserActs() {
        var played: Int? = null
        composeRule.setContent {
            RadioPreviewSheet(preview = ready, onPlay = { played = it })
        }
        composeRule.waitForIdle()

        // Merely showing the built radio must not start playback (the whole point of issue #7).
        assertNull(played)
    }

    @Test
    fun tappingRow_playsFromThatIndex() {
        var played: Int? = null
        composeRule.setContent {
            RadioPreviewSheet(preview = ready, onPlay = { played = it })
        }

        composeRule.onNodeWithTag("radioRow_2").performClick()

        assertEquals(2, played)
    }

    @Test
    fun playAll_startsFromZero() {
        var played: Int? = null
        composeRule.setContent {
            RadioPreviewSheet(preview = ready, onPlay = { played = it })
        }

        composeRule.onNodeWithTag("radioPlayAll").performClick()

        assertEquals(0, played)
    }

    @Test
    fun loading_showsSpinner_noPlay() {
        var played: Int? = null
        composeRule.setContent {
            RadioPreviewSheet(preview = RadioPreview.Loading(seed), onPlay = { played = it })
        }

        composeRule.onNodeWithTag("radioLoading").assertIsDisplayed()
        assertNull(played)
    }

    @Test
    fun ready_empty_showsEmptyState() {
        composeRule.setContent {
            RadioPreviewSheet(preview = RadioPreview.Ready(seed, emptyList()), onPlay = {})
        }

        composeRule.onNodeWithText("No radio songs found").assertIsDisplayed()
    }
}
