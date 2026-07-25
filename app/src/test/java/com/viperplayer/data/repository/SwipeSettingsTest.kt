package com.viperplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.viperplayer.domain.repository.SwipeSensitivity
import com.viperplayer.domain.repository.snapPositionalThresholdFor
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
 * The two swipe-to-change-song settings must persist with the documented defaults and round-trip
 * through the DataStore, and the sensitivity→threshold mapping must be monotonic. Mirrors
 * [PlaybackPrivacySettingsTest]: an in-memory preferences store on a temp file, exercised via a real
 * [SettingsRepositoryImpl] on the JVM (no Android).
 */
class SwipeSettingsTest {

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
    fun defaults_matchSpec() = withRepo { repo ->
        assertTrue("swipeToChangeSong defaults ON", repo.swipeToChangeSong.first())
        assertEquals(SwipeSensitivity.MEDIUM, repo.swipeSensitivity.first())
    }

    @Test
    fun swipeToChangeSong_roundTrips() = withRepo { repo ->
        repo.setSwipeToChangeSong(false)
        assertFalse(repo.swipeToChangeSong.first())
        repo.setSwipeToChangeSong(true)
        assertTrue(repo.swipeToChangeSong.first())
    }

    @Test
    fun swipeSensitivity_roundTrips() = withRepo { repo ->
        repo.setSwipeSensitivity(SwipeSensitivity.LOW)
        assertEquals(SwipeSensitivity.LOW, repo.swipeSensitivity.first())
        repo.setSwipeSensitivity(SwipeSensitivity.HIGH)
        assertEquals(SwipeSensitivity.HIGH, repo.swipeSensitivity.first())
    }

    @Test
    fun survivesProcessRestart() = runBlocking {
        val file = File(tempFolder.root, "swipe.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepositoryImpl(openStore(file, firstScope)).apply {
            setSwipeToChangeSong(false)
            setSwipeSensitivity(SwipeSensitivity.HIGH)
        }
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = SettingsRepositoryImpl(openStore(file, secondScope))
            assertFalse(reopened.swipeToChangeSong.first())
            assertEquals(SwipeSensitivity.HIGH, reopened.swipeSensitivity.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun snapThreshold_isMonotonicAndInRange() {
        val low = snapPositionalThresholdFor(SwipeSensitivity.LOW)
        val medium = snapPositionalThresholdFor(SwipeSensitivity.MEDIUM)
        val high = snapPositionalThresholdFor(SwipeSensitivity.HIGH)

        // A higher threshold means the user must swipe further, i.e. LOW sensitivity needs the most.
        assertTrue("LOW needs a larger swipe than MEDIUM", low > medium)
        assertTrue("MEDIUM needs a larger swipe than HIGH", medium > high)

        // All thresholds are valid page fractions.
        listOf(low, medium, high).forEach { threshold ->
            assertTrue("threshold $threshold in (0,1)", threshold > 0f && threshold < 1f)
        }
        // MEDIUM keeps Compose's default feel.
        assertEquals(0.5f, medium, 0.0001f)
    }
}
