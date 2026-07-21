package com.viperplayer.presentation.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.autoplaylist.AutoPlaylistType
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for how the Library surfaces the dynamic auto-playlists. These render the PRODUCTION
 * composables from LibraryScreen ([LibrarySectionHeader], [AutoPlaylistRow]) — not private mirrors — so
 * a regression in the real section wiring is caught: the header renders, a row shows its name +
 * description (or the localized song-count fallback), and clicking it forwards the auto [Playlist]
 * (whose MediaId plugin is "auto") to the navigation callback.
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
    fun autoPlaylistRow_nullDescription_showsLocalizedSongCountFallback() {
        // When a row has no description, the production row falls back to the localized song_count
        // plurals (not a hardcoded English string) — one song here => the "one" quantity form.
        val single = mostPlayed.copy(description = null, songCount = 1)
        composeRule.setContent {
            AutoPlaylistRow(playlist = single, onClick = {})
        }
        composeRule.onNodeWithText("1 song").assertIsDisplayed()
    }

    @Test
    fun autoPlaylistRow_click_forwardsAutoPlaylist() {
        var clicked: Playlist? = null
        composeRule.setContent {
            AutoPlaylistRow(playlist = mostPlayed, onClick = { clicked = it })
        }

        composeRule.onNodeWithText("Most Played").performClick()

        assertEquals(mostPlayed, clicked)
        assertEquals(AutoPlaylistType.PLUGIN_ID, (clicked?.id as? MediaId.Plugin)?.pluginId)
        assertTrue(AutoPlaylistType.isAutoPlaylist(clicked!!.id))
    }
}
