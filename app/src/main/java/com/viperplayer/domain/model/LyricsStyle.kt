package com.viperplayer.domain.model

/**
 * User-facing lyric font size buckets. The concrete sp values live in the presentation layer's
 * style mapping (so the domain stays framework-free); [MEDIUM] reproduces the previous hard-coded
 * sizing and is the default.
 */
enum class LyricsFontSize {
    SMALL,
    MEDIUM,
    LARGE,
}

/** Horizontal alignment of lyric lines. [LEFT] preserves the previous behavior and is the default. */
enum class LyricsAlignment {
    LEFT,
    CENTER,
}

/**
 * Base font weight for lyric lines. [NORMAL] maps to the previous Medium/Bold pairing (inactive vs
 * active); [BOLD] bumps both a notch heavier. Default is [NORMAL].
 */
enum class LyricsFontWeight {
    NORMAL,
    BOLD,
}

/**
 * How the active/current line is emphasized. [PRIMARY] uses the theme accent (previous behavior),
 * the others swap the highlight color. All still scale the active line up per
 * [LyricsSettings.activeLineScale]. Default is [PRIMARY].
 */
enum class LyricsHighlightColor {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

/**
 * The resolved lyrics appearance + behavior settings. Every default reproduces the app's prior
 * hard-coded lyrics rendering so upgrading users see no change until they opt in.
 *
 * @property activeLineScale multiplier applied to the active line's font size (1.0 = no scale-up).
 */
data class LyricsSettings(
    val fontSize: LyricsFontSize = LyricsFontSize.MEDIUM,
    val alignment: LyricsAlignment = LyricsAlignment.LEFT,
    val fontWeight: LyricsFontWeight = LyricsFontWeight.NORMAL,
    val highlightColor: LyricsHighlightColor = LyricsHighlightColor.PRIMARY,
    val activeLineScale: Float = DEFAULT_ACTIVE_LINE_SCALE,
    val autoScroll: Boolean = true,
    val tapToSeek: Boolean = true,
    val dimInactiveLines: Boolean = true,
    val showTranslationByDefault: Boolean = false,
    val showRomanizationByDefault: Boolean = false,
) {
    companion object {
        /** Prior behavior: active line was 20sp vs 17sp base = 20/17 ≈ 1.176x. Kept for exact parity. */
        const val DEFAULT_ACTIVE_LINE_SCALE = 20f / 17f

        /** Slider bounds for [activeLineScale]. 1.0 = flat (no emphasis), 1.6 = strong emphasis. */
        const val MIN_ACTIVE_LINE_SCALE = 1.0f
        const val MAX_ACTIVE_LINE_SCALE = 1.6f
    }
}

/**
 * Pure, framework-free lyric behavior decisions. The presentation layer owns the enum→sp/weight/
 * color mapping (it needs Compose types), but the boolean/behavior logic that has no UI dependency
 * lives here so it can be unit-tested on the JVM.
 */
object LyricsBehavior {

    /**
     * Whether the line at [lineIndex] should be dimmed. A line is dimmed only when [dimInactiveLines]
     * is on and the line is not the active one. The active line ([lineIndex] == [activeIndex]) is
     * never dimmed. When dimming is off, nothing is dimmed.
     */
    fun shouldDimLine(lineIndex: Int, activeIndex: Int, dimInactiveLines: Boolean): Boolean {
        if (!dimInactiveLines) return false
        return lineIndex != activeIndex
    }

    /** Clamp a user-provided active-line scale into the supported range. */
    fun clampActiveLineScale(scale: Float): Float =
        scale.coerceIn(LyricsSettings.MIN_ACTIVE_LINE_SCALE, LyricsSettings.MAX_ACTIVE_LINE_SCALE)
}
