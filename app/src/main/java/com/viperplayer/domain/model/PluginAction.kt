package com.viperplayer.domain.model

/** The kind of manual step a plugin needs from the user. Mirrors the SDK's ActionType. */
enum class PluginActionType { PERMISSION, LOGIN, VERIFICATION, SETUP, UNKNOWN }

/**
 * A pending user action reported by a connected plugin (grant a permission, sign in, pass a
 * verification...), joined with the plugin identity the UI needs to display and resolve it.
 *
 * Resolution order: [permission] (embedded plugin — host requests it directly), then [activity],
 * then [settingsActivity].
 */
data class PluginPendingAction(
    val pluginId: String,
    val pluginName: String,
    val actionId: String,
    val type: PluginActionType,
    val title: String,
    val description: String? = null,
    /** FQCN of the resolving activity inside the plugin's package. */
    val activity: String? = null,
    /** Android permission the host can request directly when the plugin is embedded. */
    val permission: String? = null,
    /** The plugin's settings activity, as a resolution fallback. */
    val settingsActivity: String? = null,
) {
    /** Stable identity for dedup/dismissal tracking. */
    val key: String get() = "$pluginId/$actionId"
}
