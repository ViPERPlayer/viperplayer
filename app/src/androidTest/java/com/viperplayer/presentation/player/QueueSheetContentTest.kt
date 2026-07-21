package com.viperplayer.presentation.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the stateless [QueueSheetContent]: rows render, the now-playing row is
 * present, tapping a row plays it, the × button and a horizontal swipe both remove a row, and each
 * row exposes a reorderable drag handle.
 */
class QueueSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun song(id: String, title: String) =
        Song(id = MediaId.Local(id), title = title, durationMs = 1000L)

    private val queue = listOf(
        song("a", "Song A"),
        song("b", "Song B"),
        song("c", "Song C"),
    )

    @Test
    fun rows_render_forEachQueueItem() {
        composeRule.setContent {
            QueueSheetContent(
                queue = queue,
                currentSongId = queue[1].id,
                isPlaying = true,
                onPlay = {},
                onRemove = {},
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithText("Song A").assertIsDisplayed()
        composeRule.onNodeWithText("Song B").assertIsDisplayed()
        composeRule.onNodeWithText("Song C").assertIsDisplayed()
    }

    @Test
    fun tappingRow_firesPlayWithItsIndex() {
        var played: Int? = null
        composeRule.setContent {
            QueueSheetContent(
                queue = queue,
                currentSongId = queue[0].id,
                isPlaying = false,
                onPlay = { played = it },
                onRemove = {},
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithText("Song C").performClick()

        assertEquals(2, played)
    }

    @Test
    fun removeButton_firesRemoveWithItsIndex() {
        var removed: Int? = null
        composeRule.setContent {
            QueueSheetContent(
                queue = queue,
                currentSongId = queue[0].id,
                isPlaying = false,
                onPlay = {},
                onRemove = { removed = it },
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithTag("queueRemove_1").performClick()

        assertEquals(1, removed)
    }

    @Test
    fun swipingRow_firesRemove() {
        var removed: Int? = null
        composeRule.setContent {
            QueueSheetContent(
                queue = queue,
                currentSongId = queue[0].id,
                isPlaying = false,
                onPlay = {},
                onRemove = { removed = it },
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithTag("queueSwipe_2").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(2, removed)
    }

    @Test
    fun eachRow_exposesDragHandle() {
        composeRule.setContent {
            QueueSheetContent(
                queue = queue,
                currentSongId = queue[0].id,
                isPlaying = false,
                onPlay = {},
                onRemove = {},
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        // Each row exposes a reorderable drag handle. The handle Icon's semantics merge into its row,
        // so query the unmerged tree; assert existence (the swipe-dismiss layer can occlude the
        // trailing handle for an isDisplayed check at rest).
        composeRule.onNodeWithTag("queueDragHandle_0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("queueDragHandle_1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("queueDragHandle_2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun duplicateSongs_renderWithoutCrashing() {
        // A track can appear more than once in the queue (e.g. "duplicate in queue"); stable per-row
        // keys must keep the LazyColumn keys unique so this doesn't throw a duplicate-key exception.
        val a = song("a", "Song A")
        composeRule.setContent {
            QueueSheetContent(
                queue = listOf(a, song("b", "Song B"), a),
                currentSongId = a.id,
                isPlaying = false,
                onPlay = {},
                onRemove = {},
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithTag("queueDragHandle_0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("queueDragHandle_2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyQueue_showsEmptyState() {
        composeRule.setContent {
            QueueSheetContent(
                queue = emptyList(),
                currentSongId = null,
                isPlaying = false,
                onPlay = {},
                onRemove = {},
                onReorder = { _, _ -> },
                onSaveAsPlaylist = {},
            )
        }

        composeRule.onNodeWithText("Queue is empty").assertIsDisplayed()
    }
}
