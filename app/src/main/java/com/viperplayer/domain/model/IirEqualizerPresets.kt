package com.viperplayer.domain.model

object IirEqualizerPresets {
    // 10 Bands
    val FREQUENCIES_10_BANDS = listOf(
        31.0f, 62.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
    )

    // 15 Bands
    val FREQUENCIES_15_BANDS = listOf(
        25.0f,
        40.0f,
        63.0f,
        100.0f,
        160.0f,
        250.0f,
        400.0f,
        630.0f,
        1000.0f,
        1600.0f,
        2500.0f,
        4000.0f,
        6300.0f,
        10000.0f,
        16000.0f
    )

    // 31 Bands
    val FREQUENCIES_31_BANDS = listOf(
        20.0f,
        25.0f,
        31.5f,
        40.0f,
        50.0f,
        63.0f,
        80.0f,
        100.0f,
        125.0f,
        160.0f,
        200.0f,
        250.0f,
        315.0f,
        400.0f,
        500.0f,
        630.0f,
        800.0f,
        1000.0f,
        1250.0f,
        1600.0f,
        2000.0f,
        2500.0f,
        3150.0f,
        4000.0f,
        5000.0f,
        6300.0f,
        8000.0f,
        10000.0f,
        12500.0f,
        16000.0f,
        20000.0f
    )

    fun getFrequencies(bandCount: Int): List<Float> {
        return when (bandCount) {
            10 -> FREQUENCIES_10_BANDS
            15 -> FREQUENCIES_15_BANDS
            31 -> FREQUENCIES_31_BANDS
            else -> FREQUENCIES_10_BANDS
        }
    }

    private fun createFlat(bands: Int): List<Float> = List(bands) { 0f }

    private fun createBassBoost(bands: Int): List<Float> {
        val freqs = getFrequencies(bands)
        return freqs.map { freq ->
            if (freq <= 100) 6f else if (freq <= 250) 3f else 0f
        }
    }

    private fun createTrebleBoost(bands: Int): List<Float> {
        val freqs = getFrequencies(bands)
        return freqs.map { freq ->
            if (freq >= 4000) 4f else 0f
        }
    }

    private fun createVocalBoost(bands: Int): List<Float> {
        val freqs = getFrequencies(bands)
        return freqs.map { freq ->
            if (freq in 200.0..4000.0) 3f else 0f
        }
    }

    private fun createRock(bands: Int): List<Float> {
        // Simple V-shape
        val freqs = getFrequencies(bands)
        return freqs.map { freq ->
            when {
                freq < 120 -> 4f
                freq > 4000 -> 3f
                freq in 500.0..2000.0 -> -2f
                else -> 0f
            }
        }
    }

    val PRESETS = listOf("Flat", "Bass Boost", "Treble Boost", "Vocal Boost", "Rock")

    fun getPresetGains(preset: String, bandCount: Int): List<Float> {
        return when (preset) {
            "Flat" -> createFlat(bandCount)
            "Bass Boost" -> createBassBoost(bandCount)
            "Treble Boost" -> createTrebleBoost(bandCount)
            "Vocal Boost" -> createVocalBoost(bandCount)
            "Rock" -> createRock(bandCount)
            else -> createFlat(bandCount)
        }
    }
}
