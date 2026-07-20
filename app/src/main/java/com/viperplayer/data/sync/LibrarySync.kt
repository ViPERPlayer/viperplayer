package com.viperplayer.data.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * The LOCAL library-sync seam the UI (ViewModels) depends on: observe [syncing] for progress and
 * trigger a full pull with [syncConnectedPlugins]. Backed by [LibrarySyncManager]; the interface keeps
 * the ViewModels launch-and-observe only (MVVM house rule) and trivially fakeable in unit tests
 * without the manager's heavy repository collaborators.
 */
interface LibrarySync {

    /** Plugin ids currently syncing, for the UI to show progress. Empty when idle. */
    val syncing: StateFlow<Set<String>>

    /**
     * Sync every currently-connected plugin's account library into the local library, sequentially.
     * A no-op when no plugins are connected.
     */
    suspend fun syncConnectedPlugins()
}
