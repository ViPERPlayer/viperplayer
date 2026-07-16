package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure lyric behavior decisions and the default settings snapshot. Defaults must
 * reproduce the app's prior hard-coded lyrics rendering so upgrading users see no change.
 */
class LyricsBehaviorTest {

    // --- shouldDimLine ---

    @Test
    fun shouldDimLine_dimsInactiveLine_whenDimmingOn() {
        assertTrue(LyricsBehavior.shouldDimLine(lineIndex = 2, activeIndex = 5, dimInactiveLines = true))
    }

    @Test
    fun shouldDimLine_neverDimsActiveLine() {
        assertFalse(LyricsBehavior.shouldDimLine(lineIndex = 5, activeIndex = 5, dimInactiveLines = true))
    }

    @Test
    fun shouldDimLine_dimsNothing_whenDimmingOff() {
        assertFalse(LyricsBehavior.shouldDimLine(lineIndex = 2, activeIndex = 5, dimInactiveLines = false))
        assertFalse(LyricsBehavior.shouldDimLine(lineIndex = 5, activeIndex = 5, dimInactiveLines = false))
    }

    @Test
    fun shouldDimLine_noActiveLine_dimsEverything_whenOn() {
        // activeIndex == -1 (before first line): all lines are inactive, so all dim when dimming is on.
        assertTrue(LyricsBehavior.shouldDimLine(lineIndex = 0, activeIndex = -1, dimInactiveLines = true))
    }

    // --- clampActiveLineScale ---

    @Test
    fun clampActiveLineScale_clampsBelowMin() {
        assertEquals(LyricsSettings.MIN_ACTIVE_LINE_SCALE, LyricsBehavior.clampActiveLineScale(0.5f), 0.001f)
    }

    @Test
    fun clampActiveLineScale_clampsAboveMax() {
        assertEquals(LyricsSettings.MAX_ACTIVE_LINE_SCALE, LyricsBehavior.clampActiveLineScale(3f), 0.001f)
    }

    @Test
    fun clampActiveLineScale_passesInRangeValue() {
        assertEquals(1.3f, LyricsBehavior.clampActiveLineScale(1.3f), 0.001f)
    }

    // --- defaults ---

    @Test
    fun defaults_preservePriorBehavior() {
        val defaults = LyricsSettings()
        assertEquals(LyricsFontSize.MEDIUM, defaults.fontSize)
        assertEquals(LyricsAlignment.LEFT, defaults.alignment)
        assertEquals(LyricsFontWeight.NORMAL, defaults.fontWeight)
        assertEquals(LyricsHighlightColor.PRIMARY, defaults.highlightColor)
        assertEquals(LyricsSettings.DEFAULT_ACTIVE_LINE_SCALE, defaults.activeLineScale, 0.001f)
        assertTrue(defaults.autoScroll)
        assertTrue(defaults.tapToSeek)
        assertTrue(defaults.dimInactiveLines)
        assertFalse(defaults.showTranslationByDefault)
        assertFalse(defaults.showRomanizationByDefault)
    }

    @Test
    fun defaultActiveLineScale_isWithinBounds() {
        assertTrue(LyricsSettings.DEFAULT_ACTIVE_LINE_SCALE >= LyricsSettings.MIN_ACTIVE_LINE_SCALE)
        assertTrue(LyricsSettings.DEFAULT_ACTIVE_LINE_SCALE <= LyricsSettings.MAX_ACTIVE_LINE_SCALE)
    }
}
