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
         * Build a profile from a set of parametric peaking (PK) filters by evaluating their
         * combined magnitude response on a dense log-frequency grid. This lets a ParametricEQ
         * export be applied to a fixed graphic equalizer.
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
            val points = ArrayList<Point>(steps)
            for (i in 0 until steps) {
                val f = startF * (endF / startF).pow(i.toDouble() / (steps - 1))
                var db = 0.0
                for (filter in filters) {
                    db += filter.magnitudeDbAt(f, sampleRate)
                }
                points.add(Point(f, db))
            }
            return AutoEqProfile(points = points, preampDb = preampDb)
        }
    }
}

/**
 * A single AutoEq ParametricEQ filter. Only peaking (PK) filters are modeled precisely; low/high
 * shelf types are approximated as peaking so they still contribute a sensible correction when
 * resampled onto graphic bands.
 */
data class ParametricFilter(
    val type: FilterType,
    val frequencyHz: Double,
    val gainDb: Double,
    val q: Double,
) {
    enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF }

    /**
     * Magnitude response (dB) of this RBJ peaking biquad at [frequencyHz]. Used to convert a set
     * of parametric filters into a resamplable curve.
     */
    fun magnitudeDbAt(frequencyHz: Double, sampleRate: Int): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * this.frequencyHz / sampleRate
        val qq = if (q <= 0.0) 0.707 else q
        val alpha = sin(w0) / (2.0 * qq)
        val cosW0 = cos(w0)

        // RBJ peaking EQ coefficients.
        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a

        val w = 2.0 * PI * frequencyHz / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2 * w)
        val sin2W = sin(2 * w)

        val numRe = b0 + b1 * cosW + b2 * cos2W
        val numIm = -(b1 * sinW + b2 * sin2W)
        val denRe = a0 + a1 * cosW + a2 * cos2W
        val denIm = -(a1 * sinW + a2 * sin2W)

        val numMag = sqrt(numRe * numRe + numIm * numIm)
        val denMag = sqrt(denRe * denRe + denIm * denIm)
        if (denMag <= 1e-12) return 0.0
        val mag = numMag / denMag
        return if (mag > 1e-9) 20.0 * log10(mag) else -120.0
    }
}
