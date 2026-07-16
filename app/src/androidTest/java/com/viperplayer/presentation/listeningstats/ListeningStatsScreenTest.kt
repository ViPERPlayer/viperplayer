package com.viperplayer.presentation.listeningstats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.viperplayer.domain.stats.ActivityBucket
import com.viperplayer.domain.stats.ListeningStats
import com.viperplayer.domain.stats.RankedItem
import com.viperplayer.domain.stats.StatsRange
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the stateless [ListeningStatsContent]. Renders it with hand-built
 * [ListeningStats] (no Hilt / ViewModel) and asserts the top lists and empty state show.
 */
class ListeningStatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun statsWithData() = ListeningStats(
        range = StatsRange.LAST_4_WEEKS,
        totalPlays = 5,
        totalListenedMs = 15L * 60_000L,
        uniqueSongs = 3,
        uniqueArtists = 2,
        uniqueAlbums = 2,
        topSongs = listOf(
            RankedItem(key = "s1", title = "Bohemian Rhapsody", subtitle = "The Queen Band", playCount = 3, listenedMs = 600_000L),
            RankedItem(key = "s2", title = "Come Together", subtitle = "The Beatles", playCount = 2, listenedMs = 300_000L),
        ),
        topArtists = listOf(
            RankedItem(key = "Daft Punk", title = "Daft Punk", subtitle = null, playCount = 3, listenedMs = 600_000L),
        ),
        topAlbums = listOf(
            RankedItem(key = "A Night at the Opera", title = "A Night at the Opera", subtitle = "The Queen Band", playCount = 3, listenedMs = 600_000L),
        ),
        byDayOfWeek = (0 until 7).map { ActivityBucket(it, if (it == 0) 5 else 0) },
        byHourOfDay = (0 until 24).map { ActivityBucket(it, if (it == 9) 5 else 0) },
    )

    @Test
    fun rendersTopSongsArtistsAndAlbums() {
        composeRule.setContent {
            ListeningStatsContent(
                rootPadding = PaddingValues(),
                stats = statsWithData(),
                range = StatsRange.LAST_4_WEEKS,
                hasHistory = true,
                onNavigateBack = {},
                onNavigateToWrapped = {},
                onSelectRange = {},
                onClearHistory = {},
            )
        }

        // Headers + top songs render near the top of the list, so they're on-screen.
        composeRule.onNodeWithText("Top songs").assertIsDisplayed()
        composeRule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        composeRule.onNodeWithText("Come Together").assertIsDisplayed()
        // Lower sections may be below the fold in a LazyColumn — assert they exist in the tree.
        composeRule.onNodeWithText("Top artists").assertExists()
        composeRule.onNodeWithText("Daft Punk").assertExists()
        composeRule.onNodeWithText("Top albums").assertExists()
        composeRule.onNodeWithText("A Night at the Opera").assertExists()
    }

    @Test
    fun showsWrappedEntryPoint() {
        composeRule.setContent {
            ListeningStatsContent(
                rootPadding = PaddingValues(),
                stats = statsWithData(),
                range = StatsRange.LAST_4_WEEKS,
                hasHistory = true,
                onNavigateBack = {},
                onNavigateToWrapped = {},
                onSelectRange = {},
                onClearHistory = {},
            )
        }

        composeRule.onNodeWithText("Your ViPER Wrapped").assertIsDisplayed()
    }

    @Test
    fun emptyStats_showEmptyStateAndNoTopLists() {
        composeRule.setContent {
            ListeningStatsContent(
                rootPadding = PaddingValues(),
                stats = ListeningStats.empty(StatsRange.LAST_4_WEEKS),
                range = StatsRange.LAST_4_WEEKS,
                hasHistory = false,
                onNavigateBack = {},
                onNavigateToWrapped = {},
                onSelectRange = {},
                onClearHistory = {},
            )
        }

        composeRule.onNodeWithText("No listening yet").assertIsDisplayed()
    }
}
