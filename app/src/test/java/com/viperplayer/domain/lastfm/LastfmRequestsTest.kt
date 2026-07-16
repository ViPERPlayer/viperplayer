package com.viperplayer.domain.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LastfmRequests] parameter building for updateNowPlaying / scrobble, including the
 * batched `[i]` array notation and omission of absent optional fields.
 */
class LastfmRequestsTest {

    private val track = ScrobbleTrack(
        artist = "Daft Punk",
        track = "Get Lucky",
        album = "Random Access Memories",
        durationSeconds = 369,
        trackNumber = 8,
    )

    @Test
    fun getMobileSession_includesCredentials() {
        val params = LastfmRequests.getMobileSession("KEY", "alice", "hunter2")
        assertEquals(LastfmRequests.METHOD_GET_MOBILE_SESSION, params["method"])
        assertEquals("KEY", params["api_key"])
        assertEquals("alice", params["username"])
        assertEquals("hunter2", params["password"])
    }

    @Test
    fun updateNowPlaying_hasRequiredAndOptionalParams() {
        val params = LastfmRequests.updateNowPlaying("KEY", "SK", track)
        assertEquals(LastfmRequests.METHOD_UPDATE_NOW_PLAYING, params["method"])
        assertEquals("KEY", params["api_key"])
        assertEquals("SK", params["sk"])
        assertEquals("Daft Punk", params["artist"])
        assertEquals("Get Lucky", params["track"])
        assertEquals("Random Access Memories", params["album"])
        assertEquals("369", params["duration"])
        assertEquals("8", params["trackNumber"])
        // No array suffixes on updateNowPlaying.
        assertFalse(params.keys.any { it.contains("[") })
    }

    @Test
    fun updateNowPlaying_omitsAbsentOptionals() {
        val minimal = ScrobbleTrack(artist = "A", track = "B")
        val params = LastfmRequests.updateNowPlaying("KEY", "SK", minimal)
        assertNull(params["album"])
        assertNull(params["duration"])
        assertNull(params["trackNumber"])
        assertNull(params["mbid"])
        assertNull(params["albumArtist"])
    }

    @Test
    fun scrobble_singleUsesIndexZeroWithTimestamp() {
        val pending = PendingScrobble(track, timestampSeconds = 1_700_000_000)
        val params = LastfmRequests.scrobble("KEY", "SK", listOf(pending))
        assertEquals(LastfmRequests.METHOD_SCROBBLE, params["method"])
        assertEquals("Daft Punk", params["artist[0]"])
        assertEquals("Get Lucky", params["track[0]"])
        assertEquals("1700000000", params["timestamp[0]"])
        assertEquals("Random Access Memories", params["album[0]"])
        assertEquals("369", params["duration[0]"])
    }

    @Test
    fun scrobble_batchIndexesEachEntry() {
        val batch = listOf(
            PendingScrobble(ScrobbleTrack("A1", "T1"), 100),
            PendingScrobble(ScrobbleTrack("A2", "T2"), 200),
        )
        val params = LastfmRequests.scrobble("KEY", "SK", batch)
        assertEquals("A1", params["artist[0]"])
        assertEquals("T1", params["track[0]"])
        assertEquals("100", params["timestamp[0]"])
        assertEquals("A2", params["artist[1]"])
        assertEquals("T2", params["track[1]"])
        assertEquals("200", params["timestamp[1]"])
    }

    @Test
    fun scrobble_rejectsEmptyBatch() {
        assertThrows(IllegalArgumentException::class.java) {
            LastfmRequests.scrobble("KEY", "SK", emptyList())
        }
    }

    @Test
    fun scrobble_rejectsOversizedBatch() {
        val tooMany = (0..LastfmRequests.MAX_BATCH).map {
            PendingScrobble(ScrobbleTrack("A$it", "T$it"), it.toLong())
        }
        assertTrue(tooMany.size > LastfmRequests.MAX_BATCH)
        assertThrows(IllegalArgumentException::class.java) {
            LastfmRequests.scrobble("KEY", "SK", tooMany)
        }
    }

    @Test
    fun scrobbleTrack_isValidRequiresArtistAndTitle() {
        assertTrue(ScrobbleTrack("A", "B").isValid)
        assertFalse(ScrobbleTrack("", "B").isValid)
        assertFalse(ScrobbleTrack("A", " ").isValid)
    }
}
