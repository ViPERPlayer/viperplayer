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
 * The Home "sign in" promo card dismissal flag must be persistent: it defaults to `false`, round-trips
 * through the DataStore, and — critically — survives a "process restart". We simulate the restart by
 * closing the store's scope and reopening a fresh [SettingsRepositoryImpl] over a store pointed at the
 * same file, then asserting the value is still there.
 */
class HomeSignInCardDismissedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Opens a preferences DataStore backed by [file], owned by [scope] (its own "process"). */
    private fun openStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun defaultsToFalse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            assertFalse(repo.homeSignInCardDismissed.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun roundTrips() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepositoryImpl(openStore(tempFolder.newFile("s.preferences_pb"), scope))
            repo.setHomeSignInCardDismissed(true)
            assertTrue(repo.homeSignInCardDismissed.first())

            repo.setHomeSignInCardDismissed(false)
            assertFalse(repo.homeSignInCardDismissed.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        // "First process": dismiss the card, then tear the store's scope down.
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).setHomeSignInCardDismissed(true)
        firstScope.cancel()

        // "Second process": a fresh store over the same file must still see the dismissal.
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = SettingsRepositoryImpl(openStore(file, secondScope))
            assertTrue(reopened.homeSignInCardDismissed.first())
        } finally {
            secondScope.cancel()
        }
    }
}
