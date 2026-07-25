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
 * The two new downloads/offline settings — `offlineMode` and `autoDownloadOnLike` — must each default
 * to OFF, round-trip through the DataStore, and survive a "process restart". Mirrors
 * [HomeSignInCardDismissedTest]: a real [SettingsRepositoryImpl] over an in-memory preferences store on
 * a temp file, exercised on the JVM (no Android).
 */
class OfflineAndAutoDownloadSettingsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun openStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    /** Runs [block] with a fresh repository over its own temp store, tearing the scope down after. */
    private fun withRepo(block: suspend (SettingsRepositoryImpl) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope)))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaults_areOff() = withRepo { repo ->
        assertFalse("offlineMode defaults OFF", repo.offlineMode.first())
        assertFalse("autoDownloadOnLike defaults OFF", repo.autoDownloadOnLike.first())
    }

    @Test
    fun offlineMode_roundTrips() = withRepo { repo ->
        repo.setOfflineMode(true)
        assertTrue(repo.offlineMode.first())
        repo.setOfflineMode(false)
        assertFalse(repo.offlineMode.first())
    }

    @Test
    fun autoDownloadOnLike_roundTrips() = withRepo { repo ->
        repo.setAutoDownloadOnLike(true)
        assertTrue(repo.autoDownloadOnLike.first())
        repo.setAutoDownloadOnLike(false)
        assertFalse(repo.autoDownloadOnLike.first())
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).apply {
            setOfflineMode(true)
            setAutoDownloadOnLike(true)
        }
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = SettingsRepositoryImpl(openStore(file, secondScope))
            assertTrue(reopened.offlineMode.first())
            assertTrue(reopened.autoDownloadOnLike.first())
        } finally {
            secondScope.cancel()
        }
    }
}
