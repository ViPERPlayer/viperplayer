package com.viperplayer.alarm

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.ui.AlarmsScreenContent
import com.viperplayer.alarm.ui.AlarmsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

/**
 * Compose UI test for the alarm list body ([AlarmsScreenContent]). Verifies alarms render, the empty
 * state, the enable toggle callback, and the add-alarm event — matching the app's stateless-content
 * test convention (see AddToPlaylistSheetTest).
 */
class AlarmsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun alarm(
        id: Long,
        hour: Int,
        minute: Int,
        label: String = "",
        enabled: Boolean = true,
        days: Set<DayOfWeek> = emptySet(),
    ) = Alarm(id = id, hour = hour, minute = minute, label = label, enabled = enabled, daysOfWeek = days)

    @Test
    fun rendersAlarmLabelAndAddButton() {
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = listOf(alarm(1, 8, 0, label = "Wake up"))),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = {},
                onEditAlarm = {},
                onToggleEnabled = { _, _ -> },
                onOpenExactSettings = {},
            )
        }

        composeRule.onNodeWithText("Wake up").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add alarm").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsHint() {
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = emptyList()),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = {},
                onEditAlarm = {},
                onToggleEnabled = { _, _ -> },
                onOpenExactSettings = {},
            )
        }

        composeRule.onNodeWithText("No alarms yet. Tap + to add one.").assertIsDisplayed()
    }

    @Test
    fun addButton_firesAddEvent() {
        var added = false
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = emptyList()),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = { added = true },
                onEditAlarm = {},
                onToggleEnabled = { _, _ -> },
                onOpenExactSettings = {},
            )
        }

        composeRule.onNodeWithContentDescription("Add alarm").performClick()

        assertTrue(added)
    }

    @Test
    fun toggle_firesEnableCallbackWithNewValue() {
        var toggled: Pair<Long, Boolean>? = null
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = listOf(alarm(7, 9, 30, label = "Gym", enabled = true))),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = {},
                onEditAlarm = {},
                onToggleEnabled = { a, enabled -> toggled = a.id to enabled },
                onOpenExactSettings = {},
            )
        }

        val toggle = composeRule.onNodeWithContentDescription("Enable or disable this alarm")
        toggle.assertIsOn()
        toggle.performClick()

        assertEquals(7L to false, toggled)
    }

    @Test
    fun disabledAlarm_showsToggleOff() {
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = listOf(alarm(3, 6, 15, enabled = false))),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = {},
                onEditAlarm = {},
                onToggleEnabled = { _, _ -> },
                onOpenExactSettings = {},
            )
        }

        composeRule.onNodeWithContentDescription("Enable or disable this alarm").assertIsOff()
    }

    @Test
    fun exactPermissionBanner_shownWhenDenied_andFiresEvent() {
        var opened = false
        composeRule.setContent {
            AlarmsScreenContent(
                state = AlarmsUiState(alarms = emptyList(), canScheduleExact = false),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onAddAlarm = {},
                onEditAlarm = {},
                onToggleEnabled = { _, _ -> },
                onOpenExactSettings = { opened = true },
            )
        }

        composeRule.onNodeWithText("Exact alarms are disabled").assertIsDisplayed().performClick()
        assertTrue(opened)
    }
}
