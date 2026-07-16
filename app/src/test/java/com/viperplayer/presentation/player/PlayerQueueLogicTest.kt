package com.viperplayer.presentation.player

import com.viperplayer.presentation.player.PlayerQueueLogic.movedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [PlayerQueueLogic] — the Android-free pure logic behind the now-playing
 * player's queue and seek bar: queue remove/move index math (including moving the current item and
 * the list boundaries), duplicate-safe keying, and seek-bar fraction math.
 */
class PlayerQueueLogicTest {

    // --- removalResult ---

    @Test
    fun remove_beforeCurrent_shiftsCurrentUp() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 3, removeIndex = 1, size = 5)
        assertEquals(2, r.newCurrentIndex)
        assertFalse(r.currentRemoved)
    }

    @Test
    fun remove_afterCurrent_leavesCurrentUnchanged() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 1, removeIndex = 4, size = 5)
        assertEquals(1, r.newCurrentIndex)
        assertFalse(r.currentRemoved)
    }

    @Test
    fun remove_currentInMiddle_keepsSlotForNextTrack() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 2, removeIndex = 2, size = 5)
        // The next track slides into slot 2.
        assertEquals(2, r.newCurrentIndex)
        assertTrue(r.currentRemoved)
    }

    @Test
    fun remove_currentIsLast_clampsToNewLast() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 4, removeIndex = 4, size = 5)
        assertEquals(3, r.newCurrentIndex)
        assertTrue(r.currentRemoved)
    }

    @Test
    fun remove_currentIsFirst_staysAtZero() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 0, removeIndex = 0, size = 3)
        assertEquals(0, r.newCurrentIndex)
        assertTrue(r.currentRemoved)
    }

    @Test
    fun remove_lastRemainingItem_emptiesQueue() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 0, removeIndex = 0, size = 1)
        assertNull(r.newCurrentIndex)
        assertTrue(r.currentRemoved)
    }

    @Test
    fun remove_outOfRange_isNoOp() {
        val r = PlayerQueueLogic.removalResult(currentIndex = 2, removeIndex = 9, size = 5)
        assertEquals(2, r.newCurrentIndex)
        assertFalse(r.currentRemoved)
    }

    // --- currentIndexAfterMove ---

    @Test
    fun move_currentItem_followsToNewIndex() {
        assertEquals(4, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 1, fromIndex = 1, toIndex = 4, size = 6))
    }

    @Test
    fun move_fromBeforeToAfterCurrent_shiftsCurrentDown() {
        // Dragging item 0 to slot 3 pushes the current item (index 2) up to index 1.
        assertEquals(1, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 2, fromIndex = 0, toIndex = 3, size = 6))
    }

    @Test
    fun move_fromAfterToBeforeCurrent_shiftsCurrentUp() {
        // Dragging item 5 to slot 1 pushes the current item (index 2) down to index 3.
        assertEquals(3, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 2, fromIndex = 5, toIndex = 1, size = 6))
    }

    @Test
    fun move_bothEndpointsBeforeCurrent_leavesCurrentUnchanged() {
        assertEquals(4, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 4, fromIndex = 0, toIndex = 2, size = 6))
    }

    @Test
    fun move_bothEndpointsAfterCurrent_leavesCurrentUnchanged() {
        assertEquals(1, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 1, fromIndex = 3, toIndex = 5, size = 6))
    }

    @Test
    fun move_sameIndexOrOutOfRange_isNoOp() {
        assertEquals(2, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 2, fromIndex = 3, toIndex = 3, size = 6))
        assertEquals(2, PlayerQueueLogic.currentIndexAfterMove(currentIndex = 2, fromIndex = 9, toIndex = 1, size = 6))
    }

    // --- movedItem (list mutation) ---

    @Test
    fun movedItem_reordersList() {
        assertEquals(listOf("a", "c", "b", "d"), listOf("a", "b", "c", "d").movedItem(1, 2))
    }

    @Test
    fun movedItem_moveToFront() {
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").movedItem(3, 0))
    }

    @Test
    fun movedItem_outOfRangeOrSame_returnsUnchanged() {
        val list = listOf("a", "b", "c")
        assertEquals(list, list.movedItem(1, 1))
        assertEquals(list, list.movedItem(0, 9))
        assertEquals(list, list.movedItem(-1, 1))
    }

    // --- queueKeys ---

    @Test
    fun queueKeys_uniqueIds_areStable() {
        assertEquals(listOf("a#0", "b#0", "c#0"), PlayerQueueLogic.queueKeys(listOf("a", "b", "c")) { it })
    }

    @Test
    fun queueKeys_disambiguateDuplicates() {
        assertEquals(
            listOf("a#0", "b#0", "a#1", "a#2"),
            PlayerQueueLogic.queueKeys(listOf("a", "b", "a", "a")) { it }
        )
    }

    @Test
    fun queueKeys_allKeysUnique() {
        val keys = PlayerQueueLogic.queueKeys(listOf("x", "x", "x", "y", "x")) { it }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun queueKeys_emptyList_isEmpty() {
        assertTrue(PlayerQueueLogic.queueKeys(emptyList<String>()) { it }.isEmpty())
    }

    // --- seek-bar fraction math ---

    @Test
    fun seekFraction_midpoint() {
        assertEquals(0.5f, PlayerQueueLogic.seekFraction(x = 50f, width = 100f), 0.0001f)
    }

    @Test
    fun seekFraction_clampsToBounds() {
        assertEquals(0f, PlayerQueueLogic.seekFraction(x = -20f, width = 100f), 0.0001f)
        assertEquals(1f, PlayerQueueLogic.seekFraction(x = 200f, width = 100f), 0.0001f)
    }

    @Test
    fun seekFraction_zeroWidth_isZero() {
        assertEquals(0f, PlayerQueueLogic.seekFraction(x = 50f, width = 0f), 0.0001f)
    }

    @Test
    fun fractionToPositionMs_scales() {
        assertEquals(90_000L, PlayerQueueLogic.fractionToPositionMs(fraction = 0.5f, durationMs = 180_000L))
    }

    @Test
    fun fractionToPositionMs_zeroDuration_isZero() {
        assertEquals(0L, PlayerQueueLogic.fractionToPositionMs(fraction = 0.5f, durationMs = 0L))
    }

    @Test
    fun progressFraction_normalizes() {
        assertEquals(0.25f, PlayerQueueLogic.progressFraction(positionMs = 45_000L, durationMs = 180_000L), 0.0001f)
    }

    @Test
    fun progressFraction_clampsOverrun() {
        assertEquals(1f, PlayerQueueLogic.progressFraction(positionMs = 200_000L, durationMs = 180_000L), 0.0001f)
    }

    @Test
    fun progressFraction_unknownDuration_isZero() {
        assertEquals(0f, PlayerQueueLogic.progressFraction(positionMs = 45_000L, durationMs = 0L), 0.0001f)
    }
}
