package com.viperplayer.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A parsed AutoEq headphone-correction profile.
 *
 * The profile is stored as a frequency-response curve: a list of ascending
 * (frequency Hz, gain dB) points that describe the target correction. A profile may also carry a
 * [preampDb] (the "Preamp" value from an AutoEq ParametricEQ export, or a derived value that keeps
 * the boosted curve from clipping).
 *
 * This class is intentionally pure Kotlin with **no Android dependencies** so it is JVM
 * unit-testable. Parsing lives in [AutoEqParser]; applying the curve to a fixed-band graphic
 * equalizer is done via [gainsForBands].
 */
data class AutoEqProfile(
    /** Ascending (frequency Hz, gain dB) points describing the correction curve. */
    val points: List<Point>,
    /** Optional preamp gain in dB (negative to attenuate). Null when the profile carries none. */
    val preampDb: Double? = null,
) {
    data class Point(val frequencyHz: Double, val gainDb: Double)

    init {
        require(points.isNotEmpty()) { "AutoEqProfile must contain at least one point" }
    }

    /**
     * Resample this profile's correction curve onto the given fixed graphic-equalizer band
     * center-frequencies, returning one gain (dB) per band.
     *
     * Interpolation is linear in dB over a log-frequency axis (the natural axis for audio EQ).
     * Band frequencies below the lowest / above the highest profile point are clamped to the
     * nearest endpoint gain (no extrapolation). The optional [preampDb] is folded into every band
     * so the applied curve matches the profile's intended level, and the result is clamped to
     * [[minGainDb], [maxGainDb]].
     */
    fun gainsForBands(
        bandFrequenciesHz: List<Float>,
        minGainDb: Float,
        maxGainDb: Float,
    ): List<Float> {
        val preamp = preampDb ?: 0.0
        return bandFrequenciesHz.map { bandFreq ->
            val gain = interpolateAt(bandFreq.toDouble()) + preamp
            gain.coerceIn(minGainDb.toDouble(), maxGainDb.toDouble()).toFloat()
        }
    }

    /**
     * Log-frequency linear interpolation of the curve's dB gain at [frequencyHz]. Values outside
     * the profile's range are clamped to the nearest endpoint.
     */
    fun interpolateAt(frequencyHz: Double): Double {
        val first = points.first()
        val last = points.last()
        if (frequencyHz <= first.frequencyHz) return first.gainDb
        if (frequencyHz >= last.frequencyHz) return last.gainDb

        // Binary/linear search for the bracketing pair. Points are guaranteed ascending.
        var lower = first
        var upper = last
        for (i in 1 until points.size) {
            if (points[i].frequencyHz >= frequencyHz) {
                lower = points[i - 1]
                upper = points[i]
                break
            }
        }

        val logF = ln(frequencyHz)
        val logLo = ln(lower.frequencyHz)
        val logHi = ln(upper.frequencyHz)
        if (logHi == logLo) return lower.gainDb
        val t = (logF - logLo) / (logHi - logLo)
        return lower.gainDb + t * (upper.gainDb - lower.gainDb)
    }

    companion object {
        /**
         * Build a profile from a set of parametric filters (peaking PK plus low/high shelf
         * LSC/HSC) by evaluating their combined magnitude response on a dense log-frequency grid.
         * This lets a ParametricEQ export be applied to a fixed graphic equalizer.
         */
        fun fromParametricFilters(
            filters: List<ParametricFilter>,
            preampDb: Double?,
            sampleRate: Int = 48000,
        ): AutoEqProfile {
            // Dense log grid from 20 Hz to 20 kHz for accurate resampling later.
            val steps = 256
            val startF = 20.0
            val endF = 20000.0
            // Precompute each filter's coefficients once (they don't depend on the eval frequency).
            val evaluators = filters.map { it.magnitudeEvaluator(sampleRate) }
            val points = ArrayList<Point>(steps)
            for (i in 0 until steps) {
                val f = startF * (endF / startF).pow(i.toDouble() / (steps - 1))
                var db = 0.0
                for (eval in evaluators) {
                    db += eval(f)
                }
                points.add(Point(f, db))
            }
            return AutoEqProfile(points = points, preampDb = preampDb)
        }
    }
}

/**
 * A single AutoEq ParametricEQ filter. Peaking (PK) and low/high shelf (LSC/HSC) types are each
 * modeled with their own RBJ Audio-EQ-Cookbook biquad, evaluated as a digital transfer function
 * at the given sample rate. All three types share the same complex `|H(e^jw)|` magnitude routine;
 * only the biquad coefficients differ by type — so a shelf keeps its plateau instead of collapsing
 * into a peaking bump.
 */
