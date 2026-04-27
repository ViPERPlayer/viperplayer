package com.viperplayer.domain.model

/**
 * Utility object for managing stepped values used in ViPER effects.
 * These stepped values ensure that only specific discrete values can be selected.
 */
object ViperSteppedValues {
    /**
     * Stepped values for Master Limiter output gain.
     * These values are mapped to the native driver's expected range.
     */
    val outputGainValues = listOf(
        0.01f,
        0.05f,
        0.10f,
        0.20f,
        0.30f,
        0.40f,
        0.50f,
        0.60f,
        0.70f,
        0.80f,
        0.90f,
        1.00f,
        1.10f,
        1.20f,
        1.30f,
        1.40f,
        1.50f,
        1.60f,
        1.70f,
        1.80f,
        1.90f,
        2.00f,
    )

    /**
     * Default index for output gain (corresponds to value 1.00, which is index 11).
     */
    const val DEFAULT_OUTPUT_GAIN_INDEX = 11

    /**
     * Stepped values for Master Limiter threshold limit.
     */
    val thresholdLimitValues = listOf(
        0.3f,
        0.5f,
        0.7f,
        0.8f,
        0.9f,
        1.0f,
    )

    /**
     * Default index for threshold limit (corresponds to value 100, which is index 5).
     */
    const val DEFAULT_THRESHOLD_LIMIT_INDEX = 5

    /**
     * Gets the actual value for a given index.
     * @param index The index into the outputGainValues list
     * @return The actual value, or the first value if index is out of bounds
     */
    fun getOutputGainValue(index: Int): Float {
        return outputGainValues[index]
    }

    /**
     * Gets the actual threshold limit value for a given index.
     * @param index The index into the thresholdLimitValues list
     * @return The actual value, or the first value if index is out of bounds
     */
    fun getThresholdLimitValue(index: Int): Float {
        return thresholdLimitValues[index]
    }

    /**
     * Calculates the left and right gain values from output gain index and pan value.
     * @param outputGainIndex The index into the outputGainValues list
     * @param pan The pan value (-1.0 to 1.0, where 0 = center)
     * @return A Pair of (leftGain, rightGain) Float values
     */
    fun calculateLeftRightGains(outputGainIndex: Int, pan: Float): Pair<Float, Float> {
        val gainValue = getOutputGainValue(outputGainIndex)

        // Calculate pan multipliers
        val panL = if (pan <= 0) 1f else (1f - pan)
        val panR = if (pan >= 0) 1f else (1f + pan)

        // Calculate final gains: gain * pan
        val gainL = gainValue * panL
        val gainR = gainValue * panR

        return gainL to gainR
    }
}

