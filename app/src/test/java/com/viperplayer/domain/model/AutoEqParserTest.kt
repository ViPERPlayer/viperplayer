package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * JVM unit tests for the pure AutoEq parser + interpolation. Covers GraphicEQ (single-line and
 * per-line), ParametricEQ, malformed/empty input, gain clamping, log-frequency interpolation onto
 * fixed bands (with known values), and preamp extraction.
 */
class AutoEqParserTest {

    private fun success(content: String): AutoEqProfile {
        val result = AutoEqParser.parse(content)
        assertTrue(
            "expected Success but was $result",
            result is AutoEqParser.ParseResult.Success
        )
        return (result as AutoEqParser.ParseResult.Success).profile
    }

    // --- GraphicEQ parsing -----------------------------------------------------------------------

    @Test
    fun graphicEq_singleLine_parsesAllPairs() {
        val profile = success("GraphicEQ: 20 -0.5; 100 -0.6; 1000 -0.7; 10000 -2.5")
        assertEquals(4, profile.points.size)
        assertEquals(20.0, profile.points[0].frequencyHz, 1e-9)
        assertEquals(-0.5, profile.points[0].gainDb, 1e-9)
        assertEquals(10000.0, profile.points[3].frequencyHz, 1e-9)
        assertEquals(-2.5, profile.points[3].gainDb, 1e-9)
        assertNull(profile.preampDb)
    }

    @Test
    fun graphicEq_perLineVariant_parses() {
        val content = """
            GraphicEQ:
            20 -0.5
            100 -0.6
            1000 -0.7
        """.trimIndent()
        val profile = success(content)
        assertEquals(3, profile.points.size)
        assertEquals(-0.6, profile.points[1].gainDb, 1e-9)
    }

    @Test
    fun graphicEq_barePairsWithoutLabel_parses() {
        val profile = success("20 1.0; 200 2.0; 2000 3.0")
        assertEquals(3, profile.points.size)
    }

    @Test
    fun graphicEq_sortsAndDedupesByFrequency() {
        // Out of order, with a duplicate frequency (later value wins).
        val profile = success("GraphicEQ: 1000 -0.7; 20 -0.5; 1000 -0.9; 100 -0.6")
        val freqs = profile.points.map { it.frequencyHz }
        assertEquals(listOf(20.0, 100.0, 1000.0), freqs)
        assertEquals(-0.9, profile.points.last().gainDb, 1e-9)
    }

    @Test
    fun graphicEq_ignoresGarbageTokens() {
        val profile = success("GraphicEQ: 20 -0.5; junk here; 100 -0.6; ;; 1000 abc")
        // "junk here" -> freq NaN, "1000 abc" -> gain NaN; both dropped.
        assertEquals(2, profile.points.size)
    }

    // --- ParametricEQ parsing --------------------------------------------------------------------

    @Test
    fun parametricEq_extractsPreampAndBuildsCurve() {
        val content = """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 105 Hz Gain -5.0 dB Q 0.70
            Filter 2: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.20
        """.trimIndent()
        val profile = success(content)
        assertNotNull(profile.preampDb)
        assertEquals(-6.5, profile.preampDb!!, 1e-9)
        // Curve is a dense grid, not the raw filter list.
        assertTrue(profile.points.size > 10)
        // Around 1000 Hz the +3 dB peaking filter should produce a positive gain.
        assertTrue(profile.interpolateAt(1000.0) > 1.0)
        // Around 105 Hz the -5 dB cut should produce a negative gain.
        assertTrue(profile.interpolateAt(105.0) < -1.0)
    }

    @Test
    fun parametricEq_skipsDisabledFilters() {
        val content = """
            Preamp: 0.0 dB
            Filter 1: OFF PK Fc 105 Hz Gain -12.0 dB Q 0.70
            Filter 2: ON PK Fc 1000 Hz Gain 4.0 dB Q 1.00
        """.trimIndent()
        val profile = success(content)
        // The disabled deep cut at 105 Hz must not appear.
        assertTrue(profile.interpolateAt(105.0) > -1.0)
        assertTrue(profile.interpolateAt(1000.0) > 1.0)
    }

    @Test
    fun parametricEq_shelfFilterWithoutQ_parses() {
        val content = """
            Preamp: -3.0 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.0 dB
        """.trimIndent()
        val profile = success(content)
        assertEquals(-3.0, profile.preampDb!!, 1e-9)
        assertTrue(profile.points.isNotEmpty())
    }

    // --- Error handling --------------------------------------------------------------------------

    @Test
    fun emptyInput_returnsError() {
        assertTrue(AutoEqParser.parse("") is AutoEqParser.ParseResult.Error)
        assertTrue(AutoEqParser.parse("   \n  ") is AutoEqParser.ParseResult.Error)
    }

    @Test
    fun graphicEqWithNoValidPairs_returnsError() {
        val result = AutoEqParser.parse("GraphicEQ: nonsense; more nonsense")
        assertTrue(result is AutoEqParser.ParseResult.Error)
    }

