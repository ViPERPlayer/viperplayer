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
 * Defaults + round-trip for the two recommendation-related settings:
 *  - `recommendationsEnabled` must stay **OFF** by default (opt-in; downloads a large on-device model).
 *  - `contributeAnonymizedTaste` now defaults **ON** (task #95) — but remains gated behind the
 *    recommendations opt-in elsewhere, so flipping the default doesn't contribute anything on its own.
 *
 * A real [SettingsRepositoryImpl] over an in-memory preferences store on a temp file (JVM, no Android),
 * mirroring [OfflineAndAutoDownloadSettingsTest].
 */
class RecommendationSettingsDefaultsTest {

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
    fun recommendationsEnabled_defaultsOff() = withRepo { repo ->
        assertFalse("recommendationsEnabled defaults OFF (opt-in)", repo.recommendationsEnabled.first())
    }

    @Test
    fun contributeAnonymizedTaste_defaultsOn() = withRepo { repo ->
        assertTrue("contributeAnonymizedTaste defaults ON (task #95)", repo.contributeAnonymizedTaste.first())
    }

    @Test
    fun recommendationsEnabled_roundTrips() = withRepo { repo ->
        repo.setRecommendationsEnabled(true)
        assertTrue(repo.recommendationsEnabled.first())
        repo.setRecommendationsEnabled(false)
        assertFalse(repo.recommendationsEnabled.first())
    }

    @Test
    fun contributeAnonymizedTaste_roundTrips() = withRepo { repo ->
        // Explicitly opting out must persist as false even though the default is now true.
        repo.setContributeAnonymizedTaste(false)
        assertFalse(repo.contributeAnonymizedTaste.first())
        repo.setContributeAnonymizedTaste(true)
        assertTrue(repo.contributeAnonymizedTaste.first())
    }

    @Test
    fun contributeAnonymizedTaste_optOutSurvivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).setContributeAnonymizedTaste(false)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // The explicit opt-out must NOT be overwritten by the new ON default on reopen.
            assertFalse(SettingsRepositoryImpl(openStore(file, secondScope)).contributeAnonymizedTaste.first())
        } finally {
            secondScope.cancel()
        }
    }
}
