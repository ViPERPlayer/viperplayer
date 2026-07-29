package com.viperplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The `randomizeHomeOrder` Home setting must default to ON, round-trip through the DataStore, and
 * survive a "process restart". Mirrors [OfflineAndAutoDownloadSettingsTest]: a real
 * [SettingsRepositoryImpl] over an in-memory preferences store on a temp file, on the JVM (no Android).
 */
class RandomizeHomeOrderSettingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun openStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun withRepo(block: suspend (SettingsRepositoryImpl) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope)))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun default_isOn() = withRepo { repo ->
        assertTrue("randomizeHomeOrder defaults ON", repo.randomizeHomeOrder.first())
    }

    @Test
    fun roundTrips() = withRepo { repo ->
        repo.setRandomizeHomeOrder(false)
        assertFalse(repo.randomizeHomeOrder.first())
        repo.setRandomizeHomeOrder(true)
        assertTrue(repo.randomizeHomeOrder.first())
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).setRandomizeHomeOrder(false)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertFalse(SettingsRepositoryImpl(openStore(file, secondScope)).randomizeHomeOrder.first())
        } finally {
            secondScope.cancel()
        }
    }
}
