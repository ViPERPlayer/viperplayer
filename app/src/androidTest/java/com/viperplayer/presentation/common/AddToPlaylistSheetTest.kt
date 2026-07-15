package com.viperplayer.presentation.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the add-to-playlist picker body. Verifies the "New playlist" entry, the
 * existing-playlist rows, and that picking each fires the correct callback.
 */
class AddToPlaylistSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun playlist(id: String, name: String, count: Int) =
        Playlist(id = MediaId("local", id), name = name, songCount = count)

    @Test
    fun listsExistingPlaylistsAndNewEntry() {
        composeRule.setContent {
            AddToPlaylistSheetContent(
                playlists = listOf(
                    playlist("1", "Road Trip", 3),
                    playlist("2", "Chill", 8),
                ),
                onNewPlaylist = {},
                onPickPlaylist = {},
            )
        }

        composeRule.onNodeWithText("New playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
        composeRule.onNodeWithText("Chill").assertIsDisplayed()
    }

    @Test
    fun clickingPlaylist_firesPickCallbackWithThatPlaylist() {
        var picked: Playlist? = null
        val target = playlist("2", "Chill", 8)
        composeRule.setContent {
            AddToPlaylistSheetContent(
                playlists = listOf(playlist("1", "Road Trip", 3), target),
                onNewPlaylist = {},
                onPickPlaylist = { picked = it },
            )
        }

        composeRule.onNodeWithText("Chill").performClick()

        assertEquals(target, picked)
    }

    @Test
    fun clickingNewPlaylist_firesNewCallback() {
        var newClicked = false
        composeRule.setContent {
            AddToPlaylistSheetContent(
                playlists = emptyList(),
                onNewPlaylist = { newClicked = true },
                onPickPlaylist = {},
            )
        }

        composeRule.onNodeWithText("New playlist").performClick()

        assertEquals(true, newClicked)
    }

    @Test
    fun emptyPlaylists_showsEmptyHint() {
        composeRule.setContent {
            AddToPlaylistSheetContent(
                playlists = emptyList(),
                onNewPlaylist = {},
                onPickPlaylist = {},
            )
        }

        composeRule.onNodeWithText("You have no playlists yet").assertIsDisplayed()
    }
}
