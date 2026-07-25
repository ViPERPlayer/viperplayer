package com.viperplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.viperplayer.domain.repository.SEEK_INCREMENT_DEFAULT_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MAX_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MIN_SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The eight new playback/privacy settings must each persist with the documented default and round-trip
 * through the DataStore. Mirrors [HomeSignInCardDismissedTest]: an in-memory preferences store on a
 * temp file, exercised via a real [SettingsRepositoryImpl] on the JVM (no Android).
 */
class PlaybackPrivacySettingsTest {

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
    fun defaults_matchSpec() = withRepo { repo ->
        assertTrue("skipOnError defaults ON", repo.skipOnError.first())
        assertEquals(SEEK_INCREMENT_DEFAULT_SECONDS, repo.seekIncrementSeconds.first())
        assertFalse("stopOnTaskRemoved defaults OFF", repo.stopOnTaskRemoved.first())
        assertFalse("keepScreenOnPlayer defaults OFF", repo.keepScreenOnPlayer.first())
        assertFalse("pauseWhenMuted defaults OFF", repo.pauseWhenMuted.first())
        assertFalse("resumeOnBluetooth defaults OFF", repo.resumeOnBluetooth.first())
        assertFalse("preventDuplicateQueue defaults OFF", repo.preventDuplicateQueue.first())
        assertFalse("blockScreenshots defaults OFF", repo.blockScreenshots.first())
    }

    @Test
    fun booleans_roundTrip() = withRepo { repo ->
        repo.setSkipOnError(false)
        assertFalse(repo.skipOnError.first())
        repo.setSkipOnError(true)
        assertTrue(repo.skipOnError.first())

        repo.setStopOnTaskRemoved(true)
        assertTrue(repo.stopOnTaskRemoved.first())

        repo.setKeepScreenOnPlayer(true)
        assertTrue(repo.keepScreenOnPlayer.first())

        repo.setPauseWhenMuted(true)
        assertTrue(repo.pauseWhenMuted.first())

        repo.setResumeOnBluetooth(true)
        assertTrue(repo.resumeOnBluetooth.first())

        repo.setPreventDuplicateQueue(true)
        assertTrue(repo.preventDuplicateQueue.first())

        repo.setBlockScreenshots(true)
        assertTrue(repo.blockScreenshots.first())
    }

    @Test
    fun seekIncrement_roundTripsAndClamps() = withRepo { repo ->
        repo.setSeekIncrementSeconds(30)
        assertEquals(30, repo.seekIncrementSeconds.first())

        // Below/above the allowed range is clamped on write.
        repo.setSeekIncrementSeconds(1)
        assertEquals(SEEK_INCREMENT_MIN_SECONDS, repo.seekIncrementSeconds.first())

        repo.setSeekIncrementSeconds(999)
        assertEquals(SEEK_INCREMENT_MAX_SECONDS, repo.seekIncrementSeconds.first())
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).apply {
            setSkipOnError(false)
            setPreventDuplicateQueue(true)
            setSeekIncrementSeconds(45)
        }
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = SettingsRepositoryImpl(openStore(file, secondScope))
            assertFalse(reopened.skipOnError.first())
            assertTrue(reopened.preventDuplicateQueue.first())
            assertEquals(45, reopened.seekIncrementSeconds.first())
        } finally {
            secondScope.cancel()
        }
    }
}
