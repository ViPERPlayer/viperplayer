package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.radio.RadioPlaylist
import com.viperplayer.domain.player.RadioQueueBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Replaces the old bespoke RadioPreviewSheet test (issue #7): "Song radio" now opens in the STANDARD
 * playlist detail screen instead of a custom sheet. These tests cover the two halves of that:
 *
 *  1. The radio action navigates with a *radio* [MediaId] that packs the seed — the exact id the
 *     player overflow builds via [RadioPlaylist.buildMediaId] and hands to `onNavigateToPlaylist`.
 *  2. Feeding that radio playlist (seed + related songs, built by [RadioQueueBuilder]) into the normal
 *     [PlaylistDetailScreenContent] renders every radio song, identically to any other playlist.
 */
class RadioPlaylistDetailTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun song(id: String, title: String) =
        Song(id = MediaId.Plugin("testsource", id), title = title, durationMs = 1000L)

    private val seed = song("seed", "Seed Song")
    private val radioSongs = RadioQueueBuilder.buildSongs(
        seed = seed,
        related = listOf(song("a", "Radio A"), song("b", "Radio B")),
    )

    @Test
    fun radioAction_navigatesWithRadioMediaId_encodingTheSeed() {
        // Mirror the player overflow: build the nav target for the current song's radio and capture it.
        var navigatedTo: MediaId? = null
        val onNavigateToPlaylist: (MediaId) -> Unit = { navigatedTo = it }

        onNavigateToPlaylist(RadioPlaylist.buildMediaId(seed.id))

        val target = navigatedTo!!
        // The action navigates to a radio playlist (standard PlaylistDetail route), not a bespoke sheet.
        assertTrue(RadioPlaylist.isRadioPlaylist(target))
        // ...and the seed round-trips out of it so the detail screen can regenerate the radio.
        assertEquals(seed.id, RadioPlaylist.parseSeedId(target))
    }

    @Test
    fun radioPlaylist_rendersAllRadioSongs_inStandardDetailScreen() {
        val radioPlaylist = Playlist(
            id = RadioPlaylist.buildMediaId(seed.id),
            name = "Song radio",
            artworkUrl = seed.artworkUrl,
            songCount = radioSongs.size,
            isEditable = false,
        )

        composeRule.setContent {
            PlaylistDetailScreenContent(
                rootPadding = PaddingValues(0.dp),
                uiState = PlaylistDetailUiState.Success(
                    playlist = radioPlaylist,
                    songs = radioSongs,
                ),
                currentSong = null,
                isPlaying = false,
                isEditable = false,
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

        // The generated radio (seed first, then related) renders like any other playlist's song list.
        composeRule.onNodeWithText("Seed Song").assertIsDisplayed()
        composeRule.onNodeWithText("Radio A").assertIsDisplayed()
        composeRule.onNodeWithText("Radio B").assertIsDisplayed()
    }
}
