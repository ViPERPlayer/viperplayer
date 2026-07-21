package com.viperplayer.domain.autoplaylist

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.stats.PlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure [AutoPlaylistGenerator]. Covers each generator: play-count ranking with
 * a deterministic tie-break, distinct + newest-first + limited recently-played, date-desc recently-
 * added, favorites, forgotten windowing, deterministic shuffle, empty inputs, limit enforcement, and
 * the "history song no longer in the library" resolution (included via cached metadata).
 */
class AutoPlaylistGeneratorTest {

    private fun mediaId(source: String) = MediaId.Local(source)

    /** The canonical join key string, matching what the play-history stores (and [AutoPlaylistGenerator.mediaKey]). */
    private fun key(source: String) = "t=local&s=$source"

    private fun song(source: String, title: String = "T-$source", liked: Boolean = false) = Song(
        id = mediaId(source),
        title = title,
        artists = listOf(ArtistRef(name = "Artist")),
        isLiked = liked,
    )

    private fun rec(
        source: String,
        ts: Long,
        title: String = "T-$source",
        artist: String = "Artist",
        album: String? = "Album",
        durationMs: Long = 180_000L,
        artworkUrl: String? = null,
    ) = PlayRecord(
        timestampMs = ts,
        mediaId = key(source),
        title = title,
        artist = artist,
        album = album,
        pluginId = "local",
        listenedMs = durationMs,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
    )

    // ---- Recently Added ------------------------------------------------------------------------

