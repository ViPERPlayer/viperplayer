package com.viperplayer.presentation.player

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight

/**
 * Pure enum → Compose-type mapping for lyric styling. Kept out of the Composable so it can be
 * unit-tested (Compose value classes like [TextUnit]/[FontWeight]/[TextAlign] are plain JVM types).
 * The base sizes here reproduce the previous hard-coded 17sp inactive / 20sp active look at MEDIUM.
 */
object LyricsStyleMapping {

    /** Base (inactive-line) font size for each bucket. */
    fun baseFontSize(size: LyricsFontSize): TextUnit = when (size) {
        LyricsFontSize.SMALL -> 14.sp
        LyricsFontSize.MEDIUM -> 17.sp
        LyricsFontSize.LARGE -> 22.sp
    }

    /**
     * Active-line font size: the base size scaled by [activeLineScale]. Kept pure so the exact
     * active sp is testable.
     */
    fun activeFontSize(size: LyricsFontSize, activeLineScale: Float): TextUnit =
        (baseFontSize(size).value * activeLineScale).sp

    /**
     * Translation/romanization sub-line size — a fixed fraction of the base line size. The factors
     * reproduce the prior 15sp active / 13sp inactive sub-lines at the MEDIUM (17sp) base.
     */
    fun subLineFontSize(size: LyricsFontSize, active: Boolean): TextUnit {
        val base = baseFontSize(size).value
        val factor = if (active) 15f / 17f else 13f / 17f
        return (base * factor).sp
    }

    /** Inactive-line weight. NORMAL → Medium (prior look); BOLD → SemiBold. */
    fun inactiveFontWeight(weight: LyricsFontWeight): FontWeight = when (weight) {
        LyricsFontWeight.NORMAL -> FontWeight.Medium
        LyricsFontWeight.BOLD -> FontWeight.SemiBold
    }

    /** Active-line weight. NORMAL → Bold (prior look); BOLD → ExtraBold. */
    fun activeFontWeight(weight: LyricsFontWeight): FontWeight = when (weight) {
        LyricsFontWeight.NORMAL -> FontWeight.Bold
        LyricsFontWeight.BOLD -> FontWeight.ExtraBold
    }

    fun textAlign(alignment: LyricsAlignment): TextAlign = when (alignment) {
        LyricsAlignment.LEFT -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
    }
}
