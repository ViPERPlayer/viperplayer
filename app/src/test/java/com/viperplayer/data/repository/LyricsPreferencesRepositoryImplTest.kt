package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.LyricsPrefsDao
import com.viperplayer.data.local.entity.LyricsPrefsEntity
import com.viperplayer.data.lyrics.LyricsJson
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.LyricsWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the lyrics-preferences persistence layer: the [LyricsPrefsDao] single-row
 * upsert/prune semantics that back the repository, plus the [LyricsJson] (de)serialization used to
 * store a manual override. Driven against a fake in-memory DAO with plain string keys (the repository
 * simply keys these by `MediaId.encode()`, whose `Uri`-based encoding isn't available on the JVM).
 *
 * Covers: offset persist/observe, offset reset pruning the row, override persist/observe through
 * [LyricsJson], clearing an override, and offset + override kept independently on one key.
 */
class LyricsPreferencesRepositoryImplTest {

    /** In-memory DAO honoring the primary-key upsert and the writeOrPrune default-decay semantics. */
    private class FakeLyricsPrefsDao : LyricsPrefsDao {
        private val rows = MutableStateFlow<Map<String, LyricsPrefsEntity>>(emptyMap())

        override fun observe(mediaId: String): Flow<LyricsPrefsEntity?> = rows.map { it[mediaId] }
        override suspend fun get(mediaId: String): LyricsPrefsEntity? = rows.value[mediaId]
        override suspend fun upsert(entity: LyricsPrefsEntity) {
            rows.value = rows.value + (entity.mediaId to entity)
        }
        override suspend fun delete(mediaId: String) {
            rows.value = rows.value - mediaId
        }
    }

    private val key = "t=local&s=song-1"

    private val syncedLyrics = Lyrics(
        synced = true,
        lines = listOf(
            LyricsLine(startMs = 0, text = "hello", words = listOf(LyricsWord(0, "hello"))),
            LyricsLine(startMs = 1000, text = "world"),
        ),
        plainText = null,
    )

    private suspend fun LyricsPrefsDao.offsetOf(key: String): Long = observe(key).first()?.offsetMs ?: 0L
    private suspend fun LyricsPrefsDao.overrideOf(key: String): Lyrics? =
        observe(key).first()?.overrideJson?.let { LyricsJson.decode(it) }

    @Test
    fun offset_roundTrips() = runTest {
        val dao = FakeLyricsPrefsDao()
        assertEquals(0L, dao.offsetOf(key))

        dao.setOffset(key, 750)
        assertEquals(750L, dao.offsetOf(key))

        dao.setOffset(key, -1200)
        assertEquals(-1200L, dao.offsetOf(key))
    }

    @Test
    fun offset_resetToZero_prunesRow() = runTest {
        val dao = FakeLyricsPrefsDao()
        dao.setOffset(key, 500)
        assertEquals(500L, dao.offsetOf(key))

        dao.setOffset(key, 0)
        // A default-only row is pruned, so both views read empty.
        assertNull(dao.get(key))
        assertEquals(0L, dao.offsetOf(key))
        assertNull(dao.overrideOf(key))
    }

    @Test
    fun override_roundTripsSyncedLyrics() = runTest {
        val dao = FakeLyricsPrefsDao()
        assertNull(dao.overrideOf(key))

        dao.setOverride(key, LyricsJson.encode(syncedLyrics))
        val restored = dao.overrideOf(key)
        requireNotNull(restored)
        assertTrue(restored.synced)
        assertEquals(2, restored.lines.size)
        assertEquals("hello", restored.lines[0].text)
        assertEquals(0L, restored.lines[0].startMs)
        assertEquals(1, restored.lines[0].words.size)
        assertEquals("hello", restored.lines[0].words[0].text)
        assertEquals(1000L, restored.lines[1].startMs)
    }

    @Test
    fun override_roundTripsPlainLyrics() = runTest {
        val dao = FakeLyricsPrefsDao()
        val plain = Lyrics(synced = false, lines = emptyList(), plainText = "just some text")
        dao.setOverride(key, LyricsJson.encode(plain))
        val restored = dao.overrideOf(key)
        requireNotNull(restored)
        assertEquals(false, restored.synced)
        assertEquals("just some text", restored.plainText)
    }

    @Test
    fun clearOverride_revertsToNull() = runTest {
        val dao = FakeLyricsPrefsDao()
        dao.setOverride(key, LyricsJson.encode(syncedLyrics))
        assertTrue(dao.overrideOf(key) != null)

        dao.setOverride(key, null)
        assertNull(dao.get(key))
        assertNull(dao.overrideOf(key))
    }

    @Test
    fun offsetAndOverride_areIndependentOnSameKey() = runTest {
        val dao = FakeLyricsPrefsDao()
        dao.setOffset(key, 400)
        dao.setOverride(key, LyricsJson.encode(syncedLyrics))

        // Both persisted on the one row.
        assertEquals(400L, dao.offsetOf(key))
        assertTrue(dao.overrideOf(key) != null)

        // Clearing the override leaves the offset intact (row not pruned).
        dao.setOverride(key, null)
        assertEquals(400L, dao.offsetOf(key))
        assertNull(dao.overrideOf(key))
    }
}
