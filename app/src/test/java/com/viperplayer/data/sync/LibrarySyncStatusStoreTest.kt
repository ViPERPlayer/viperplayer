package com.viperplayer.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [LibrarySyncStatusStore] must window on each RUN's START timestamp, not the last record. Records
 * from one "Sync now" (all within [RUN_WINDOW_MS] of the run start) accumulate; a later record opens
 * a new run and resets the totals — even if it lands within the window of the *previous run's last*
 * record. This last case is the regression: back-to-back runs used to pile onto each other.
 */
class LibrarySyncStatusStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun openStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun defaultsToNotSynced() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = LibrarySyncStatusStore(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            val status = store.status.first()
            assertFalse(status.hasSynced)
            assertEquals(0, status.songs)
            assertEquals(0, status.albums)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recordsWithinTheSameRunAccumulate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = LibrarySyncStatusStore(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            val start = 1_000_000L
            store.record(SyncResult(songs = 10, albums = 2), start)
            store.record(SyncResult(songs = 5, albums = 1), start + 30_000L)

            val status = store.status.first()
            assertEquals(15, status.songs)
            assertEquals(3, status.albums)
            assertEquals(start + 30_000L, status.lastSyncedAtMs)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aNewRunBeyondTheWindowResetsCounts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = LibrarySyncStatusStore(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            val firstRun = 1_000_000L
            store.record(SyncResult(songs = 10, albums = 2), firstRun)

            // Well past RUN_WINDOW_MS (5 min) => new run, totals reset.
            val secondRun = firstRun + 10 * 60 * 1000L
            store.record(SyncResult(songs = 3, albums = 1), secondRun)

            val status = store.status.first()
            assertEquals(3, status.songs)
            assertEquals(1, status.albums)
        } finally {
            scope.cancel()
        }
    }

    /**
     * The regression: two back-to-back runs, each near the window edge, must not accumulate. Anchoring
     * on the last RECORD would slide the window forward and merge them; anchoring on the run START keeps
     * the second run separate.
     */
    @Test
    fun backToBackRunsDoNotDoubleCount() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = LibrarySyncStatusStore(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            val window = 5 * 60 * 1000L

            // Run 1 starts at t=0, its second plugin records near the window edge.
            store.record(SyncResult(songs = 10), 0L)
            store.record(SyncResult(songs = 10), window - 1_000L)

            // Run 2 starts just past run 1's START window, but within the window of run 1's LAST record.
            // It must open a fresh run and reset, not pile onto run 1's 20.
            store.record(SyncResult(songs = 4), window + 1_000L)

            val status = store.status.first()
            assertEquals(4, status.songs)
        } finally {
            scope.cancel()
        }
    }
}
