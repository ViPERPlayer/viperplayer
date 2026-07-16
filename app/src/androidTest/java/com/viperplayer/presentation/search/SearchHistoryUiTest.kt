package com.viperplayer.presentation.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.search.SearchHistoryLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the search recent-history surface. They host the real
 * [RecentSearchesHeader] + [HistoryListItem] + [SuggestionListItem] composables over a mutable
 * history list (as [SearchViewModel] would), driving them via the same [SearchHistoryLogic] rules
 * the ViewModel/repository use, and assert the UI both renders the recent list and forwards
 * remove / clear / insert events.
 */
class SearchHistoryUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Hosts the history section over a mutable list. Blank query -> "recent searches" header + full
     * history; non-blank query -> filtered history (the header is hidden, like the real screen).
     * Returns an accessor for the live history so a test can assert committed edits.
     */
    private fun setContent(
        initialHistory: List<String>,
        query: String = "",
    ): () -> List<String> {
        var history by mutableStateOf(initialHistory)
        composeRule.setContent {
            val shown = if (query.isEmpty()) {
                history
            } else {
                history.filter { it.contains(query, ignoreCase = true) }
            }
            Column {
                LazyColumn {
                    if (query.isEmpty() && shown.isNotEmpty()) {
                        item(key = "header") {
                            RecentSearchesHeader(
                                onClearAll = { history = SearchHistoryLogic.clear() }
                            )
                        }
                    }
                    items(shown, key = { "h_$it" }) { entry ->
                        HistoryListItem(
                            suggestion = entry,
                            onRemove = { history = SearchHistoryLogic.remove(history, entry) },
                            onInsert = { /* inserts into the field; state owned elsewhere */ },
                            modifier = Modifier
                        )
                    }
                }
            }
        }
        return { history }
    }

    @Test
    fun emptyQuery_showsRecentHeaderAndHistory() {
        setContent(listOf("beatles", "queen"))

        composeRule.onNodeWithTag("searchRecentHeader").assertIsDisplayed()
        composeRule.onNodeWithText("Recent searches").assertIsDisplayed()
        composeRule.onNodeWithText("Clear all").assertIsDisplayed()
        composeRule.onNodeWithText("beatles").assertIsDisplayed()
        composeRule.onNodeWithText("queen").assertIsDisplayed()
    }

    @Test
    fun clearAll_removesAllHistory() {
        val history = setContent(listOf("beatles", "queen"))

        composeRule.onNodeWithText("Clear all").performClick()

        assertTrue(history().isEmpty())
    }

    @Test
    fun removeEntry_dropsOnlyThatEntry() {
        val history = setContent(listOf("beatles", "queen"))

        // Two history rows -> two "Remove" buttons; tap the first (beatles).
        composeRule.onAllNodesWithContentDescription("Remove")[0].performClick()

        assertEquals(listOf("queen"), history())
    }

    @Test
    fun nonBlankQuery_hidesRecentHeader_andFiltersHistory() {
        setContent(listOf("beatles", "queen"), query = "que")

        composeRule.onNodeWithText("Recent searches").assertDoesNotExist()
        composeRule.onNodeWithText("queen").assertIsDisplayed()
        composeRule.onNodeWithText("beatles").assertDoesNotExist()
    }

    @Test
    fun suggestionItem_forwardsInsert() {
        var inserted = false
        composeRule.setContent {
            SuggestionListItem(
                suggestion = "pink floyd",
                onInsert = { inserted = true },
                modifier = Modifier
            )
        }

        composeRule.onNodeWithContentDescription("Insert query").performClick()
        assertTrue(inserted)
    }
}
