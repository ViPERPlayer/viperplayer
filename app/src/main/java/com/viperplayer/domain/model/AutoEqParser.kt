package com.viperplayer.domain.model

/**
 * Pure, Android-free parser for AutoEq headphone-correction profile text files.
 *
 * Supports two AutoEq export formats:
 *
 *  1. **GraphicEQ** — a curve of (frequency Hz, gain dB) pairs, either on a single line
 *     (`GraphicEQ: 20 -0.5; 21 -0.6; 23 -0.7; ...`) or one pair per line. Up to ~127 points.
 *  2. **ParametricEQ** — a `Preamp: -6.5 dB` line plus `Filter N: ON PK Fc 105 Hz Gain -5.0 dB Q 0.70`
 *     lines. Filters are evaluated into a resamplable curve so they can be applied to a fixed-band
 *     graphic equalizer.
 *
 * Parsing is lenient about whitespace, ordering and blank/comment lines, but rejects input that
 * yields no usable data by returning [ParseResult.Error] (never throwing) so callers can surface a
 * user-visible message instead of crashing.
 */
object AutoEqParser {

    sealed interface ParseResult {
        data class Success(val profile: AutoEqProfile) : ParseResult
        data class Error(val error: ParseError) : ParseResult
    }

    /** Typed parse failures; the UI layer maps these to localized messages. */
    enum class ParseError { EMPTY_FILE, NO_GRAPHIC_POINTS, NO_PARAMETRIC_FILTERS }

    /** Convenience: parse and return the profile, or null on any failure. */
    fun parseOrNull(content: String): AutoEqProfile? =
        (parse(content) as? ParseResult.Success)?.profile

    fun parse(content: String): ParseResult {
        if (content.isBlank()) return ParseResult.Error(ParseError.EMPTY_FILE)

        // A GraphicEQ marker anywhere means graphic format; otherwise look for Filter/Preamp lines.
        val hasGraphicMarker = content.contains("GraphicEQ", ignoreCase = true)
        val hasParametric = FILTER_LINE.containsMatchIn(content) ||
            PREAMP_LINE.containsMatchIn(content)

        return when {
            hasGraphicMarker -> parseGraphicEq(content)
            hasParametric -> parseParametricEq(content)
            // No explicit marker: try graphic-style bare "freq gain" pairs as a last resort.
            else -> parseGraphicEq(content)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GraphicEQ
    // ---------------------------------------------------------------------------------------------

    private fun parseGraphicEq(content: String): ParseResult {
        val points = ArrayList<AutoEqProfile.Point>()

        // Strip an optional "GraphicEQ:" label wherever it appears, then split on both semicolons
        // and newlines so we accept the single-line and the per-line variants uniformly.
        val body = GRAPHIC_LABEL.replace(content, " ")
        val tokens = body.split(';', '\n', '\r')

        for (raw in tokens) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            val parts = line.split(WHITESPACE)
            if (parts.size < 2) continue
            val freq = parts[0].toDoubleOrNull() ?: continue
            val gain = parts[1].toDoubleOrNull() ?: continue
            if (freq <= 0.0 || !freq.isFinite() || !gain.isFinite()) continue
            points.add(AutoEqProfile.Point(freq, gain))
        }

        if (points.isEmpty()) {
            return ParseResult.Error(ParseError.NO_GRAPHIC_POINTS)
        }

        val sorted = points.sortedBy { it.frequencyHz }.dedupByFrequency()
        return ParseResult.Success(AutoEqProfile(points = sorted, preampDb = null))
    }

    // ---------------------------------------------------------------------------------------------
    // ParametricEQ
    // ---------------------------------------------------------------------------------------------

    private fun parseParametricEq(content: String): ParseResult {
        val preamp = PREAMP_LINE.find(content)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        val filters = ArrayList<ParametricFilter>()
        for (match in FILTER_LINE.findAll(content)) {
            val enabled = match.groupValues[1].equals("ON", ignoreCase = true)
            if (!enabled) continue
            val typeToken = match.groupValues[2].uppercase()
            val fc = match.groupValues[3].toDoubleOrNull() ?: continue
            // Gain and Q are parsed independently from the filter line so either order — and either
            // omitted — is handled (some tools emit "Q .. Gain ..", shelves may omit Q entirely).
            val line = match.value
            val gain = GAIN_TOKEN.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            // Q is optional (e.g. LSC/HSC shelves may omit it); default to a Butterworth-ish 0.707.
            val q = Q_TOKEN.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.707
            val type = when {
                typeToken.startsWith("LS") -> ParametricFilter.FilterType.LOW_SHELF
                typeToken.startsWith("HS") -> ParametricFilter.FilterType.HIGH_SHELF
                else -> ParametricFilter.FilterType.PEAKING
            }
            if (fc <= 0.0 || !fc.isFinite()) continue
            filters.add(ParametricFilter(type = type, frequencyHz = fc, gainDb = gain, q = q))
        }

        if (filters.isEmpty()) {
            // A file with only a Preamp and no usable filters isn't actionable for a graphic EQ.
            return ParseResult.Error(ParseError.NO_PARAMETRIC_FILTERS)
        }

        return ParseResult.Success(
            AutoEqProfile.fromParametricFilters(filters = filters, preampDb = preamp)
        )
    }

    private fun List<AutoEqProfile.Point>.dedupByFrequency(): List<AutoEqProfile.Point> {
        if (size <= 1) return this
        val out = ArrayList<AutoEqProfile.Point>(size)
        for (p in this) {
            val last = out.lastOrNull()
            if (last != null && last.frequencyHz == p.frequencyHz) {
                out[out.size - 1] = p // keep the later gain for a duplicate frequency
            } else {
                out.add(p)
            }
        }
        return out
    }

    private val WHITESPACE = Regex("\\s+")
    private val GRAPHIC_LABEL = Regex("GraphicEQ\\s*:", RegexOption.IGNORE_CASE)

    // Number sub-pattern accepts an explicit leading + (e.g. "+2.0") as well as - and unsigned,
    // so "Gain +2.0 dB" / "Preamp: +3.2 dB" are not silently dropped to a 0 dB no-op.
    private const val NUMBER = "[+-]?\\d+(?:\\.\\d+)?"

    private val PREAMP_LINE = Regex("Preamp\\s*:\\s*($NUMBER)\\s*dB", RegexOption.IGNORE_CASE)

    // Matches the whole filter line up to Fc; Gain and Q are extracted separately (see below) so
    // they may appear in either order and either may be omitted.
    // Captures: 1=ON/OFF  2=type  3=Fc
    private val FILTER_LINE = Regex(
        "Filter\\s*\\d+\\s*:\\s*(ON|OFF)\\s+([A-Za-z]+)\\s+Fc\\s+($NUMBER)\\s*Hz.*",
        RegexOption.IGNORE_CASE
    )

    // Order-independent Gain / Q extraction from a single filter line.
    private val GAIN_TOKEN = Regex("Gain\\s+($NUMBER)\\s*dB", RegexOption.IGNORE_CASE)
    private val Q_TOKEN = Regex("\\bQ\\s+($NUMBER)", RegexOption.IGNORE_CASE)
}
