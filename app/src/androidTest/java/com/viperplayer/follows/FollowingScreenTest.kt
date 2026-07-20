package com.viperplayer.follows

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.ui.FollowingScreenContent
import com.viperplayer.follows.ui.FollowingUiModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the stateless [FollowingScreenContent]: it renders followed artists, shows the
 * empty state, and forwards unfollow. The screen is stateless, so the test owns the artist list and
 * mutates it in response to the unfollow callback (as a real ViewModel would).
 */
class FollowingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun artist(sourceId: String, name: String) = FollowingUiModel(
        artist = FollowedArtist(
            mediaId = MediaId("plugin", sourceId),
            name = name,
            artworkUrl = null,
            followedAt = sourceId.toLong(),
        ),
        latestRelease = null,
    )

    @Test
    fun rendersFollowedArtists() {
        composeRule.setContent {
            FollowingScreenContent(
                artists = listOf(artist("1", "Alpha"), artist("2", "Beta")),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onArtistClick = {},
                onUnfollow = {},
            )
        }

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun emptyList_showsEmptyState() {
        composeRule.setContent {
            FollowingScreenContent(
                artists = emptyList(),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onArtistClick = {},
                onUnfollow = {},
            )
        }

        composeRule.onNodeWithText("You're not following any artists yet. Open an artist and tap Follow.")
            .assertIsDisplayed()
    }

    @Test
    fun tappingArtist_invokesCallback() {
        var clicked: MediaId? = null
        composeRule.setContent {
            FollowingScreenContent(
                artists = listOf(artist("1", "Alpha")),
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onArtistClick = { clicked = it.mediaId },
                onUnfollow = {},
            )
        }

        composeRule.onNodeWithText("Alpha").performClick()

        assertEquals(MediaId("plugin", "1"), clicked)
    }

    @Test
    fun unfollowFromMenu_removesArtistAndInvokesCallback() {
        var unfollowed: MediaId? = null
        composeRule.setContent {
            var artists by remember { mutableStateOf(listOf(artist("1", "Alpha"), artist("2", "Beta"))) }
            FollowingScreenContent(
                artists = artists,
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onArtistClick = {},
                onUnfollow = { id ->
                    unfollowed = id
                    artists = artists.filterNot { it.artist.mediaId == id }
                },
            )
        }

        // Open the per-row overflow menu (the "More" action) for the first row, then Unfollow.
        composeRule.onAllNodesWithContentDescription("More")[0].performClick()
        composeRule.onNodeWithText("Unfollow").performClick()

        assertEquals(MediaId("plugin", "1"), unfollowed)
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
    }
}