data class ParametricFilter(
    val type: FilterType,
    val frequencyHz: Double,
    val gainDb: Double,
    val q: Double,
) {
    enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF }

    /** RBJ biquad coefficients (normalized-free: caller uses the raw b/a). */
    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a0: Double, val a1: Double, val a2: Double,
    )

    /**
     * Magnitude response (dB) of this RBJ biquad at [frequencyHz]. The biquad coefficients are
     * selected by [type] (peaking vs. low/high shelf); the magnitude is then computed from the
     * shared digital `|H(e^jw)|` evaluator at [sampleRate]. Used to convert a set of parametric
     * filters into a resamplable curve.
     */
    fun magnitudeDbAt(frequencyHz: Double, sampleRate: Int): Double =
        magnitudeDbFrom(coefficients(sampleRate), frequencyHz, sampleRate)

    /**
     * Returns a magnitude(dB)-at-frequency function with this filter's (frequency-independent)
     * coefficients computed ONCE. Callers evaluating the response over many grid frequencies should
     * use this instead of [magnitudeDbAt] so the coefficients aren't recomputed per frequency.
     */
    fun magnitudeEvaluator(sampleRate: Int): (Double) -> Double {
        val coeffs = coefficients(sampleRate)
        return { frequencyHz -> magnitudeDbFrom(coeffs, frequencyHz, sampleRate) }
    }

    private fun magnitudeDbFrom(coeffs: Biquad, frequencyHz: Double, sampleRate: Int): Double {
        val mag = magnitudeAt(coeffs, frequencyHz, sampleRate)
        if (mag <= 0.0) return 0.0
        return if (mag > 1e-9) 20.0 * log10(mag) else -120.0
    }

    /** RBJ Audio-EQ-Cookbook biquad coefficients for this filter's [type]. */
    private fun coefficients(sampleRate: Int): Biquad {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequencyHz / sampleRate
        val qq = if (q <= 0.0) 0.707 else q
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        return when (type) {
            FilterType.PEAKING -> {
                val alpha = sinW0 / (2.0 * qq)
                Biquad(
                    b0 = 1.0 + alpha * a,
                    b1 = -2.0 * cosW0,
                    b2 = 1.0 - alpha * a,
                    a0 = 1.0 + alpha / a,
                    a1 = -2.0 * cosW0,
                    a2 = 1.0 - alpha / a,
                )
            }

            FilterType.LOW_SHELF -> {
                // alpha uses the shelf form: sin(w0)/2 * sqrt((A + 1/A)(1/Q - 1) + 2).
                // Clamp the radicand: for very high-Q shelves (Q well above real AutoEq exports)
                // (A + 1/A)(1/Q - 1) + 2 can go negative, which would yield sqrt(NaN) and poison
                // the whole summed curve. Real shelves use Q ~= 0.7 so this is a safety net.
                val alpha = sinW0 / 2.0 * sqrt(((a + 1.0 / a) * (1.0 / qq - 1.0) + 2.0).coerceAtLeast(0.0))
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                Biquad(
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha),
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0),
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha),
                    a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha,
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0),
                    a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha,
                )
            }

            FilterType.HIGH_SHELF -> {
                // Clamp the radicand: for very high-Q shelves (Q well above real AutoEq exports)
                // (A + 1/A)(1/Q - 1) + 2 can go negative, which would yield sqrt(NaN) and poison
                // the whole summed curve. Real shelves use Q ~= 0.7 so this is a safety net.
                val alpha = sinW0 / 2.0 * sqrt(((a + 1.0 / a) * (1.0 / qq - 1.0) + 2.0).coerceAtLeast(0.0))
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                Biquad(
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha),
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0),
                    b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha),
                    a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha,
                    a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0),
                    a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha,
                )
            }
        }
    }

    /**
     * Digital biquad magnitude `|H(e^jw)|` (linear, not dB) at [frequencyHz], evaluating
     * H(z) = (b0 + b1 z^-1 + b2 z^-2) / (a0 + a1 z^-1 + a2 z^-2) at z = e^{jw}. Shared by every
     * filter type so peaking and shelves use the exact same magnitude reduction.
     */
    private fun magnitudeAt(c: Biquad, frequencyHz: Double, sampleRate: Int): Double {
        val w = 2.0 * PI * frequencyHz / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2 * w)
        val sin2W = sin(2 * w)

        val numRe = c.b0 + c.b1 * cosW + c.b2 * cos2W
        val numIm = -(c.b1 * sinW + c.b2 * sin2W)
        val denRe = c.a0 + c.a1 * cosW + c.a2 * cos2W
        val denIm = -(c.a1 * sinW + c.a2 * sin2W)

        val numMag = sqrt(numRe * numRe + numIm * numIm)
        val denMag = sqrt(denRe * denRe + denIm * denIm)
        if (denMag <= 1e-12) return 0.0
        return numMag / denMag
    }
}
