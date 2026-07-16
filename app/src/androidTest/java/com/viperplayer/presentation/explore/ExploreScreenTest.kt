package com.viperplayer.presentation.explore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.MediaId
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the Explore screen: it renders the aggregated discovery sections / mood chips
 * / browse categories, and shows a graceful empty state when there is nothing to explore.
 */
class ExploreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val populated = ExploreContent(
        moods = listOf(
            ExploreMood("Chill", "demo", "s1", "chill"),
            ExploreMood("Focus", "demo", "s1", "focus"),
        ),
        categories = listOf(
            BrowseCategory(id = "charts", pluginId = "demo", name = "Charts"),
        ),
        sections = listOf(
            ExploreSection(
                id = "demo:top",
                pluginId = "demo",
                pluginName = "Demo",
                title = "Top Albums",
                subtitle = "From Demo",
                items = listOf(
                    Album(id = MediaId("demo", "a1"), name = "Album One"),
                    Album(id = MediaId("demo", "a2"), name = "Album Two"),
                ),
            ),
        ),
    )

    private fun content(state: ExploreUiState) {
        composeRule.setContent {
            ExploreScreenContent(
                uiState = state,
                currentSongId = null,
                isPlaying = false,
                rootPadding = PaddingValues(0.dp),
                onNavigateBack = {},
                onRefresh = {},
                onMoodSelected = {},
                onNavigateToAlbum = {},
                onNavigateToArtist = {},
                onNavigateToPlaylist = {},
                onPlaySongFromSection = { _, _ -> },
            )
        }
    }

    @Test
    fun content_rendersSectionsMoodsAndCategories() {
        content(ExploreUiState.Content(content = populated, hasPlugins = true))

        composeRule.onNodeWithText("Top Albums").assertIsDisplayed()
        composeRule.onNodeWithText("Album One").assertIsDisplayed()
        composeRule.onNodeWithTag("exploreMoods").assertIsDisplayed()
        composeRule.onNodeWithText("Chill").assertIsDisplayed()
        composeRule.onNodeWithTag("exploreCategories").assertIsDisplayed()
        composeRule.onNodeWithText("Charts").assertIsDisplayed()
        composeRule.onNodeWithTag("section:demo:top").assertIsDisplayed()
    }

    @Test
    fun emptyContent_withPlugins_showsEmptyState() {
        content(ExploreUiState.Content(content = ExploreContent(), hasPlugins = true))

        composeRule.onNodeWithTag("exploreEmpty").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing to explore yet").assertIsDisplayed()
    }

    @Test
    fun emptyContent_withoutPlugins_showsNoSourcesState() {
        content(ExploreUiState.Content(content = ExploreContent(), hasPlugins = false))

        composeRule.onNodeWithTag("exploreEmpty").assertIsDisplayed()
        composeRule.onNodeWithText("No sources connected").assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() {
        content(ExploreUiState.Error("boom"))

        composeRule.onNodeWithText("boom").assertIsDisplayed()
    }
}
