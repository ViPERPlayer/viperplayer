package com.viperplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The "restore last queue" (persistent queue) setting gates cold-start queue restoration. It must
 * default to ON (matching the feature-parity target), round-trip through the DataStore, and survive
 * a "process restart" (simulated by reopening a fresh [SettingsRepositoryImpl] over the same file).
 */
class PersistentQueueSettingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Opens a preferences DataStore backed by [file], owned by [scope] (its own "process"). */
    private fun openStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun defaultsToTrue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            assertTrue(repo.persistentQueueEnabled.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun roundTrips() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope))

            repo.setPersistentQueueEnabled(false)
            assertFalse(repo.persistentQueueEnabled.first())

            repo.setPersistentQueueEnabled(true)
            assertTrue(repo.persistentQueueEnabled.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        // "First process": disable persistent queue, then tear the store's scope down.
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).setPersistentQueueEnabled(false)
        // Join, don't just cancel. DataStore keeps a process-wide registry of open files and only
        // deregisters this one when the owning job actually completes, so a bare cancel() races the
        // second open and intermittently trips its "multiple DataStores active for the same file"
        // check (IllegalStateException from FileStorage) on a loaded machine.
        firstJob.cancelAndJoin()

        // "Second process": a fresh store over the same file must still see the disabled value.
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = SettingsRepositoryImpl(openStore(file, secondScope))
            assertFalse(reopened.persistentQueueEnabled.first())
        } finally {
            secondScope.cancel()
        }
    }
}
