package com.viperplayer.domain.audio

/**
 * The active Bluetooth A2DP audio codec of the connected output, as reported by the platform.
 *
 * The [displayName] is what the UI shows; [codecType] is the raw
 * `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` int the platform returned (kept for debugging /
 * diagnostics). LDAC additionally carries a [ldacBitrateLabel] (e.g. "990kbps") when the platform
 * exposes the configured bitrate.
 */
data class BluetoothCodecInfo(
    val codecType: Int,
    val displayName: String,
    /** Human-readable LDAC bitrate, e.g. "990kbps", or null for non-LDAC / unknown quality. */
    val ldacBitrateLabel: String? = null,
) {
    /** Full label for a single-line display, e.g. "LDAC 990kbps" or just "aptX HD". */
    val label: String get() = ldacBitrateLabel?.let { "$displayName $it" } ?: displayName

    companion object {
        // Mirror of android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_* constants. These are
        // stable platform ints; we hard-code them so the pure mapping is testable on the JVM without
        // the (partly @SystemApi/hidden) framework class on the classpath.
        const val SOURCE_CODEC_TYPE_SBC = 0
        const val SOURCE_CODEC_TYPE_AAC = 1
        const val SOURCE_CODEC_TYPE_APTX = 2
        const val SOURCE_CODEC_TYPE_APTX_HD = 3
        const val SOURCE_CODEC_TYPE_LDAC = 4
        const val SOURCE_CODEC_TYPE_LC3 = 5
        const val SOURCE_CODEC_TYPE_APTX_ADAPTIVE = 6
        const val SOURCE_CODEC_TYPE_OPUS = 7
        const val SOURCE_CODEC_TYPE_LHDC_V2 = 8
        const val SOURCE_CODEC_TYPE_LHDC_V3 = 9
        const val SOURCE_CODEC_TYPE_LHDC_V5 = 10
        const val SOURCE_CODEC_TYPE_INVALID = 1_000_000

        // Mirror of BluetoothCodecConfig.CODEC_SPECIFIC_1 LDAC quality markers. The framework encodes
        // the selected LDAC quality index in codecSpecific1; these are the documented values.
        const val LDAC_QUALITY_HIGH = 1000L // 990/909 kbps
        const val LDAC_QUALITY_MID = 1001L  // 660/606 kbps
        const val LDAC_QUALITY_LOW = 1002L  // 330/303 kbps
        const val LDAC_QUALITY_ABR = 1003L  // adaptive bitrate

        /**
         * Maps a raw `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` int to a display name, or null when
         * the codec is invalid/unknown so callers can degrade gracefully.
         */
        fun codecTypeToName(codecType: Int): String? = when (codecType) {
            SOURCE_CODEC_TYPE_SBC -> "SBC"
            SOURCE_CODEC_TYPE_AAC -> "AAC"
            SOURCE_CODEC_TYPE_APTX -> "aptX"
            SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            SOURCE_CODEC_TYPE_LC3 -> "LC3"
            SOURCE_CODEC_TYPE_APTX_ADAPTIVE -> "aptX Adaptive"
            SOURCE_CODEC_TYPE_OPUS -> "Opus"
            SOURCE_CODEC_TYPE_LHDC_V2, SOURCE_CODEC_TYPE_LHDC_V3, SOURCE_CODEC_TYPE_LHDC_V5 -> "LHDC"
            else -> null
        }

        /**
         * Human-readable LDAC bitrate label from the `codecSpecific1` quality marker the framework
         * exposes, or null when it's not a recognised LDAC quality value.
         */
        fun ldacBitrateLabel(codecSpecific1: Long): String? = when (codecSpecific1) {
            LDAC_QUALITY_HIGH -> "990kbps"
            LDAC_QUALITY_MID -> "660kbps"
            LDAC_QUALITY_LOW -> "330kbps"
            LDAC_QUALITY_ABR -> "Adaptive"
            else -> null
        }

        /**
         * Builds a [BluetoothCodecInfo] from a codec-type int (+ optional LDAC `codecSpecific1`
         * quality marker), or null when the codec type is unknown/invalid.
         */
        fun from(codecType: Int, codecSpecific1: Long? = null): BluetoothCodecInfo? {
            val name = codecTypeToName(codecType) ?: return null
            val ldacLabel = if (codecType == SOURCE_CODEC_TYPE_LDAC && codecSpecific1 != null) {
                ldacBitrateLabel(codecSpecific1)
            } else {
                null
            }
            return BluetoothCodecInfo(codecType = codecType, displayName = name, ldacBitrateLabel = ldacLabel)
        }
    }
}
