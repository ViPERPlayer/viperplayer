package com.viperplayer.presentation.player

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LyricsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure enum → Compose-style mapping used by the lyrics renderer. These lock down
 * the sp/weight/alignment resolution and the active-line scaling so styling changes are intentional.
 */
class LyricsStyleMappingTest {

    @Test
    fun baseFontSize_mapsEachBucket() {
        assertEquals(14f, LyricsStyleMapping.baseFontSize(LyricsFontSize.SMALL).value, 0.001f)
        assertEquals(17f, LyricsStyleMapping.baseFontSize(LyricsFontSize.MEDIUM).value, 0.001f)
        assertEquals(22f, LyricsStyleMapping.baseFontSize(LyricsFontSize.LARGE).value, 0.001f)
    }

    @Test
    fun activeFontSize_scalesBaseByFactor() {
        // MEDIUM base is 17sp; scaling by the default 1.15 gives 19.55sp.
        val active = LyricsStyleMapping.activeFontSize(LyricsFontSize.MEDIUM, 1.15f).value
        assertEquals(19.55f, active, 0.01f)
    }

    @Test
    fun activeFontSize_scaleOfOne_equalsBase() {
        val base = LyricsStyleMapping.baseFontSize(LyricsFontSize.LARGE).value
        val active = LyricsStyleMapping.activeFontSize(LyricsFontSize.LARGE, 1.0f).value
        assertEquals(base, active, 0.001f)
    }

    @Test
    fun subLineFontSize_isSmallerThanBase_andLargerWhenActive() {
        val base = LyricsStyleMapping.baseFontSize(LyricsFontSize.MEDIUM).value
        val inactiveSub = LyricsStyleMapping.subLineFontSize(LyricsFontSize.MEDIUM, active = false).value
        val activeSub = LyricsStyleMapping.subLineFontSize(LyricsFontSize.MEDIUM, active = true).value
        assertTrue(inactiveSub < base)
        assertTrue(activeSub < base)
        assertTrue(activeSub > inactiveSub)
    }

    @Test
    fun fontWeight_normal_mapsMediumToBold() {
        assertEquals(FontWeight.Medium, LyricsStyleMapping.inactiveFontWeight(LyricsFontWeight.NORMAL))
        assertEquals(FontWeight.Bold, LyricsStyleMapping.activeFontWeight(LyricsFontWeight.NORMAL))
    }

    @Test
    fun fontWeight_bold_mapsHeavier() {
        assertEquals(FontWeight.SemiBold, LyricsStyleMapping.inactiveFontWeight(LyricsFontWeight.BOLD))
        assertEquals(FontWeight.ExtraBold, LyricsStyleMapping.activeFontWeight(LyricsFontWeight.BOLD))
    }

    @Test
    fun textAlign_mapsBothOptions() {
        assertEquals(TextAlign.Start, LyricsStyleMapping.textAlign(LyricsAlignment.LEFT))
        assertEquals(TextAlign.Center, LyricsStyleMapping.textAlign(LyricsAlignment.CENTER))
    }

    @Test
    fun defaultMediumStyle_reproducesPriorLook() {
        // Prior renderer: inactive 17sp Medium, active 20sp Bold, sub-lines 15sp/13sp.
        val settings = LyricsSettings()
        val base = LyricsStyleMapping.baseFontSize(settings.fontSize).value
        val active = LyricsStyleMapping.activeFontSize(settings.fontSize, settings.activeLineScale).value
        assertEquals(17f, base, 0.001f)
        // Default scale is 20/17, so the active line is exactly 20sp — matching the old renderer.
        assertEquals(20f, active, 0.01f)
        assertEquals(15f, LyricsStyleMapping.subLineFontSize(settings.fontSize, active = true).value, 0.01f)
        assertEquals(13f, LyricsStyleMapping.subLineFontSize(settings.fontSize, active = false).value, 0.01f)
        assertEquals(FontWeight.Medium, LyricsStyleMapping.inactiveFontWeight(settings.fontWeight))
        assertEquals(FontWeight.Bold, LyricsStyleMapping.activeFontWeight(settings.fontWeight))
        assertEquals(TextAlign.Start, LyricsStyleMapping.textAlign(settings.alignment))
    }
}
