package com.viperplayer.data.player.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [PlayerTransfer] queue-transfer snapshot: index clamping, empty handling,
 * and position flooring used when moving state between the local ExoPlayer and the CastPlayer.
 */
class PlayerTransferTest {

    @Test
    fun `empty transfer reports empty and safe defaults`() {
        val t = PlayerTransfer.EMPTY
        assertTrue(t.isEmpty)
        assertEquals(0, t.safeIndex)
        assertEquals(0L, t.safePositionMs)
    }

    @Test
    fun `non-empty transfer is not empty`() {
        val t = PlayerTransfer(listOf("a", "b"), currentIndex = 1, positionMs = 5_000, playWhenReady = true)
        assertFalse(t.isEmpty)
    }

    @Test
    fun `index within range is preserved`() {
        val t = PlayerTransfer(listOf("a", "b", "c"), currentIndex = 2, positionMs = 0, playWhenReady = false)
        assertEquals(2, t.safeIndex)
    }

    @Test
    fun `index past the end is clamped to last item`() {
        val t = PlayerTransfer(listOf("a", "b"), currentIndex = 9, positionMs = 0, playWhenReady = false)
        assertEquals(1, t.safeIndex)
    }

    @Test
    fun `negative index is clamped to zero`() {
        val t = PlayerTransfer(listOf("a", "b"), currentIndex = -3, positionMs = 0, playWhenReady = false)
        assertEquals(0, t.safeIndex)
    }

    @Test
    fun `index on empty queue is zero`() {
        val t = PlayerTransfer(emptyList(), currentIndex = 4, positionMs = 0, playWhenReady = false)
        assertEquals(0, t.safeIndex)
    }

    @Test
    fun `negative position is floored to zero`() {
        val t = PlayerTransfer(listOf("a"), currentIndex = 0, positionMs = -100, playWhenReady = false)
        assertEquals(0L, t.safePositionMs)
    }

    @Test
    fun `positive position is preserved`() {
        val t = PlayerTransfer(listOf("a"), currentIndex = 0, positionMs = 12_345, playWhenReady = true)
        assertEquals(12_345L, t.safePositionMs)
        assertTrue(t.playWhenReady)
    }
}
