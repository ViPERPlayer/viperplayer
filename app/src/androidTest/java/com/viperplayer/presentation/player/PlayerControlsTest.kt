package com.viperplayer.presentation.player

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the player's self-contained transport widgets: the seek bar's tap-to-seek and
 * the drag-to-dismiss grab handle. These render with plain callbacks (no MediaController), so the
 * scrubber's fraction→position math and the handle's tap/drag affordance are exercised directly.
 */
class PlayerControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seekBar_tap_seeksNearTappedFraction() {
        var seekedTo: Long? = null
        composeRule.setContent {
            WavySeekBar(
                position = { 0L },
                bufferedPosition = { 0L },
                duration = 200_000L,
                isPlaying = false,
                onSeek = { seekedTo = it },
                modifier = Modifier.width(300.dp),
            )
        }

        // Tap the center of the bar → roughly the midpoint of the track.
        composeRule.onNodeWithTag("seekBar").performClick()
        composeRule.waitForIdle()

        val result = seekedTo
        assertTrue("expected a seek near the middle, got $result", result != null && result in 80_000L..120_000L)
    }

    @Test
    fun dragHandle_swipeDown_firesDismiss() {
        var dismissed = false
        composeRule.setContent {
            DragToDismissHandle(onDismiss = { dismissed = true })
        }

        // A long downward drag from the handle, extending well past its own bounds, must exceed the
        // dismiss threshold. Built from primitives so it works regardless of the swipe-helper API.
        composeRule.onNodeWithTag("playerDragHandle").performTouchInput {
            down(center)
            moveTo(Offset(center.x, center.y + 150f))
            moveTo(Offset(center.x, center.y + 400f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(true, dismissed)
    }
}
