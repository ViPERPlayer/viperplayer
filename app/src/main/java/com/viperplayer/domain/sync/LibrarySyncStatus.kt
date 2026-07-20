package com.viperplayer.domain.sync

/**
 * Persisted status of the most recent LOCAL library sync (the plugin-account → on-device pull run by
 * `LibrarySyncManager`). Aggregate counts across all synced plugins from the last "Sync now", plus
 * when it finished. Pure data, surfaced to the You hub + Account screen.
 *
 * @param lastSyncedAtMs epoch millis the last sync completed, or 0 when never synced.
 * @param songs / albums / artists / playlists items pulled in the last sync run.
 */
data class LibrarySyncStatus(
    val lastSyncedAtMs: Long = 0,
    val songs: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlists: Int = 0,
) {
    val hasSynced: Boolean get() = lastSyncedAtMs > 0
    val total: Int get() = songs + albums + artists + playlists
}
