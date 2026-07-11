package com.viperplayer.data.playlist

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uSerializerTest {

    private fun song(pluginId: String, sourceId: String, title: String, artist: String?, durationMs: Long?) =
        Song(
            id = MediaId(pluginId, sourceId),
            title = title,
            artists = artist?.let { listOf(ArtistRef(name = it, id = null)) } ?: emptyList(),
            durationMs = durationMs,
        )

    @Test
    fun serialize_writesExtM3uHeaderAndExtinf() {
        val songs = listOf(
            song("testsource", "12345", "Bohemian Rhapsody", "Queen", 354_000),
        )
        val out = M3uSerializer.serialize("My List", songs)

        val lines = out.trimEnd().lines()
        assertEquals("#EXTM3U", lines[0])
        assertEquals("#PLAYLIST:My List", lines[1])
        assertEquals("#EXTINF:354,Queen - Bohemian Rhapsody", lines[2])
        assertEquals("viper://testsource/12345", lines[3])
    }

    @Test
    fun serialize_localSongWithContentUri_usesUriDirectly() {
        val songs = listOf(
            song("local", "content://media/external/audio/media/42", "Track", "Artist", 120_000),
        )
        val out = M3uSerializer.serialize("Local", songs)
        assertTrue(out.contains("content://media/external/audio/media/42"))
    }

    @Test
    fun parse_ignoresCommentsAndBlankLines_readsExtinf() {
        val content = """
            #EXTMU3
            #EXTM3U

            #EXTINF:200,Artist - Title
            viper://testsource/999

            # a random comment
            viper://othersource/abc
        """.trimIndent()

        val entries = M3uSerializer.parse(content)
        assertEquals(2, entries.size)
        assertEquals("viper://testsource/999", entries[0].location)
        assertEquals("Artist - Title", entries[0].title)
        assertEquals(200, entries[0].durationSec)
        // Second entry had no preceding EXTINF.
        assertEquals("viper://othersource/abc", entries[1].location)
        assertNull(entries[1].title)
        assertNull(entries[1].durationSec)
    }

    @Test
    fun roundTrip_viperPlaylist_preservesIdsAndMetadata() {
        val songs = listOf(
            song("testsource", "12345", "Bohemian Rhapsody", "Queen", 354_000),
            song("othersource", "abc-DEF", "Some / Song", "The Band", 61_000),
        )
        val serialized = M3uSerializer.serialize("Round Trip", songs)
        val entries = M3uSerializer.parse(serialized)

        assertEquals(2, entries.size)

        val first = M3uSerializer.parseViperUri(entries[0].location)
        assertEquals("testsource" to "12345", first)
        assertEquals(354, entries[0].durationSec)

        // sourceId containing a slash must survive percent-encoding round trip.
        val second = M3uSerializer.parseViperUri(entries[1].location)
        assertEquals("othersource" to "abc-DEF", second)
        assertEquals("The Band - Some / Song", entries[1].title)
    }

    @Test
    fun parseViperUri_rejectsNonViperLocations() {
        assertNull(M3uSerializer.parseViperUri("/music/song.mp3"))
        assertNull(M3uSerializer.parseViperUri("content://media/audio/42"))
        assertNull(M3uSerializer.parseViperUri("viper://only-plugin"))
    }
}
