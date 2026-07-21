package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of the user's library + settings + ViPER presets, exported to / restored
 * from a single JSON file.
 *
 * Scope: DB-level data + settings + presets only. Binary assets (downloaded audio, cached artwork,
 * ViPER DDC/IR files on disk) are intentionally NOT included — only their DB references are.
 */
@Serializable
data class BackupBundle(
    val version: Int = CURRENT_VERSION,
    val exportedAtMs: Long = 0L,
    /** Convenience index: MediaId.encode() of every liked song. Also encoded in [songs]. */
    val likedSongIds: List<String> = emptyList(),
    /** Convenience index: MediaId.encode() of every saved song. Also encoded in [songs]. */
    val savedSongIds: List<String> = emptyList(),
    /** All liked + saved songs, with enough columns to fully re-create the row. */
    val songs: List<BackupSong> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
    val viperPresets: List<BackupViperPreset> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * A single song row. [pluginId] + [sourceId] together form the [MediaId] used to key the row.
 */
@Serializable
data class BackupSong(
    val pluginId: String,
    val sourceId: String,
    val title: String,
    val artistNames: List<String> = emptyList(),
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val isExplicit: Boolean = false,
    val isVideo: Boolean = false,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
)

@Serializable
data class BackupPlaylist(
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val ownerName: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    /** Ordered track list; each references a [BackupSong] by pluginId/sourceId. */
    val songs: List<BackupSong> = emptyList(),
)

/**
 * App settings. Enums are stored as their `.name` string so an unknown value on restore can be
 * skipped without crashing.
 */
@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val dynamicThemeMode: String? = null,
    val pureBlack: Boolean? = null,
    val audioQuality: String? = null,
    val historyDuration: String? = null,
    val skipSilence: Boolean? = null,
    val replayGainEnabled: Boolean? = null,
    val replayGainPreampDb: Float? = null,
    val replayGainAlbumMode: Boolean? = null,
    val dspBypass: Boolean? = null,
    val autoLoadMore: Boolean? = null,
    val showExplicitContent: Boolean? = null,
    val maxSongCacheSize: Long? = null,
    val maxImageCacheSize: Long? = null,
    /** Custom accent seed (packed ARGB), or null for the brand default. Appended for compatibility. */
    val accentColor: Int? = null,
)

/**
 * A ViPER preset. All effect settings from [com.viperplayer.data.local.entity.ViperPresetEntity]
 * are captured except the auto-generated [id] and [deviceId] association (a restored preset is
 * re-created as a fresh row and re-keyed by name).
 *
 * DDC coefficients are round-tripped in [ddcCoeffs]. The on-disk DDC/IR files themselves
 * (referenced by [viperDdcSelectedFile] / [convolverImpulseResponse]) are NOT backed up — those
 * paths are preserved as-is and may be stale after a restore onto a different device.
 */
@Serializable
data class BackupViperPreset(
    val name: String,
    val deviceId: String,
    val enabled: Boolean,
    val masterLimiterOutputGain: Int,
    val masterLimiterOutputPan: Float,
    val masterLimiterThresholdLimit: Int,
    val spectrumExtensionEnabled: Boolean,
    val spectrumExtensionStrength: Int,
    val fieldSurroundEnabled: Boolean,
    val fieldSurroundStrength: Int,
    val fieldSurroundMidImageStrength: Int,
    val differentialSurroundEnabled: Boolean,
    val differentialSurroundDelay: Int,
    val dynamicSystemEnabled: Boolean,
    val dynamicSystemDeviceType: String,
    val dynamicSystemBassStrength: Int,
    val tubeSimulatorEnabled: Boolean,
    val viperBassEnabled: Boolean,
    val viperBassMode: Int,
    val viperBassFrequency: Int,
    val viperBassGain: Int,
    val viperClarityEnabled: Boolean,
    val viperClarityMode: Int,
    val viperClarityGain: Int,
    val auditorySystemProtectionEnabled: Boolean,
    val auditorySystemProtectionLevel: Int,
    val analogXEnabled: Boolean,
    val analogXLevel: Int,
    val speakerOptimizationEnabled: Boolean,
    val viperDdcEnabled: Boolean,
    val viperDdcSelectedFile: String? = null,
    val iirEqualizerEnabled: Boolean,
    val iirEqualizerBandCount: Int,
    val iirEqualizerPreset: String,
    val iirEqualizerBandGains: String,
    val playbackGainEnabled: Boolean,
    val playbackGainStrength: Int,
    val playbackGainMaxGain: Int,
    val playbackGainOutputThreshold: Float,
    val convolverEnabled: Boolean,
    val convolverImpulseResponse: String? = null,
    val convolverCrossChannel: Int,
    val fetCompressorEnabled: Boolean,
    val fetCompressorThreshold: Int,
    val fetCompressorRatio: Int,
    val fetCompressorKnee: Int,
    val fetCompressorAutoKnee: Boolean,
    val fetCompressorGain: Int,
    val fetCompressorAutoGain: Boolean,
    val fetCompressorAttack: Int,
    val fetCompressorAutoAttack: Boolean,
    val fetCompressorRelease: Int,
    val fetCompressorAutoRelease: Boolean,
    val fetCompressorKneeMulti: Int,
    val fetCompressorMaxAttack: Int,
    val fetCompressorMaxRelease: Int,
    val fetCompressorCrest: Int,
    val fetCompressorAdapt: Int,
    val fetCompressorNoClip: Boolean,
    val headphoneSurroundEnabled: Boolean,
    val headphoneSurroundLevel: Int,
    val reverberationEnabled: Boolean,
    val reverberationRoomSize: Int,
    val reverberationWidth: Int,
    val reverberationDamp: Int,
    val reverberationWet: Int,
    val reverberationDry: Int,
    val ddcCoeffs: List<BackupDdcCoeff> = emptyList(),
)

@Serializable
data class BackupDdcCoeff(
    val sampleRate: Int,
    val index: Int,
    val value: Float,
)
