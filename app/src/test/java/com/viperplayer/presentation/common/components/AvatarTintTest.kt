package com.viperplayer.presentation.common.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure palette-index selection behind [avatarTonesFor]. The colour mapping itself
 * needs a Compose ColorScheme, but the index arithmetic ([avatarToneIndex]) is Android-free.
 */
class AvatarTintTest {

    private val paletteSize = 4

    @Test
    fun index_isDeterministic_forSameKey() {
        val a = avatarToneIndex("friend-42", paletteSize)
        val b = avatarToneIndex("friend-42", paletteSize)
        assertEquals(a, b)
    }

    @Test
    fun index_isAlwaysInRange() {
        val keys = listOf("", "a", "friend-1", "friend-2", "友達", "🎵", "a-very-long-identifier-string")
        for (key in keys) {
            val index = avatarToneIndex(key, paletteSize)
            assertTrue("index $index out of range for '$key'", index in 0 until paletteSize)
        }
    }

    @Test
    fun index_handlesIntMinValueHash_withoutOverflow() {
        // "polygenelubricants" is the classic String whose hashCode() is exactly Int.MIN_VALUE, where a
        // plain abs() would overflow back to a negative value.
        val key = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, key.hashCode())
        val index = avatarToneIndex(key, paletteSize)
        assertTrue("index $index out of range", index in 0 until paletteSize)
    }

    @Test
    fun differentKeys_spreadAcrossPalette() {
        // Not a strict guarantee, but a large set of distinct keys should touch more than one tone.
        val distinctIndices = (0 until 100).map { avatarToneIndex("id-$it", paletteSize) }.toSet()
        assertTrue("expected keys to spread across the palette", distinctIndices.size > 1)
    }
}
