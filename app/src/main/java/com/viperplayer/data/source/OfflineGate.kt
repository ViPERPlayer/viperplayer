package com.viperplayer.data.source

import com.viperplayer.domain.model.MediaId.Companion.LOCAL_PLUGIN_ID

/**
 * Pure decision for offline-mode gating at the plugin fetch boundary: should a fetch to [pluginId] be
 * BLOCKED (rejected before any network round-trip) given the current [offlineMode] setting?
 *
 * Blocked only when ALL of:
 * - [offlineMode] is on (the user opted into offline mode), and
 * - [allowOffline] is false (this call site is a user-facing browse/search/detail read, not a
 *   push/sync write or a stream resolution that may still serve downloaded content), and
 * - [pluginId] is a REMOTE plugin — the embedded on-device local library ([LOCAL_PLUGIN_ID]) is
 *   inherently offline and is never gated.
 *
 * Android-free so it can be unit-tested directly; [PluginDataSource.source] applies it.
 */
fun shouldBlockForOffline(pluginId: String, offlineMode: Boolean, allowOffline: Boolean): Boolean =
    offlineMode && !allowOffline && pluginId != LOCAL_PLUGIN_ID
