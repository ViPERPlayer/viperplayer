package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.TagDetails
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the tag-details content: present fields render as labeled rows, absent fields
 * are omitted, and the Loading / Empty states show their respective placeholders. Drives the pure
 * [TagDetailsScreenContent] directly with a state object (no ViewModel), matching the project's other
 * screen tests.
 */
class TagDetailsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(state: TagDetailsUiState) {
        composeRule.setContent {
            TagDetailsScreenContent(
                rootPadding = PaddingValues(0.dp),
                state = state,
                onNavigateBack = {},
            )
        }
    }

    @Test
    fun loaded_rendersPresentFields() {
        content(
            TagDetailsUiState.Loaded(
                title = "Money",
                details = TagDetails(
                    title = "Money",
                    artists = listOf("Pink Floyd"),
                    album = "The Dark Side of the Moon",
                    genre = "Progressive Rock",
                    trackNumber = 6,
                    trackTotal = 10,
                    container = "FLAC",
                    sampleRate = 44100,
                    bitsPerSample = 16,
                ),
            ),
        )

        composeRule.onNodeWithTag("tagDetailsList").assertIsDisplayed()
        composeRule.onNodeWithText("Money").assertIsDisplayed()
        composeRule.onNodeWithText("Pink Floyd").assertIsDisplayed()
        composeRule.onNodeWithText("The Dark Side of the Moon").assertIsDisplayed()
        composeRule.onNodeWithText("Progressive Rock").assertIsDisplayed()
        // Track shown as "n / total".
        composeRule.onNodeWithText("6 / 10").assertIsDisplayed()
        composeRule.onNodeWithText("FLAC").assertIsDisplayed()
        composeRule.onNodeWithText("44100 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("16-bit").assertIsDisplayed()
    }

    @Test
    fun loaded_omitsAbsentFields() {
        content(
            TagDetailsUiState.Loaded(
                title = "Only Title",
                details = TagDetails(title = "Only Title"),
            ),
        )

        // The single present field is shown...
        composeRule.onNodeWithText("Only Title").assertIsDisplayed()
        // ...and labels for absent fields do not appear.
        composeRule.onNodeWithText("Album").assertDoesNotExist()
        composeRule.onNodeWithText("Composer").assertDoesNotExist()
        composeRule.onNodeWithText("Disc").assertDoesNotExist()
        composeRule.onNodeWithText("Bitrate").assertDoesNotExist()
    }

    @Test
    fun empty_showsPlaceholder() {
        content(TagDetailsUiState.Empty(title = "Broken"))
        composeRule.onNodeWithTag("tagDetailsEmpty").assertIsDisplayed()
    }

    @Test
    fun loading_showsSpinner() {
        content(TagDetailsUiState.Loading(title = "Loading Track"))
        composeRule.onNodeWithTag("tagDetailsLoading").assertIsDisplayed()
    }
}
