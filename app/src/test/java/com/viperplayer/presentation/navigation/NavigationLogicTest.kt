package com.viperplayer.presentation.navigation

import androidx.navigation3.runtime.NavKey
import com.viperplayer.domain.model.MediaId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [NavigationLogic] single-top guard used by [Navigator] to stop rapid taps from
 * pushing duplicate destinations.
 */
class NavigationLogicTest {

    private fun album(id: String): NavKey =
        AlbumDetail(albumId = MediaId(pluginId = "p", sourceId = id), initialName = "n")

    @Test
    fun shouldPush_ontoEmptyStack_isTrue() {
        assertTrue(NavigationLogic.shouldPush(emptyList(), album("a1")))
    }

    @Test
    fun shouldPush_differentTop_isTrue() {
        val stack = listOf<NavKey>(Home, album("a1"))
        assertTrue(NavigationLogic.shouldPush(stack, album("a2")))
    }

    @Test
    fun shouldPush_sameDestinationOnTop_isFalse() {
        val stack = listOf<NavKey>(Home, album("a1"))
        // Re-tapping the exact same item (equal data-class key) is a no-op.
        assertFalse(NavigationLogic.shouldPush(stack, album("a1")))
    }

    @Test
    fun shouldPush_sameKeyDeeperInStack_stillPushes() {
        // Only the *top* is guarded; the same key deeper down does not block a re-push.
        val stack = listOf<NavKey>(album("a1"), album("a2"))
        assertTrue(NavigationLogic.shouldPush(stack, album("a1")))
    }

    @Test
    fun shouldPush_differentDestinationType_isTrue() {
        val stack = listOf<NavKey>(album("a1"))
        assertTrue(NavigationLogic.shouldPush(stack, Search))
    }
}
