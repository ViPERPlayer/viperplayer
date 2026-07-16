package com.viperplayer.presentation.player

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for [PlaybackSpeedDialog] (issue #8). The dialog's sliders are driven by local
 * state seeded from the real value; dragging must APPLY the new speed AND keep the slider on the new
 * value rather than springing back to 1× (the reported bug). Here we drive the slider through its
 * SetProgress semantics action and assert the change is both forwarded and reflected.
 */
class PlaybackSpeedDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingSpeedSlider_appliesAndKeepsNewValue() {
        var appliedSpeed: Float? = null
        composeRule.setContent {
            PlaybackSpeedDialog(
                currentSpeed = 1f,
                currentPitch = 1f,
                onSpeedChange = { appliedSpeed = it },
                onPitchChange = {},
                onDismiss = {},
            )
        }

        // The first slider is speed. Set it toward the top of its 0.5..2 range.
        val speedSlider = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
        speedSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(1.75f) }
        composeRule.waitForIdle()

        // The change must have been forwarded to the player (applied), not swallowed.
        val applied = appliedSpeed
        assertNotNull("speed change was not applied", applied)
        assertTrue("expected an increased speed, got $applied", applied!! > 1.4f)

        // And the slider must still sit at the new value — NOT sprung back to 1× (the bug). Read the
        // slider's current progress from its ProgressBarRangeInfo semantics.
        val range: ProgressBarRangeInfo =
            speedSlider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertTrue(
            "slider reverted (progress=${range.current}); it should hold the dragged value",
            range.current > 1.4f
        )
    }

    @Test
    fun reset_returnsSlidersToNormal() {
        var appliedSpeed: Float? = null
        var appliedPitch: Float? = null
        composeRule.setContent {
            PlaybackSpeedDialog(
                currentSpeed = 1.5f,
                currentPitch = 1.5f,
                onSpeedChange = { appliedSpeed = it },
                onPitchChange = { appliedPitch = it },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Reset").performClick()
        composeRule.waitForIdle()

        assertEquals(1f, appliedSpeed)
        assertEquals(1f, appliedPitch)
    }
}
