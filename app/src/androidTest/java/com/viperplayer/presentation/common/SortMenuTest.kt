package com.viperplayer.presentation.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for [SortMenu]: opening the menu, picking an order, and toggling direction by
 * re-picking the selected order. The menu is stateless, so the test drives the [SortOrder] itself via
 * the `onOrderChange` callback (as a real ViewModel would).
 */
class SortMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val options = listOf(
        SortOption.DEFAULT,
        SortOption.TITLE,
        SortOption.DURATION,
    )

    /** Host the stateless [SortMenu] over a mutable [SortOrder], returning the last change captured. */
    private fun setContentWithState(initial: SortOrder = SortOrder.DEFAULT): () -> SortOrder {
        var current by mutableStateOf(initial)
        composeRule.setContent {
            SortMenu(
                current = current,
                options = options,
                onOrderChange = { current = it },
            )
        }
        return { current }
    }

    @Test
    fun clickingSortButton_opensMenuWithOptions() {
        setContentWithState()

        composeRule.onNodeWithTag("sortMenuButton").performClick()

        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Duration").assertIsDisplayed()
    }

    @Test
    fun pickingAnOption_selectsItAscending() {
        val order = setContentWithState()

        composeRule.onNodeWithTag("sortMenuButton").performClick()
        composeRule.onNodeWithTag("sortOption_TITLE").performClick()

        assertEquals(SortOrder(SortOption.TITLE, SortDirection.ASCENDING), order())
    }

    @Test
    fun rePickingSelectedOption_togglesDirectionToDescending() {
        val order = setContentWithState(SortOrder(SortOption.TITLE, SortDirection.ASCENDING))

        // Re-open and pick the already-selected TITLE option → direction toggles.
        composeRule.onNodeWithTag("sortMenuButton").performClick()
        composeRule.onNodeWithTag("sortOption_TITLE").performClick()

        assertEquals(SortOrder(SortOption.TITLE, SortDirection.DESCENDING), order())
    }

    @Test
    fun pickingDefault_resetsToDefaultOrder() {
        val order = setContentWithState(SortOrder(SortOption.DURATION, SortDirection.DESCENDING))

        composeRule.onNodeWithTag("sortMenuButton").performClick()
        composeRule.onNodeWithTag("sortOption_DEFAULT").performClick()

        assertEquals(SortOrder.DEFAULT, order())
    }
}
