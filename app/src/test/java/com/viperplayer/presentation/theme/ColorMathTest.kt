package com.viperplayer.presentation.theme

import com.viperplayer.presentation.theme.ColorMath.Swatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure JVM unit tests for the framework-free color math backing the theming polish. */
class ColorMathTest {

    private val eps = 1e-3

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // --- Relative luminance -------------------------------------------------

    @Test
    fun luminanceOfBlackIsZero() {
        assertEquals(0.0, ColorMath.relativeLuminance(ColorMath.BLACK), eps)
    }

    @Test
    fun luminanceOfWhiteIsOne() {
        assertEquals(1.0, ColorMath.relativeLuminance(ColorMath.WHITE), eps)
    }

    @Test
    fun luminanceIgnoresAlpha() {
        // A translucent white and an opaque white have the same (composited-pixel) luminance.
        val translucentWhite = 0x80FFFFFF.toInt()
        assertEquals(
            ColorMath.relativeLuminance(ColorMath.WHITE),
            ColorMath.relativeLuminance(translucentWhite),
            eps
        )
    }

    // --- Contrast ratio -----------------------------------------------------

    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, ColorMath.contrastRatio(ColorMath.BLACK, ColorMath.WHITE), eps)
    }

    @Test
    fun whiteOnBlackIsTwentyOne_symmetric() {
        assertEquals(21.0, ColorMath.contrastRatio(ColorMath.WHITE, ColorMath.BLACK), eps)
    }

    @Test
    fun sameColorIsOne() {
        val teal = argb(0, 105, 109)
        assertEquals(1.0, ColorMath.contrastRatio(teal, teal), eps)
    }

    @Test
    fun knownPairMidGrayOnWhite() {
        // #767676 on white is the canonical WCAG AA boundary (~4.54:1).
        val gray = argb(0x76, 0x76, 0x76)
        assertEquals(4.54, ColorMath.contrastRatio(gray, ColorMath.WHITE), 0.05)
    }

    // --- On-color selection -------------------------------------------------

    @Test
    fun lightBackgroundGetsBlackText() {
        assertEquals(ColorMath.BLACK, ColorMath.onColorFor(ColorMath.WHITE))
        assertEquals(ColorMath.BLACK, ColorMath.onColorFor(argb(0xF0, 0xF0, 0xF0)))
    }

    @Test
    fun darkBackgroundGetsWhiteText() {
        assertEquals(ColorMath.WHITE, ColorMath.onColorFor(ColorMath.BLACK))
        assertEquals(ColorMath.WHITE, ColorMath.onColorFor(argb(0x20, 0x20, 0x20)))
    }

    @Test
    fun onColorAlwaysMeetsAtLeastMinContrast() {
        // For any background, the chosen on-color must beat picking the other one.
        for (v in 0..255 step 15) {
            val bg = argb(v, v, v)
            val chosen = ColorMath.onColorFor(bg)
            val other = if (chosen == ColorMath.BLACK) ColorMath.WHITE else ColorMath.BLACK
            assertTrue(
                "on-color must be the higher-contrast option at v=$v",
                ColorMath.contrastRatio(chosen, bg) >= ColorMath.contrastRatio(other, bg)
            )
        }
    }

    // --- WCAG thresholds ----------------------------------------------------

    @Test
    fun wcagAaBlackOnWhitePasses() {
        assertTrue(ColorMath.meetsWcagAa(ColorMath.BLACK, ColorMath.WHITE))
        assertTrue(ColorMath.meetsWcagAaa(ColorMath.BLACK, ColorMath.WHITE))
    }

    @Test
    fun wcagAaFailsForLowContrast() {
        val lightGray = argb(0xCC, 0xCC, 0xCC)
        assertFalse(ColorMath.meetsWcagAa(lightGray, ColorMath.WHITE))
    }

    @Test
    fun wcagLargeTextThresholdIsLower() {
        // #949494 on white is ~3.03:1 — fails normal AA (4.5) but clears large-text AA (3.0).
        val gray = argb(0x94, 0x94, 0x94)
        assertFalse(ColorMath.meetsWcagAa(gray, ColorMath.WHITE, largeText = false))
        assertTrue(ColorMath.meetsWcagAa(gray, ColorMath.WHITE, largeText = true))
    }

    // --- Best-seed selection ------------------------------------------------

    @Test
    fun bestSeedOfEmptyIsNull() {
        assertNull(ColorMath.bestSeedFrom(emptyList()))
    }

    @Test
    fun bestSeedPrefersVividOverGray() {
        // A large gray background vs a smaller but vivid accent — accent should win.
        val gray = Swatch(argb(0x88, 0x88, 0x88), population = 10_000)
        val vivid = Swatch(argb(0xE0, 0x10, 0x10), population = 2_000)
        assertEquals(vivid.argb, ColorMath.bestSeedFrom(listOf(gray, vivid)))
    }

    @Test
    fun bestSeedPrefersMorePopulousAmongEquallyVivid() {
        // Two DIFFERENT fully-saturated, full-value hues (equal chroma) — the tie must break on
        // population, so the more-populous swatch (a distinct color) wins.
        val smallVivid = Swatch(argb(0xFF, 0x00, 0x00), population = 500)   // pure red
        val bigVivid = Swatch(argb(0x00, 0x00, 0xFF), population = 5_000)   // pure blue
        assertEquals(bigVivid.argb, ColorMath.bestSeedFrom(listOf(smallVivid, bigVivid)))
    }

    @Test
    fun bestSeedReturnsSingletonRegardless() {
        val only = Swatch(argb(0x33, 0x33, 0x33), population = 1)
        assertEquals(only.argb, ColorMath.bestSeedFrom(listOf(only)))
    }

    // --- HSV round-trip / determinism ---------------------------------------

    @Test
    fun hsvRoundTripIsStable() {
        val colors = listOf(
            argb(0x67, 0x50, 0xA4),
            argb(0xB3, 0x26, 0x1E),
            argb(0x00, 0x63, 0x9B),
            argb(0x12, 0x34, 0x56)
        )
        for (c in colors) {
            val hsv = ColorMath.toHsv(c)
            val back = ColorMath.fromHsv(hsv[0], hsv[1], hsv[2])
            // Allow a 1-per-channel rounding tolerance.
            assertChannelClose(c, back, channelShift = 16)
            assertChannelClose(c, back, channelShift = 8)
            assertChannelClose(c, back, channelShift = 0)
        }
    }

    private fun assertChannelClose(expected: Int, actual: Int, channelShift: Int) {
        val e = (expected ushr channelShift) and 0xFF
        val a = (actual ushr channelShift) and 0xFF
        assertTrue("channel@$channelShift expected=$e actual=$a", abs(e - a) <= 1)
    }

    @Test
    fun fromHsvIsDeterministic() {
        // Same inputs must always yield the identical color (scheme determinism guarantee).
        val a = ColorMath.fromHsv(210f, 0.8f, 0.6f)
        val b = ColorMath.fromHsv(210f, 0.8f, 0.6f)
        assertEquals(a, b)
    }

    @Test
    fun fromHsvClampsAndWrapsHue() {
        // Hue wraps mod 360; 360 == 0.
        assertEquals(ColorMath.fromHsv(0f, 1f, 1f), ColorMath.fromHsv(360f, 1f, 1f))
        // Out-of-range saturation/value are clamped, not thrown.
        val clamped = ColorMath.fromHsv(120f, 5f, 5f)
        assertEquals(ColorMath.fromHsv(120f, 1f, 1f), clamped)
    }

    @Test
    fun fromHsvProducesOpaqueColors() {
        val c = ColorMath.fromHsv(200f, 0.5f, 0.5f)
        assertEquals(0xFF, (c ushr 24) and 0xFF)
    }

    // --- Pure-black surface mapping -----------------------------------------

    @Test
    fun pureBlackMappingCoversAllSurfaceRoles() {
        // Every surface-family role must be blackened so no near-black gray leaks on OLED.
        assertEquals(
            ColorMath.SurfaceRole.entries.toSet(),
            ColorMath.PURE_BLACK_SURFACE_ROLES
        )
        assertTrue(
            "container roles must be included",
            ColorMath.PURE_BLACK_SURFACE_ROLES.containsAll(
                listOf(
                    ColorMath.SurfaceRole.SURFACE_CONTAINER_LOW,
                    ColorMath.SurfaceRole.SURFACE_CONTAINER,
                    ColorMath.SurfaceRole.SURFACE_CONTAINER_HIGH,
                    ColorMath.SurfaceRole.SURFACE_CONTAINER_HIGHEST,
                    ColorMath.SurfaceRole.SURFACE_CONTAINER_LOWEST
                )
            )
        )
    }

    @Test
    fun blackHasWhiteOnColor_pureBlackForegroundContrast() {
        // The pure-black surface (black) must carry a white foreground and pass AAA.
        val on = ColorMath.onColorFor(ColorMath.BLACK)
        assertEquals(ColorMath.WHITE, on)
        assertTrue(ColorMath.meetsWcagAaa(on, ColorMath.BLACK))
    }

    @Test
    fun seedFromRealisticPaletteIsNonNull() {
        val swatches = listOf(
            Swatch(argb(0x2A, 0x2A, 0x2A), 8000), // dark background
            Swatch(argb(0xD4, 0x3F, 0x2A), 1500), // warm accent
            Swatch(argb(0xEE, 0xEE, 0xEE), 3000)  // light text
        )
        val seed = ColorMath.bestSeedFrom(swatches)
        assertNotNull(seed)
        // The warm accent is the only chromatic swatch, so it must win.
        assertEquals(argb(0xD4, 0x3F, 0x2A), seed)
    }
}
