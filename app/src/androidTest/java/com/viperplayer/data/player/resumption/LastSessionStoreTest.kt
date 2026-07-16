package com.viperplayer.data.player.resumption

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for [LastSessionStore] round-tripping through the real Preferences DataStore.
 */
@RunWith(AndroidJUnit4::class)
class LastSessionStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LastSessionStore(context)

    private fun sampleSession() = LastSession(
        items = listOf(
            LastSessionItem("testsource", "1", "First", "Artist A", "Album A", "https://art/1.jpg", 180_000L, false),
            LastSessionItem("testsource", "2", "Second", null, null, null, null, true),
        ),
        currentIndex = 1,
        positionMs = 30_000L,
        playWhenReady = true,
        shuffleEnabled = true,
        repeatMode = "ALL",
    )

    @Before
    fun clear() = runBlocking { store.clear() }

    @Test
    fun saveThenLoad_returnsEquivalentSession() = runBlocking {
        val original = sampleSession()
        store.save(original)
        assertEquals(original, store.load())
    }

    @Test
    fun load_whenEmpty_returnsNull() = runBlocking {
        assertNull(store.load())
    }

    @Test
    fun clear_removesPersistedSession() = runBlocking {
        store.save(sampleSession())
        store.clear()
        assertNull(store.load())
    }
}
