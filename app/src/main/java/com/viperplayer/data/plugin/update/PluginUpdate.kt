package com.viperplayer.data.plugin.update

/**
 * An available update for one installed plugin, as surfaced to the UI. Produced by
 * [PluginUpdateManager] from the plugin's fetched [PluginUpdateManifest] joined with its currently
 * installed version.
 */
data class PluginUpdate(
    /** Package name of the plugin (its stable id). */
    val pluginId: String,
    /** The plugin's current on-device versionName, for "1.0 → 1.3.0" display. */
    val installedVersionName: String,
    /** The available build's versionCode (used as the dismissal key). */
    val availableVersionCode: Long,
    /** The available build's human-readable version. */
    val availableVersionName: String,
    /** Where to download the APK. */
    val downloadUrl: String,
    /** Optional release notes. */
    val changelog: String?,
    /** Optional lowercase-hex SHA-256 of the APK for integrity verification. */
    val sha256: String?,
)

/**
 * Progress of a single download+install run for a plugin, exposed as a flow so the UI can show a
 * bar and error/finished states. One in-flight run per plugin at a time.
 */
sealed interface PluginUpdateProgress {
    val pluginId: String

    /** Downloading the APK; [fraction] in 0f..1f, or null when the total size is unknown. */
    data class Downloading(override val pluginId: String, val fraction: Float?) : PluginUpdateProgress

    /** Download finished; the PackageInstaller session is being created / committed. */
    data class Installing(override val pluginId: String) : PluginUpdateProgress

    /** The system install confirmation is being shown to the user (awaiting their action). */
    data class AwaitingUserConfirmation(override val pluginId: String) : PluginUpdateProgress

    /** Install completed successfully. */
    data class Succeeded(override val pluginId: String) : PluginUpdateProgress

    /** The download or install failed / was rejected; [message] is user-presentable. */
    data class Failed(override val pluginId: String, val message: String) : PluginUpdateProgress
}
