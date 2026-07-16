package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [SortOrder] value type and the selection semantics the sort menu applies:
 * picking a new option selects it ascending, re-picking toggles direction, and DEFAULT resets.
 */
class SortOrderTest {

    @Test
    fun default_isDefaultAndAscending() {
        assertTrue(SortOrder.DEFAULT.isDefault)
        assertEquals(SortOption.DEFAULT, SortOrder.DEFAULT.option)
        assertEquals(SortDirection.ASCENDING, SortOrder.DEFAULT.direction)
    }

    @Test
    fun nonDefaultOption_isNotDefault() {
        assertFalse(SortOrder(SortOption.TITLE, SortDirection.ASCENDING).isDefault)
    }

    @Test
    fun direction_toggles() {
        assertEquals(SortDirection.DESCENDING, SortDirection.ASCENDING.toggled())
        assertEquals(SortDirection.ASCENDING, SortDirection.DESCENDING.toggled())
    }

    // --- menu selection semantics (mirrors SortMenu.onClick) ---

    /** Reproduces the SortMenu's decision for what a click on [picked] produces given [current]. */
    private fun nextOrder(current: SortOrder, picked: SortOption): SortOrder = when {
        picked == SortOption.DEFAULT -> SortOrder.DEFAULT
        picked == current.option -> current.copy(direction = current.direction.toggled())
        else -> SortOrder(picked, SortDirection.ASCENDING)
    }

    @Test
    fun pickingNewOption_selectsItAscending() {
        val result = nextOrder(SortOrder.DEFAULT, SortOption.TITLE)
        assertEquals(SortOrder(SortOption.TITLE, SortDirection.ASCENDING), result)
    }

    @Test
    fun rePickingSelectedOption_togglesDirection() {
        val current = SortOrder(SortOption.TITLE, SortDirection.ASCENDING)
        val result = nextOrder(current, SortOption.TITLE)
        assertEquals(SortOrder(SortOption.TITLE, SortDirection.DESCENDING), result)
    }

    @Test
    fun rePickingTwice_returnsToAscending() {
        var order = SortOrder(SortOption.DURATION, SortDirection.ASCENDING)
        order = nextOrder(order, SortOption.DURATION)
        order = nextOrder(order, SortOption.DURATION)
        assertEquals(SortOrder(SortOption.DURATION, SortDirection.ASCENDING), order)
    }

    @Test
    fun pickingDefault_resetsToDefaultRegardlessOfCurrent() {
        val current = SortOrder(SortOption.ARTIST, SortDirection.DESCENDING)
        val result = nextOrder(current, SortOption.DEFAULT)
        assertEquals(SortOrder.DEFAULT, result)
    }

    @Test
    fun switchingBetweenOptions_alwaysStartsAscending() {
        val current = SortOrder(SortOption.TITLE, SortDirection.DESCENDING)
        val result = nextOrder(current, SortOption.ALBUM)
        assertEquals(SortOrder(SortOption.ALBUM, SortDirection.ASCENDING), result)
    }
}