    @Test
    fun parametricEqPreampOnly_noFilters_returnsError() {
        val result = AutoEqParser.parse("Preamp: -6.5 dB")
        assertTrue(result is AutoEqParser.ParseResult.Error)
    }

    @Test
    fun parseOrNull_returnsNullOnError() {
        assertNull(AutoEqParser.parseOrNull(""))
        assertNotNull(AutoEqParser.parseOrNull("GraphicEQ: 20 0; 20000 0"))
    }

    // --- Interpolation onto fixed bands ----------------------------------------------------------

    @Test
    fun interpolation_exactBandFrequency_returnsExactGain() {
        // Points placed exactly on 10-band centers.
        val profile = success("GraphicEQ: 125 -2.0; 250 -4.0; 500 -6.0")
        assertEquals(-2.0, profile.interpolateAt(125.0), 1e-9)
        assertEquals(-4.0, profile.interpolateAt(250.0), 1e-9)
        assertEquals(-6.0, profile.interpolateAt(500.0), 1e-9)
    }

    @Test
    fun interpolation_geometricMidpoint_isArithmeticMeanOfGains() {
        // Log-frequency linear interpolation: at the geometric midpoint of two points, the gain is
        // the arithmetic mean of the endpoint gains. sqrt(100*10000) = 1000.
        val profile = success("GraphicEQ: 100 -2.0; 10000 -8.0")
        assertEquals(-5.0, profile.interpolateAt(1000.0), 1e-6)
    }

    @Test
    fun interpolation_belowAndAboveRange_clampsToEndpoints() {
        val profile = success("GraphicEQ: 100 -2.0; 10000 -8.0")
        assertEquals(-2.0, profile.interpolateAt(20.0), 1e-9)   // below lowest point
        assertEquals(-8.0, profile.interpolateAt(20000.0), 1e-9) // above highest point
    }

    @Test
    fun gainsForBands_mapsOntoTenBandCenters_withKnownMidpoint() {
        val profile = success("GraphicEQ: 100 -2.0; 10000 -8.0")
        val gains = profile.gainsForBands(
            bandFrequenciesHz = IirEqualizerPresets.FREQUENCIES_10_BANDS,
            minGainDb = IirEqualizerPresets.MIN_GAIN_DB,
            maxGainDb = IirEqualizerPresets.MAX_GAIN_DB,
        )
        assertEquals(IirEqualizerPresets.FREQUENCIES_10_BANDS.size, gains.size)
        // Band index 5 is 1000 Hz -> geometric midpoint of (100, 10000) -> mean(-2, -8) = -5.
        assertEquals(-5.0f, gains[5], 1e-4f)
        // 31 Hz band is below 100 Hz -> clamped to first endpoint (-2 dB).
        assertEquals(-2.0f, gains[0], 1e-4f)
        // 16000 Hz band is above 10000 Hz -> clamped to last endpoint (-8 dB).
        assertEquals(-8.0f, gains.last(), 1e-4f)
    }

    // --- Gain clamping ---------------------------------------------------------------------------

    @Test
    fun gainsForBands_clampsToEqRange() {
        // Extreme gains well outside the +/-12 dB EQ range.
        val profile = success("GraphicEQ: 20 -40.0; 20000 40.0")
        val gains = profile.gainsForBands(
            bandFrequenciesHz = IirEqualizerPresets.FREQUENCIES_10_BANDS,
            minGainDb = IirEqualizerPresets.MIN_GAIN_DB,
            maxGainDb = IirEqualizerPresets.MAX_GAIN_DB,
        )
        assertTrue(gains.all { it in IirEqualizerPresets.MIN_GAIN_DB..IirEqualizerPresets.MAX_GAIN_DB })
        assertEquals(IirEqualizerPresets.MIN_GAIN_DB, gains.first(), 1e-4f) // 31 Hz near -40 -> clamped
        assertEquals(IirEqualizerPresets.MAX_GAIN_DB, gains.last(), 1e-4f)  // 16 kHz near +40 -> clamped
    }

    @Test
    fun gainsForBands_foldsPreampIntoEveryBand() {
        val profile = AutoEqProfile(
            points = listOf(
                AutoEqProfile.Point(20.0, 0.0),
                AutoEqProfile.Point(20000.0, 0.0),
            ),
            preampDb = -3.0,
        )
        val gains = profile.gainsForBands(
            bandFrequenciesHz = IirEqualizerPresets.FREQUENCIES_10_BANDS,
            minGainDb = IirEqualizerPresets.MIN_GAIN_DB,
            maxGainDb = IirEqualizerPresets.MAX_GAIN_DB,
        )
        // Flat 0 dB curve + (-3 dB) preamp -> every band -3 dB.
        assertTrue(gains.all { abs(it - (-3.0f)) < 1e-4f })
    }
}