    @Test
    fun recentlyAdded_preservesNewestFirstOrderAndDedups() {
        // Repo supplies songs already newest-first; generator keeps order, drops dupes, limits.
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val input = listOf(a, b, c, a) // 'a' duplicated later

        val result = AutoPlaylistGenerator.recentlyAdded(input, limit = 10)

        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun recentlyAdded_respectsLimit() {
        val input = (1..20).map { song("s$it") }
        val result = AutoPlaylistGenerator.recentlyAdded(input, limit = 5)
        assertEquals(5, result.size)
        assertEquals(input.take(5), result)
    }

    @Test
    fun recentlyAdded_emptyLibrary_isEmpty() {
        assertTrue(AutoPlaylistGenerator.recentlyAdded(emptyList()).isEmpty())
    }

    // ---- Favorites -----------------------------------------------------------------------------

    @Test
    fun favorites_returnsLikedSongsInOrderDeduped() {
        val a = song("a", liked = true)
        val b = song("b", liked = true)
        val result = AutoPlaylistGenerator.favorites(listOf(a, b, a), limit = 10)
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun favorites_emptyLiked_isEmpty() {
        assertTrue(AutoPlaylistGenerator.favorites(emptyList()).isEmpty())
    }

    // ---- Most Played ---------------------------------------------------------------------------

    @Test
    fun mostPlayed_ranksByPlayCountDescending() {
        val library = listOf(song("a"), song("b"), song("c"))
        // b: 3 plays, a: 2 plays, c: 1 play
        val records = listOf(
            rec("a", 100), rec("a", 200),
            rec("b", 100), rec("b", 200), rec("b", 300),
            rec("c", 100),
        )
        val result = AutoPlaylistGenerator.mostPlayed(records, library)
        assertEquals(listOf("b", "a", "c"), result.map { it.id.sourceId })
    }

    @Test
    fun mostPlayed_tieBreak_isDeterministic_byRecencyThenId() {
        val library = listOf(song("a"), song("b"), song("c"))
        // All 2 plays. Tie-break: more-recent last play first, then id ascending.
        // a: last=500, b: last=900, c: last=900 -> order b (id<c), c, a
        val records = listOf(
            rec("a", 100), rec("a", 500),
            rec("b", 100), rec("b", 900),
            rec("c", 100), rec("c", 900),
        )
        val result = AutoPlaylistGenerator.mostPlayed(records, library)
        assertEquals(listOf("b", "c", "a"), result.map { it.id.sourceId })

        // Order must be stable across a reshuffled input.
        val reshuffled = AutoPlaylistGenerator.mostPlayed(records.shuffled(), library)
        assertEquals(result.map { it.id }, reshuffled.map { it.id })
    }

    @Test
    fun mostPlayed_respectsLimit() {
        val library = (1..10).map { song("s$it") }
        val records = (1..10).flatMap { i -> List(i) { rec("s$i", (it + 1) * 100L) } }
        val result = AutoPlaylistGenerator.mostPlayed(records, library, limit = 3)
        assertEquals(3, result.size)
    }

    @Test
    fun mostPlayed_emptyHistory_isEmpty() {
        assertTrue(AutoPlaylistGenerator.mostPlayed(emptyList(), listOf(song("a"))).isEmpty())
    }

    @Test
    fun mostPlayed_songMissingFromLibrary_isIncludedFromCachedMetadata() {
        // 'ghost' is played but not in the library; it must be included using play-history metadata.
        val library = listOf(song("a"))
        val records = listOf(
            rec("ghost", 100, title = "Ghost Song", artist = "Ghost Artist",
                artworkUrl = "http://art/ghost.jpg"),
            rec("ghost", 200),
            rec("a", 100),
        )
        val result = AutoPlaylistGenerator.mostPlayed(records, library)
        assertEquals(listOf("ghost", "a"), result.map { it.id.sourceId })
        val ghost = result.first()
        assertEquals("Ghost Song", ghost.title)
        assertEquals("Ghost Artist", ghost.artistNames)
        assertEquals("http://art/ghost.jpg", ghost.artworkUrl)
    }

    @Test
    fun mostPlayed_customKeyOf_joinsLibraryOnProvidedKey() {
        // Simulate the production join where the history stores an encoded key; a matching keyOf
        // resolves the live library song instead of falling back to cached metadata.
        val liveA = song("a", title = "Live A", liked = true)
        val encodedRecords = listOf(
            PlayRecord(
                timestampMs = 100L,
                mediaId = "ENC:a", // stand-in for an encoded id
                title = "Stale A",
                artist = "Artist",
                album = "Album",
                pluginId = "local",
                listenedMs = 1000L,
                durationMs = 1000L,
            ),
        )
        val result = AutoPlaylistGenerator.mostPlayed(
            encodedRecords,
            listOf(liveA),
            keyOf = { "ENC:${it.sourceId}" },
        )
        assertEquals("Live A", result.single().title)
        assertTrue(result.single().isLiked)
    }

    @Test
    fun mostPlayed_prefersLiveLibrarySongOverCachedMetadata() {
        // The library song carries the current (liked) state; history metadata is stale.
        val liveA = song("a", title = "Current Title", liked = true)
        val records = listOf(rec("a", 100, title = "Stale Title"))
        val result = AutoPlaylistGenerator.mostPlayed(records, listOf(liveA))
        assertEquals("Current Title", result.first().title)
        assertTrue(result.first().isLiked)
    }

    // ---- Recently Played -----------------------------------------------------------------------

    @Test
    fun recentlyPlayed_isDistinctNewestFirstAndLimited() {
        val library = listOf(song("a"), song("b"), song("c"))
        // a last played 300, b last played 500, c last played 400 -> b, c, a
        val records = listOf(
            rec("a", 100), rec("a", 300),
            rec("b", 200), rec("b", 500),
            rec("c", 400),
        )
        val result = AutoPlaylistGenerator.recentlyPlayed(records, library, limit = 2)
        // Distinct songs, newest-first, limited to 2 => b then c.
        assertEquals(listOf("b", "c"), result.map { it.id.sourceId })
    }

    @Test
    fun recentlyPlayed_tieOnTimestamp_breaksById() {
        val library = listOf(song("a"), song("b"))
        val records = listOf(rec("b", 500), rec("a", 500))
        val result = AutoPlaylistGenerator.recentlyPlayed(records, library)
        assertEquals(listOf("a", "b"), result.map { it.id.sourceId })
    }

    @Test
    fun recentlyPlayed_emptyHistory_isEmpty() {
        assertTrue(AutoPlaylistGenerator.recentlyPlayed(emptyList(), emptyList()).isEmpty())
    }

    // ---- Forgotten -----------------------------------------------------------------------------

    @Test
    fun forgotten_includesOnlySongsNotPlayedSinceCutoff() {
        val library = listOf(song("old"), song("recent"), song("mixed"))
        val cutoff = 1_000L
        // old: last play 500 (< cutoff) -> forgotten
        // recent: last play 2000 (>= cutoff) -> excluded
        // mixed: plays at 300 and 1500 -> last is 1500 (>= cutoff) -> excluded
        val records = listOf(
            rec("old", 500),
            rec("recent", 2000),
            rec("mixed", 300), rec("mixed", 1500),
        )
        val result = AutoPlaylistGenerator.forgotten(records, library, notRecentBeforeMs = cutoff)
        assertEquals(listOf("old"), result.map { it.id.sourceId })
    }

    @Test
    fun forgotten_ordersByMostRecentPlayDescending() {
        val library = listOf(song("x"), song("y"), song("z"))
        val cutoff = 10_000L
        val records = listOf(rec("x", 100), rec("y", 5000), rec("z", 3000))
        val result = AutoPlaylistGenerator.forgotten(records, library, notRecentBeforeMs = cutoff)
        // All before cutoff; ordered by last play desc: y(5000), z(3000), x(100)
        assertEquals(listOf("y", "z", "x"), result.map { it.id.sourceId })
    }

    @Test
    fun forgotten_emptyHistory_isEmpty() {
        assertTrue(
            AutoPlaylistGenerator.forgotten(emptyList(), emptyList(), notRecentBeforeMs = 1L).isEmpty()
        )
    }

    // ---- Shuffle All ---------------------------------------------------------------------------

    @Test
    fun shuffleAll_isDeterministicForSameSeed() {
        val library = (1..20).map { song("s$it") }
        val a = AutoPlaylistGenerator.shuffleAll(library, seed = 42L)
        val b = AutoPlaylistGenerator.shuffleAll(library, seed = 42L)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun shuffleAll_containsEverySongAndRespectsLimit() {
        val library = (1..20).map { song("s$it") }
        val full = AutoPlaylistGenerator.shuffleAll(library, seed = 7L)
        assertEquals(library.map { it.id }.toSet(), full.map { it.id }.toSet())

        val limited = AutoPlaylistGenerator.shuffleAll(library, seed = 7L, limit = 5)
        assertEquals(5, limited.size)
    }

    @Test
    fun shuffleAll_emptyLibrary_isEmpty() {
        assertTrue(AutoPlaylistGenerator.shuffleAll(emptyList(), seed = 1L).isEmpty())
    }

    // ---- recordToSong --------------------------------------------------------------------------

    @Test
    fun recordToSong_blankArtist_yieldsNoArtistRefs() {
        val song = AutoPlaylistGenerator.recordToSong(rec("a", 1L, artist = ""))
        assertTrue(song.artists.isEmpty())
    }

    @Test
    fun recordToSong_zeroDuration_isNullDuration() {
        val song = AutoPlaylistGenerator.recordToSong(rec("a", 1L, durationMs = 0L))
        assertEquals(null, song.durationMs)
    }

    @Test
    fun recordToSong_malformedId_doesNotThrowAndYieldsSourceId() {
        // A non-canonical id string must not blow up MediaId's non-blank-sourceId requirement.
        val record = PlayRecord(
            timestampMs = 1L,
            mediaId = "not-a-canonical-id",
            title = "T",
            artist = "A",
            album = null,
            pluginId = "local",
            listenedMs = 1L,
            durationMs = 1L,
        )
        val song = AutoPlaylistGenerator.recordToSong(record)
        assertEquals("not-a-canonical-id", song.id.sourceId)
    }

    @Test
    fun mediaKey_matchesCanonicalIdFormat() {
        assertEquals("t=local&s=abc", AutoPlaylistGenerator.mediaKey(mediaId("abc")))
        assertEquals("t=plugin&p=testsource&s=42", AutoPlaylistGenerator.mediaKey(MediaId.Plugin("testsource", "42")))
    }

    // ---- id decode round-trip (missing-from-library) -------------------------------------------

    @Test
    fun mostPlayed_missingSong_reconstructedIdIsDecodedNotPercentEncoded() {
        // A `local`-style sourceId whose reserved chars ('/' and ':') get percent-encoded by
        // MediaId.encode() when persisted to the play-history.
        val rawSourceId = "content://media/external/audio/media/123"
        // What MediaId.encode() actually stores on-device for a Local id (Uri-encoded sourceId).
        val encodedMediaId = "t=local&s=${encodeUriComponent(rawSourceId)}"
        // The record is present in HISTORY but ABSENT from the library, so resolution must fall back
        // to recordToSong -> mediaIdOf. Empty library guarantees the missing-from-library path.
        val record = PlayRecord(
            timestampMs = 100L,
            mediaId = encodedMediaId,
            title = "Ghost",
            artist = "Ghost Artist",
            album = null,
            pluginId = "local",
            listenedMs = 1000L,
            durationMs = 1000L,
        )

        // Mirror what AutoPlaylistRepositoryImpl passes: keyOf = MediaId.encode() (encoded) and
        // mediaIdOf = MediaId.decode() (Uri-DECODES). Modeled purely here so the test runs on the
        // JVM without android.net.Uri. If the fix regressed (raw value used, no decode), the sourceId
        // would keep its percent-escapes and this assertion would fail.
        val result = AutoPlaylistGenerator.mostPlayed(
            records = listOf(record),
            library = emptyList(),
            keyOf = { id -> "t=local&s=${encodeUriComponent(id.sourceId)}" },
            mediaIdOf = { raw -> decodeCanonicalMediaId(raw) },
        )

        val ghost = result.single()
        assertTrue("expected a Local id", ghost.id is MediaId.Local)
        // The reconstructed sourceId must decode back to the original content:// URI, NOT the
        // percent-encoded "content%3A%2F%2F…" form.
        assertEquals(rawSourceId, ghost.id.sourceId)
        assertTrue(
            "sourceId must not retain percent-encoding: ${ghost.id.sourceId}",
            !ghost.id.sourceId.contains("%3A") && !ghost.id.sourceId.contains("%2F"),
        )
    }

    /**
     * Minimal, pure stand-in for android.net.Uri.encode (used by MediaId.encode()) covering the
     * reserved chars that appear in a `content://` sourceId, so this JVM test needs no Android runtime.
     */
    private fun encodeUriComponent(value: String): String =
        value.replace("%", "%25").replace(":", "%3A").replace("/", "%2F")

    /**
     * Pure model of MediaId.decode() for a Local id: split on '&'/'=' and Uri-DECODE each value. This
     * is the decoding behaviour the repository delegates to (via MediaId.decode) for a
     * missing-from-library id, verified here without android.net.Uri.
     */
    private fun decodeCanonicalMediaId(raw: String): MediaId {
        val params = raw.split("&").associate {
            val (k, v) = it.split("=")
            k to decodeUriComponent(v)
        }
        return MediaId.Local(params.getValue("s"))
    }

    private fun decodeUriComponent(value: String): String =
        value.replace("%3A", ":").replace("%2F", "/").replace("%25", "%")
}
