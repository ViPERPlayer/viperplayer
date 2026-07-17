package com.viperplayer.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ViperEqualizerMath {

    data class FilterCoefficients(
        val b0: Double, val b1: Double, val b2: Double,
        val a0: Double, val a1: Double, val a2: Double
    )

    // RBJ Peaking EQ Coefficients
    private fun calculateCoefficients(
        freq: Double,
        Q: Double,
        gainDb: Double,
        samplingRate: Int
    ): FilterCoefficients {
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / samplingRate
        val alpha = sin(w0) / (2.0 * Q)
        val cosW0 = cos(w0)

        val b0 = 1.0 + alpha * A
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * A
        val a0 = 1.0 + alpha / A
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / A

        return FilterCoefficients(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a0 = 1.0,
            a1 = a1 / a0,
            a2 = a2 / a0
        )
    }

    // Calculates Complex Magnitude Response |H(z)| of a single Biquad
    private fun calculateBiquadResponse(
        coeffs: FilterCoefficients,
        freq: Double,
        samplingRate: Int
    ): Complex {
        val w = 2.0 * PI * freq / samplingRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2 * w)
        val sin2W = sin(2 * w)

        // H(z) = (b0 + b1 z^-1 + b2 z^-2) / (a0 + a1 z^-1 + a2 z^-2)

        val numRe = coeffs.b0 + coeffs.b1 * cosW + coeffs.b2 * cos2W
        val numIm = -(coeffs.b1 * sinW + coeffs.b2 * sin2W)
        val denRe = coeffs.a0 + coeffs.a1 * cosW + coeffs.a2 * cos2W
        val denIm = -(coeffs.a1 * sinW + coeffs.a2 * sin2W)

        // C / D = (Cre + jCim) / (Dre + jDim)
        val denMagSq = denRe * denRe + denIm * denIm
        val re = (numRe * denRe + numIm * denIm) / denMagSq
        val im = (numIm * denRe - numRe * denIm) / denMagSq

        return Complex(re, im)
    }

    data class Complex(val re: Double, val im: Double) {
        operator fun times(other: Complex): Complex {
            return Complex(
                re * other.re - im * other.im,
                re * other.im + im * other.re
            )
        }

        fun magnitude(): Double {
            return sqrt(re * re + im * im)
        }
    }

    fun calculateFrequencyResponse(
        bandCount: Int,
        gains: List<Float>,
        samplingRate: Int = 44100
    ): List<Double> {
        // Correct Q factors matching C++ implementation
        val qFactor = when (bandCount) {
            10 -> 1.414
            15 -> 2.145
            31 -> 4.318
            else -> 1.414
        }

        // Draw the curve at the labeled band center frequencies (IirEqualizerPresets) so it lines up
        // with the band sliders the user sees. These equal the native DSP centers exactly for the 15-
        // and 31-band EQs; on the 10-band the DSP's internal centers are 31.25/62.5 Hz vs the labeled
        // 31/62 Hz — a ≤0.5 Hz difference on the two lowest bands, imperceptible on the log plot.
        val frequencies = IirEqualizerPresets.getFrequencies(bandCount).map { it.toDouble() }

        // Generate pre-calculated coeffs for active bands
        val filters = gains.zip(frequencies).map { (gain, freq) ->
            calculateCoefficients(freq, qFactor, gain.toDouble(), samplingRate)
        }

        val points = mutableListOf<Double>()
        val startFreq = 20.0
        val endFreq = 22000.0
        val steps = 500

        for (i in 0 until steps) {
            // Logarithmic sweep
            val f = startFreq * (endFreq / startFreq).pow(i.toDouble() / (steps - 1))

            // For serial cascade, H_total = H_1 * H_2 * ... * H_N
            // We multiply the complex responses.
            var totalResponse = Complex(1.0, 0.0)

            for (filter in filters) {
                val response = calculateBiquadResponse(filter, f, samplingRate)
                totalResponse = totalResponse * response
            }

            // Convert to dB
            // 20 * log10(|H_total|)
            val mag = totalResponse.magnitude()
            val dB = if (mag > 0.000001) 20.0 * log10(mag) else -120.0
            points.add(dB)
        }

        return points
    }

    // Helper to get X coordinate (0..1) for a frequency on log scale
    fun freqToX(freq: Float): Float {
        val minF = 20.0f
        val maxF = 22000.0f
        return (log10(freq) - log10(minF)) / (log10(maxF) - log10(minF))
    }
}
