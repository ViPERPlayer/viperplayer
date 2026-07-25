package com.viperplayer.data.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LrcLibLyricsProvider.searchLyrics] — the manual lyric-match search mapping — driven by a
 * Ktor [MockEngine] so no live LRCLIB server is needed. Locks down: synced vs plain detection, the
 * display metadata + duration (seconds→ms) mapping, dropping instrumental / empty-lyric records, and
 * the synced-first ordering used to surface the best matches on top.
 */
class LrcLibLyricsProviderSearchTest {

    private fun provider(body: String, status: HttpStatusCode = HttpStatusCode.OK): LrcLibLyricsProvider {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return LrcLibLyricsProvider(HttpClient(engine))
    }

    @Test
    fun searchLyrics_mapsSyncedRecord_withMetadataAndDuration() = runTest {
        val body = """
            [
              {
                "trackName": "Song A",
                "artistName": "Artist A",
                "albumName": "Album A",
                "duration": 215.0,
                "syncedLyrics": "[00:01.00]hello\n[00:03.00]world"
              }
            ]
        """.trimIndent()
        val results = provider(body).searchLyrics("Song A", "Artist A")

        assertEquals(1, results.size)
        val candidate = results[0]
        assertEquals("Song A", candidate.trackName)
        assertEquals("Artist A", candidate.artistName)
        assertEquals("Album A", candidate.albumName)
        assertEquals(215_000L, candidate.durationMs)
        assertTrue(candidate.synced)
        assertTrue(candidate.lyrics.synced)
        assertEquals(2, candidate.lyrics.lines.size)
        assertEquals("hello", candidate.lyrics.lines[0].text)
    }

    @Test
    fun searchLyrics_mapsPlainRecord_asUnsynced() = runTest {
        val body = """
            [
              {"trackName": "Plain", "artistName": "P", "plainLyrics": "just words\nmore words"}
            ]
        """.trimIndent()
        val results = provider(body).searchLyrics("Plain", null)

        assertEquals(1, results.size)
        assertFalse(results[0].synced)
        assertFalse(results[0].lyrics.synced)
        assertEquals("just words\nmore words", results[0].lyrics.plainText)
        assertEquals(null, results[0].durationMs)
    }

    @Test
    fun searchLyrics_dropsInstrumentalAndEmptyRecords() = runTest {
        val body = """
            [
              {"trackName": "Inst", "instrumental": true, "syncedLyrics": "[00:01.00]x"},
              {"trackName": "Empty"},
              {"trackName": "Good", "plainLyrics": "words"}
            ]
        """.trimIndent()
        val results = provider(body).searchLyrics("q", null)

        assertEquals(1, results.size)
        assertEquals("Good", results[0].trackName)
    }

    @Test
    fun searchLyrics_ordersSyncedBeforePlain() = runTest {
        val body = """
            [
              {"trackName": "PlainFirst", "plainLyrics": "words"},
              {"trackName": "SyncedSecond", "syncedLyrics": "[00:01.00]hi"}
            ]
        """.trimIndent()
        val results = provider(body).searchLyrics("q", null)

        assertEquals(2, results.size)
        assertTrue(results[0].synced)
        assertEquals("SyncedSecond", results[0].trackName)
        assertFalse(results[1].synced)
    }

    @Test
    fun searchLyrics_blankTitle_returnsEmptyWithoutRequest() = runTest {
        val results = provider("[]").searchLyrics("   ", "Artist")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchLyrics_httpError_returnsEmpty() = runTest {
        val results = provider("oops", HttpStatusCode.InternalServerError).searchLyrics("q", null)
        assertTrue(results.isEmpty())
    }
}
