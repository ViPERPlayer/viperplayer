package com.viperplayer.presentation.settings.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.repository.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the ReplayGain settings controls (untagged preamp, gain-mode selector, DRC
 * toggle, post-amp). Verifies the controls render and that picking each fires the right callback.
 */
class ReplayGainControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state() = PlayerSettingsUiState(
        replayGainEnabled = true,
        replayGainMode = ReplayGainMode.TRACK,
        replayGainDrcEnabled = false,
    )

    @Test
    fun showsAllControls() {
        composeRule.setContent {
            ReplayGainControls(
                uiState = state(),
                onPreampChange = {},
                onUntaggedPreampChange = {},
                onModeChange = {},
                onDrcChange = {},
                onPostAmpChange = {},
            )
        }

        composeRule.onNodeWithText("Preamp").assertIsDisplayed()
        composeRule.onNodeWithText("Untagged preamp").assertIsDisplayed()
        composeRule.onNodeWithText("Post-amp").assertIsDisplayed()
        composeRule.onNodeWithText("Track").assertIsDisplayed()
        composeRule.onNodeWithText("Album").assertIsDisplayed()
        composeRule.onNodeWithText("Smart").assertIsDisplayed()
    }

    @Test
    fun pickingSmartMode_firesModeCallback() {
        var picked: ReplayGainMode? = null
        composeRule.setContent {
            ReplayGainControls(
                uiState = state(),
                onPreampChange = {},
                onUntaggedPreampChange = {},
                onModeChange = { picked = it },
                onDrcChange = {},
                onPostAmpChange = {},
            )
        }

        composeRule.onNodeWithText("Smart").performClick()

        assertEquals(ReplayGainMode.SMART, picked)
    }

    @Test
    fun togglingDrc_firesDrcCallback() {
        var drc: Boolean? = null
        composeRule.setContent {
            ReplayGainControls(
                uiState = state(),
                onPreampChange = {},
                onUntaggedPreampChange = {},
                onModeChange = {},
                onDrcChange = { drc = it },
                onPostAmpChange = {},
            )
        }

        composeRule.onNodeWithText("Prevent clipping (DRC)").performClick()

        assertEquals(true, drc)
    }
}
