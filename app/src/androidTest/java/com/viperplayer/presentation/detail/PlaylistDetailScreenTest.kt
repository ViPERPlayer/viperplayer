package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the local-playlist detail screen: the rename dialog (prefill, blank
 * validation, confirm callback) and the presence of a working drag handle on each editable row.
 */
class PlaylistDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun song(id: String, title: String) =
        Song(id = MediaId("local", id), title = title, durationMs = 1000L)

    private val playlist = Playlist(
        id = MediaId("local", "playlist_1"),
        name = "Road Trip",
        songCount = 2,
        isEditable = true,
    )

    // --- Rename dialog ---

    @Test
    fun renameDialog_isPrefilledWithCurrentName() {
        composeRule.setContent {
            RenamePlaylistDialog(currentName = "Road Trip", onConfirm = {}, onDismiss = {})
        }

        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
    }

    @Test
    fun renameDialog_confirm_forwardsTrimmedName() {
        var confirmed: String? = null
        composeRule.setContent {
            RenamePlaylistDialog(
                currentName = "Road Trip",
                onConfirm = { confirmed = it },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag("renamePlaylistField").performTextClearance()
        composeRule.onNodeWithTag("renamePlaylistField").performTextInput("  Chill Vibes  ")
        composeRule.onNodeWithTag("renamePlaylistConfirm").performClick()

        assertEquals("Chill Vibes", confirmed)
    }

    @Test
    fun renameDialog_blankName_disablesConfirm() {
        composeRule.setContent {
            RenamePlaylistDialog(currentName = "Road Trip", onConfirm = {}, onDismiss = {})
        }

        composeRule.onNodeWithTag("renamePlaylistConfirm").assertIsEnabled()
        composeRule.onNodeWithTag("renamePlaylistField").performTextClearance()
        composeRule.onNodeWithTag("renamePlaylistConfirm").assertIsNotEnabled()
    }

    @Test
    fun renameDialog_dismiss_firesDismissCallback() {
        var dismissed = false
        composeRule.setContent {
            RenamePlaylistDialog(
                currentName = "Road Trip",
                onConfirm = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(true, dismissed)
    }

    // --- Reorderable edit mode ---

    @Test
    fun editMode_showsDragHandleAndRemovePerRow() {
        composeRule.setContent {
            PlaylistDetailScreenContent(
                rootPadding = PaddingValues(0.dp),
                uiState = PlaylistDetailUiState.Success(
                    playlist = playlist,
                    songs = listOf(song("a", "Song A"), song("b", "Song B")),
                ),
                currentSong = null,
                isPlaying = false,
                isEditable = true,
                onNavigateBack = {},
                onRefresh = {},
                onPlayAll = {},
                onShuffle = {},
                onPlaySong = {},
                onPlayNext = {},
                onAddToQueue = {},
                onToggleLike = {},
                onNavigateToArtist = {},
                onNavigateToAlbum = {},
            )
        }

        // Enter edit mode via the Edit action in the app bar.
        composeRule.onNodeWithContentDescription("Edit").performClick()

        // Each editable row must expose a reorderable drag handle and a remove button.
        composeRule.onNodeWithTag("dragHandle_0").assertIsDisplayed()
        composeRule.onNodeWithTag("dragHandle_1").assertIsDisplayed()
        composeRule.onNodeWithTag("removeSong_0").assertIsDisplayed()
        composeRule.onNodeWithTag("removeSong_1").assertIsDisplayed()
    }

    @Test
    fun editMode_overflowMenu_opensRenameDialog() {
        composeRule.setContent {
            PlaylistDetailScreenContent(
                rootPadding = PaddingValues(0.dp),
                uiState = PlaylistDetailUiState.Success(
                    playlist = playlist,
                    songs = listOf(song("a", "Song A")),
                ),
                currentSong = null,
                isPlaying = false,
                isEditable = true,
                onNavigateBack = {},
                onRefresh = {},
                onPlayAll = {},
                onShuffle = {},
                onPlaySong = {},
                onPlayNext = {},
                onAddToQueue = {},
                onToggleLike = {},
                onNavigateToArtist = {},
                onNavigateToAlbum = {},
            )
        }

        composeRule.onNodeWithTag("playlistOverflowMenu").performClick()
        composeRule.onNodeWithTag("renamePlaylistMenuItem").performClick()

        // The rename dialog appears prefilled with the current playlist name.
        composeRule.onNodeWithTag("renamePlaylistField").assertIsDisplayed()
    }
}
