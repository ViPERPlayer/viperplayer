package com.viperplayer.data.player.resumption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSessionCodecTest {

    private fun item(
        pluginId: String = "testsource",
        sourceId: String = "123",
        title: String = "Song",
        artist: String? = "Artist",
        album: String? = "Album",
        artworkUrl: String? = "https://art/123.jpg",
        durationMs: Long? = 200_000L,
        isVideo: Boolean = false,
    ) = LastSessionItem(pluginId, sourceId, title, artist, album, artworkUrl, durationMs, isVideo)

    private fun session(
        items: List<LastSessionItem> = listOf(item(sourceId = "1"), item(sourceId = "2"), item(sourceId = "3")),
        currentIndex: Int = 1,
        positionMs: Long = 42_000L,
        playWhenReady: Boolean = true,
        shuffleEnabled: Boolean = true,
        repeatMode: String = "ALL",
    ) = LastSession(items, currentIndex, positionMs, playWhenReady, shuffleEnabled, repeatMode)

    // ---- round-trip ----

    @Test
    fun encodeThenDecode_preservesEverything() {
        val original = session()
        val decoded = LastSessionCodec.decode(LastSessionCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun roundTrip_preservesItemFieldsIncludingNulls() {
        val original = session(
            items = listOf(
                item(sourceId = "1", artist = null, album = null, artworkUrl = null, durationMs = null, isVideo = true),
            ),
            currentIndex = 0,
        )
        val decoded = LastSessionCodec.decode(LastSessionCodec.encode(original))!!
        val restored = decoded.items.single()
        assertEquals("1", restored.sourceId)
        assertNull(restored.artist)
        assertNull(restored.album)
        assertNull(restored.artworkUrl)
        assertNull(restored.durationMs)
        assertTrue(restored.isVideo)
    }

    @Test
    fun roundTrip_preservesIndexPositionAndFlags() {
        val decoded = LastSessionCodec.decode(
            LastSessionCodec.encode(
                session(currentIndex = 2, positionMs = 99_999L, playWhenReady = false, shuffleEnabled = false, repeatMode = "ONE")
            )
        )!!
        assertEquals(2, decoded.currentIndex)
        assertEquals(99_999L, decoded.positionMs)
        assertEquals(false, decoded.playWhenReady)
        assertEquals(false, decoded.shuffleEnabled)
        assertEquals("ONE", decoded.repeatMode)
    }

    // ---- decode edge cases ----

    @Test
    fun decode_nullOrBlank_returnsNull() {
        assertNull(LastSessionCodec.decode(null))
        assertNull(LastSessionCodec.decode(""))
        assertNull(LastSessionCodec.decode("   "))
    }

    @Test
    fun decode_malformedJson_returnsNull() {
        assertNull(LastSessionCodec.decode("{not json"))
        assertNull(LastSessionCodec.decode("[]"))
        assertNull(LastSessionCodec.decode("42"))
        assertNull(LastSessionCodec.decode("{\"items\":[{\"pluginId\":}]}"))
    }

    @Test
    fun decode_versionMismatch_returnsNull() {
        val futureVersion = """{"version":${LastSessionCodec.VERSION + 1},"items":[{"pluginId":"testsource","sourceId":"1"}]}"""
        assertNull(LastSessionCodec.decode(futureVersion))
    }

    @Test
    fun decode_emptyItems_returnsNull() {
        val empty = """{"version":${LastSessionCodec.VERSION},"items":[]}"""
        assertNull(LastSessionCodec.decode(empty))
    }

    @Test
    fun decode_dropsItemsWithBlankIds_andReturnsNullWhenAllDropped() {
        val allBlank = """{"version":${LastSessionCodec.VERSION},"items":[{"pluginId":"","sourceId":"1"},{"pluginId":"testsource","sourceId":""}]}"""
        assertNull(LastSessionCodec.decode(allBlank))
    }

    @Test
    fun decode_dropsOnlyInvalidItems_keepsValidOnes() {
        val mixed = """{"version":${LastSessionCodec.VERSION},"currentIndex":0,"items":[
            {"pluginId":"testsource","sourceId":"1","title":"Keep"},
            {"pluginId":"","sourceId":"2","title":"Drop"}
        ]}""".trimIndent()
        val decoded = LastSessionCodec.decode(mixed)!!
        assertEquals(1, decoded.items.size)
        assertEquals("Keep", decoded.items.single().title)
    }

    @Test
    fun decode_missingOptionalFields_usesDefaults() {
        val minimal = """{"version":${LastSessionCodec.VERSION},"items":[{"pluginId":"testsource","sourceId":"1"}]}"""
        val decoded = LastSessionCodec.decode(minimal)!!
        val restored = decoded.items.single()
        assertEquals("", restored.title)
        assertNull(restored.artist)
        assertEquals(0, decoded.currentIndex)
        assertEquals(0L, decoded.positionMs)
        assertEquals("OFF", decoded.repeatMode)
    }

    @Test
    fun decode_ignoresUnknownKeys() {
        val withExtra = """{"version":${LastSessionCodec.VERSION},"future":"x","items":[{"pluginId":"testsource","sourceId":"1","surprise":true}]}"""
        val decoded = LastSessionCodec.decode(withExtra)
        assertEquals(1, decoded!!.items.size)
    }

    // ---- selectRestore ----

    @Test
    fun selectRestore_null_returnsNull() {
        assertNull(LastSessionCodec.selectRestore(null))
    }

    @Test
    fun selectRestore_normalCase_passesThrough() {
        val sel = LastSessionCodec.selectRestore(session(currentIndex = 1, positionMs = 5_000L))!!
        assertEquals(1, sel.startIndex)
        assertEquals(5_000L, sel.startPositionMs)
        assertEquals(3, sel.items.size)
        assertTrue(sel.shuffleEnabled)
        assertEquals("ALL", sel.repeatMode)
    }

    @Test
    fun selectRestore_indexPastEnd_clampsToLastAndZeroesPosition() {
        val sel = LastSessionCodec.selectRestore(
            session(items = listOf(item(sourceId = "1"), item(sourceId = "2")), currentIndex = 9, positionMs = 5_000L)
        )!!
        assertEquals(1, sel.startIndex)          // clamped to lastIndex
        assertEquals(0L, sel.startPositionMs)    // out-of-range index → position reset
    }

    @Test
    fun selectRestore_negativeIndex_clampsToZeroAndZeroesPosition() {
        val sel = LastSessionCodec.selectRestore(
            session(items = listOf(item(sourceId = "1")), currentIndex = -3, positionMs = 8_000L)
        )!!
        assertEquals(0, sel.startIndex)
        assertEquals(0L, sel.startPositionMs)
    }

    @Test
    fun selectRestore_negativePosition_clampedToZero() {
        val sel = LastSessionCodec.selectRestore(
            session(items = listOf(item(sourceId = "1")), currentIndex = 0, positionMs = -100L)
        )!!
        assertEquals(0, sel.startIndex)
        assertEquals(0L, sel.startPositionMs)
    }

    @Test
    fun selectRestore_emptyItems_returnsNull() {
        assertNull(LastSessionCodec.selectRestore(session(items = emptyList(), currentIndex = 0)))
    }

    @Test
    fun encode_decode_selectRestore_endToEnd() {
        val sel = LastSessionCodec.selectRestore(
            LastSessionCodec.decode(LastSessionCodec.encode(session(currentIndex = 2, positionMs = 12_345L)))
        )!!
        assertEquals(2, sel.startIndex)
        assertEquals(12_345L, sel.startPositionMs)
        assertEquals("testsource", sel.items.first().pluginId)
    }
}
