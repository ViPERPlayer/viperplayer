package com.viperplayer.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for the pure position/order logic behind local-playlist add / remove / reorder. These
 * mirror exactly what [com.viperplayer.data.repository.MediaLibraryRepositoryImpl] persists to the
 * `playlist_songs` order column.
 */
class PlaylistOrderingTest {

    // --- nextPosition (append) ---

    @Test
    fun nextPosition_emptyPlaylist_startsAtZero() {
        assertEquals(0, PlaylistOrdering.nextPosition(null))
    }

    @Test
    fun nextPosition_nonEmptyPlaylist_isMaxPlusOne() {
        assertEquals(4, PlaylistOrdering.nextPosition(3))
    }

    // --- move (reorder) ---

    @Test
    fun move_forward_shiftsInterveningItemsDown() {
        // [A,B,C,D] moving index 0 -> 2 gives [B,C,A,D]
        val result = PlaylistOrdering.move(listOf("A", "B", "C", "D"), fromIndex = 0, toIndex = 2)
        assertEquals(listOf("B", "C", "A", "D"), result)
    }

    @Test
    fun move_backward_shiftsInterveningItemsUp() {
        // [A,B,C,D] moving index 3 -> 1 gives [A,D,B,C]
        val result = PlaylistOrdering.move(listOf("A", "B", "C", "D"), fromIndex = 3, toIndex = 1)
        assertEquals(listOf("A", "D", "B", "C"), result)
    }

    @Test
    fun move_sameIndex_isNoOpAndReturnsSameInstance() {
        val input = listOf("A", "B", "C")
        assertSame(input, PlaylistOrdering.move(input, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun move_outOfRange_returnsInputUnchanged() {
        val input = listOf("A", "B", "C")
        assertSame(input, PlaylistOrdering.move(input, fromIndex = 0, toIndex = 5))
        assertSame(input, PlaylistOrdering.move(input, fromIndex = -1, toIndex = 1))
    }
}
