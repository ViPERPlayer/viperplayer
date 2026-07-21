package com.viperplayer.domain.autoplaylist

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.stats.PlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (real android.net.Uri) test for the id-encoding round-trip of a play-history song that
 * is no longer in the library.
 *
 * The play-history persists `Song.id.toString()`, which Uri-ENCODES both parts of a [MediaId]. For a
 * `local`-style sourceId that is a `content://…` URI (full of reserved chars), the persisted string is
 * heavily percent-encoded. When the song is missing from the library, the auto-playlist generator must
 * reconstruct the [MediaId] by DECODING that string ([MediaId.fromString]) — not use it raw — so the
 * plugin later receives the original sourceId and resolves the right track.
 *
 * This mirrors the exact `keyOf` / `mediaIdOf` lambdas [com.viperplayer.data.repository.AutoPlaylistRepositoryImpl]
 * passes into the generator, but with the genuine Android [MediaId.toString] / [MediaId.fromString].
 */
@RunWith(AndroidJUnit4::class)
class AutoPlaylistIdDecodeTest {

    // The production join key: the encoded canonical id string, matching what the history stores.
    private val keyOf: (MediaId) -> String = { it.encode() }

    // The production reconstruction: Uri-DECODE via MediaId.decode, pure fallback on malformed ids.
    private val mediaIdOf: (String) -> MediaId = { raw ->
        MediaId.decode(raw) ?: AutoPlaylistGenerator.parseMediaId(raw)
    }

    @Test
    fun mostPlayed_missingLocalSong_sourceIdDecodesBackToContentUri() {
        val original = MediaId.Local("content://media/external/audio/media/123")
        // Exactly what the play-history persists for this song (Song.id.encode(), Uri-encoded).
        val persistedId = original.encode()
        // Sanity: persistence really did percent-encode the reserved chars.
        assertFalse("expected a percent-encoded persisted id", persistedId.contains("content://"))

        val record = PlayRecord(
            timestampMs = 100L,
            mediaId = persistedId,
            title = "Ghost",
            artist = "Ghost Artist",
            album = null,
            pluginId = "local",
            listenedMs = 1000L,
            durationMs = 1000L,
        )

        // Song present in HISTORY but ABSENT from the library => missing-from-library resolution path.
        val result = AutoPlaylistGenerator.mostPlayed(
            records = listOf(record),
            library = emptyList(),
            keyOf = keyOf,
            mediaIdOf = mediaIdOf,
        )

        val ghost = result.single()
        // Round-trips exactly back to the original content:// URI — not the percent-encoded form.
        assertEquals(original, ghost.id)
        assertEquals("content://media/external/audio/media/123", ghost.id.sourceId)
        assertFalse(ghost.id.sourceId.contains("%"))
    }

    @Test
    fun inLibrarySong_stillResolvesViaEncodedKey_unchanged() {
        // The in-library path must keep matching on the correctly-ENCODED encode() key.
        val id = MediaId.Local("content://media/external/audio/media/999")
        val liveSong = Song(id = id, title = "Live", isLiked = true)
        val record = PlayRecord(
            timestampMs = 100L,
            mediaId = id.encode(),
            title = "Stale",
            artist = "A",
            album = null,
            pluginId = "local",
            listenedMs = 1000L,
            durationMs = 1000L,
        )

        val result = AutoPlaylistGenerator.mostPlayed(
            records = listOf(record),
            library = listOf(liveSong),
            keyOf = keyOf,
            mediaIdOf = mediaIdOf,
        )

        // Resolved from the live library (current title/liked), proving the encoded-key join still hits.
        assertEquals("Live", result.single().title)
    }
}
