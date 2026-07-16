package com.viperplayer.presentation.theme

import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure, framework-free color math for theming decisions.
 *
 * Colors are represented as 32-bit ARGB integers (the same layout Android's `Color.toArgb()`
 * produces), so every function here is a plain arithmetic transform with no Android dependency and
 * is directly unit-testable on the JVM.
 *
 * References:
 * - WCAG 2.1 relative luminance and contrast-ratio definitions.
 * - Material 3 tonal-palette seed selection.
 */
object ColorMath {

    /** Opaque black (0xFF000000). */
    const val BLACK: Int = 0xFF000000.toInt()

    /** Opaque white (0xFFFFFFFF). */
    const val WHITE: Int = 0xFFFFFFFF.toInt()

    private fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    private fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    private fun blue(argb: Int): Int = argb and 0xFF

    /**
     * Linearizes a single sRGB channel in [0,1] to linear light, per the WCAG / sRGB transfer
     * function.
     */
    private fun linearize(channel: Double): Double {
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * WCAG relative luminance of an (opaque) sRGB color, in [0,1]. Alpha is ignored — contrast is a
     * property of the composited, opaque pixel.
     */
    fun relativeLuminance(argb: Int): Double {
        val r = linearize(red(argb) / 255.0)
        val g = linearize(green(argb) / 255.0)
        val b = linearize(blue(argb) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * WCAG contrast ratio between two opaque colors, in [1.0, 21.0]. Symmetric in its arguments;
     * black-on-white (and white-on-black) is exactly 21.0, and any color against itself is 1.0.
     */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Picks the higher-contrast of [BLACK]/[WHITE] to draw on top of [background] — i.e. the
     * "on" color. Ties (a mid-gray exactly equidistant) resolve to [BLACK], matching the common
     * "prefer dark text" convention.
     */
    fun onColorFor(background: Int): Int {
        val onBlack = contrastRatio(BLACK, background)
        val onWhite = contrastRatio(WHITE, background)
        return if (onWhite > onBlack) WHITE else BLACK
    }

    /** WCAG AA contrast threshold: 3.0 for large text/UI, 4.5 otherwise. */
    fun meetsWcagAa(foreground: Int, background: Int, largeText: Boolean = false): Boolean {
        val threshold = if (largeText) 3.0 else 4.5
        return contrastRatio(foreground, background) >= threshold
    }

    /** WCAG AAA contrast threshold: 4.5 for large text/UI, 7.0 otherwise. */
    fun meetsWcagAaa(foreground: Int, background: Int, largeText: Boolean = false): Boolean {
        val threshold = if (largeText) 4.5 else 7.0
        return contrastRatio(foreground, background) >= threshold
    }

    /**
     * A candidate seed color extracted from album art, paired with how many pixels it covers.
     *
     * @param argb the swatch's representative color.
     * @param population pixel count for the swatch (Palette's population); higher = more dominant.
     */
    data class Swatch(val argb: Int, val population: Int)

    /**
     * Chooses the best seed color from a set of palette swatches for driving a Material 3 scheme.
     *
     * The score rewards colorful, well-populated swatches and penalizes near-gray or near-black/white
     * swatches (which make dull, low-chroma schemes). Concretely each swatch is scored by
     * `chroma * populationWeight`, where chroma is HSV saturation × value and population is
     * normalized against the most-populous swatch (so a huge but drab background never wins over a
     * smaller vivid accent, yet a marginally-more-vivid speck can't beat a dominant color either).
     *
     * Returns `null` for an empty input.
     */
    fun bestSeedFrom(swatches: List<Swatch>): Int? {
        if (swatches.isEmpty()) return null
        val maxPopulation = swatches.maxOf { it.population }.coerceAtLeast(1)
        return swatches.maxByOrNull { swatch ->
            val (_, saturation, value) = rgbToHsv(swatch.argb)
            val chroma = saturation * value
            val populationWeight = swatch.population.toDouble() / maxPopulation
            // Blend so population matters but chroma dominates: a vivid swatch with half the pixels
            // still beats a gray one with all of them.
            chroma * (0.35 + 0.65 * populationWeight)
        }?.argb
    }

    /**
     * Converts an opaque sRGB color to HSV: hue in [0,360), saturation in [0,1], value in [0,1].
     * Alpha is ignored (result is always treated as opaque).
     */
    fun toHsv(argb: Int): FloatArray {
        val (h, s, v) = rgbToHsv(argb)
        return floatArrayOf(h.toFloat(), s.toFloat(), v.toFloat())
    }

    /**
     * Converts HSV (hue [0,360), saturation [0,1], value [0,1]) to an opaque ARGB color. Inputs are
     * clamped to their valid ranges, and hue wraps modulo 360.
     */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Returns (hue in [0,360), saturation in [0,1], value in [0,1]) for an ARGB color. */
    private fun rgbToHsv(argb: Int): Triple<Double, Double, Double> {
        val r = red(argb) / 255.0
        val g = green(argb) / 255.0
        val b = blue(argb) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0.0 -> 0.0
            max == r -> 60.0 * (((g - b) / delta) % 6.0)
            max == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }.let { if (it < 0) it + 360.0 else it }
        val saturation = if (max == 0.0) 0.0 else delta / max
        return Triple(hue, saturation, max)
    }

    /**
     * The surface-family color roles that a "pure black" (AMOLED) dark theme must map to true black
     * so that cards, sheets, bars and elevation tints don't reveal near-black gray boxes on an OLED
     * panel. The corresponding `on*` roles stay untouched (they carry the foreground contrast).
     *
     * Exposed so the pure-black mapping is auditable/testable independently of Compose's
     * `ColorScheme` type.
     */
    enum class SurfaceRole {
        BACKGROUND,
        SURFACE,
        SURFACE_DIM,
        SURFACE_BRIGHT,
        SURFACE_CONTAINER_LOWEST,
        SURFACE_CONTAINER_LOW,
        SURFACE_CONTAINER,
        SURFACE_CONTAINER_HIGH,
        SURFACE_CONTAINER_HIGHEST,
    }

    /**
     * The full set of surface roles blackened by the pure-black mapping. Kept as the single source of
     * truth for both the runtime [pureDark]-style transform and its test.
     */
    val PURE_BLACK_SURFACE_ROLES: Set<SurfaceRole> = SurfaceRole.entries.toSet()
}
