package com.viperplayer.domain.model

/**
 * Plugin information.
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int?,
    val description: String? = null,
    val author: String? = null,
    val settingsActivity: String? = null,
)

/**
 * Plugin capabilities.
 */
data class PluginCapabilities(
    val canSearch: Boolean = true,
    val canBrowse: Boolean = true,
    val hasLibrary: Boolean = true,
    /** The plugin can PUSH local library changes up to the account (two-way sync write side). */
    val hasLibraryWrite: Boolean = false,
    val hasPlaylists: Boolean = true,
    val canSeek: Boolean = true,
    val hasLyrics: Boolean = false,
    val hasHighQuality: Boolean = false,
    val supportsOffline: Boolean = false,
    val hasSettings: Boolean = false
)

/**
 * Connected plugin with its info and capabilities.
 */
data class Plugin(
    val info: PluginInfo,
    val capabilities: PluginCapabilities,
    val isConnected: Boolean = true
)

