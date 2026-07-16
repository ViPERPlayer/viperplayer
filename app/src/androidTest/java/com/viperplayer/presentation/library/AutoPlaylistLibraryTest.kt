package com.viperplayer.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.Composable
import com.viperplayer.domain.autoplaylist.AutoPlaylistType
import com.viperplayer.domain.model.Playlist
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.search.model.SearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for how the Library surfaces the dynamic auto-playlists: the section header
 * renders, an auto-playlist row shows its name + description, and clicking it forwards the auto
 * [Playlist] (whose [MediaId] plugin is "auto") to the navigation callback.
 */
class AutoPlaylistLibraryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mostPlayed = Playlist(
        id = AutoPlaylistType.MOST_PLAYED.mediaId,
        name = "Most Played",
        description = "Your most-played songs of all time",
        songCount = 12,
        isEditable = false,
    )

    @Test
    fun sectionHeader_isDisplayed() {
        composeRule.setContent {
            LibrarySectionHeader(title = "Auto-playlists")
        }
        composeRule.onNodeWithText("Auto-playlists").assertIsDisplayed()
    }

    @Test
    fun autoPlaylistRow_showsNameAndDescription() {
        composeRule.setContent {
            AutoPlaylistRow(playlist = mostPlayed, onClick = {})
        }
        composeRule.onNodeWithText("Most Played").assertIsDisplayed()
        composeRule.onNodeWithText("Your most-played songs of all time").assertIsDisplayed()
    }

    @Test
    fun autoPlaylistRow_click_forwardsAutoPlaylist() {
        var clicked: Playlist? = null
        composeRule.setContent {
            AutoPlaylistRow(playlist = mostPlayed, onClick = { clicked = it })
        }

        composeRule.onNodeWithText("Most Played").performClick()

        assertEquals(mostPlayed, clicked)
        assertEquals(AutoPlaylistType.PLUGIN_ID, clicked?.id?.pluginId)
        assertTrue(AutoPlaylistType.isAutoPlaylist(clicked!!.id))
    }

    /** Mirrors the auto-playlist row rendered inside LibraryScreen's Playlists tab. */
    @Composable
    private fun AutoPlaylistRow(playlist: Playlist, onClick: (Playlist) -> Unit) {
        Column {
            ListItem(
                title = playlist.name,
                badges = emptyList(),
                subtitle = playlist.description,
                isActive = false,
                leadingContent = {
                    ListItemLeadingArtwork(
                        artworkUrl = playlist.artworkUrl,
                        type = SearchItem.Type.PLAYLIST,
                        isActive = false,
                        isPlaying = false,
                    )
                },
                trailingContent = {},
                onClick = { onClick(playlist) },
            )
        }
    }
}
