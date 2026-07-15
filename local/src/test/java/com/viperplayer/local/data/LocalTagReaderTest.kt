package com.viperplayer.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for the pure byte-level embedded-lyrics reader: ID3v2 SYLT/USLT, FLAC/Ogg Vorbis
 * comments, container sniffing, and the synced-over-unsynced preference. Uses hand-built minimal tag
 * blobs so no real audio files are needed.
 */
class LocalTagReaderTest {

    // ---- ID3v2 --------------------------------------------------------------------------------

    @Test
    fun `id3 USLT unsynced lyrics parse to plain text`() {
        val uslt = usltFrame(encoding = 3, descriptor = "", lyrics = "line one\nline two")
        val tag = id3v24(listOf("USLT" to uslt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertFalse(lyrics.synced)
        assertEquals("line one\nline two", lyrics.plainText)
    }

    @Test
    fun `id3 SYLT with millisecond timestamps parses to synced lines`() {
        val sylt = syltFrame(
            encoding = 3,
            descriptor = "",
            entries = listOf("Hello" to 1000, "World" to 3500),
        )
        val tag = id3v24(listOf("SYLT" to sylt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertTrue(lyrics.synced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1000L, lyrics.lines[0].startMs)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(3500L, lyrics.lines[1].startMs)
        assertEquals("World", lyrics.lines[1].text)
    }

    @Test
    fun `id3 prefers SYLT over USLT when both are present`() {
        val uslt = usltFrame(encoding = 3, descriptor = "", lyrics = "plain fallback")
        val sylt = syltFrame(encoding = 3, descriptor = "", entries = listOf("Timed" to 500))
        val tag = id3v24(listOf("USLT" to uslt, "SYLT" to sylt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertTrue(lyrics.synced)
        assertEquals("Timed", lyrics.lines[0].text)
        assertEquals(500L, lyrics.lines[0].startMs)
    }

    @Test
    fun `id3 SYLT with non-millisecond timestamp format degrades to unsynced text`() {
        // timestampFormat 1 = MPEG frames; we can't convert, so text-only.
        val sylt = syltFrame(encoding = 3, descriptor = "", entries = listOf("Frame" to 12), timestampFormat = 1)
        val tag = id3v24(listOf("SYLT" to sylt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertFalse(lyrics.synced)
        assertTrue(lyrics.plainText!!.contains("Frame"))
    }

    @Test
    fun `id3 USLT with UTF-16 encoding decodes`() {
        val uslt = usltFrame(encoding = 1, descriptor = "", lyrics = "café ☕")
        val tag = id3v24(listOf("USLT" to uslt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertEquals("café ☕", lyrics.plainText)
    }

    @Test
    fun `id3v23 frame sizes (non-syncsafe) are read`() {
        val uslt = usltFrame(encoding = 3, descriptor = "", lyrics = "v2.3 lyrics")
        val tag = id3(versionMajor = 3, frames = listOf("USLT" to uslt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertEquals("v2.3 lyrics", lyrics.plainText)
    }

    // ---- Vorbis comments (FLAC / Ogg) ---------------------------------------------------------

    @Test
    fun `flac UNSYNCEDLYRICS parses to plain text`() {
        val flac = flacWithComments(listOf("UNSYNCEDLYRICS=hello from flac"))
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac)!!
        assertFalse(lyrics.synced)
        assertEquals("hello from flac", lyrics.plainText)
    }

    @Test
    fun `flac LYRICS holding LRC becomes synced`() {
        val flac = flacWithComments(listOf("LYRICS=[00:01.00]synced flac line"))
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac)!!
        assertTrue(lyrics.synced)
        assertEquals(1000L, lyrics.lines[0].startMs)
        assertEquals("synced flac line", lyrics.lines[0].text)
    }

    @Test
    fun `flac prefers SYNCEDLYRICS over UNSYNCEDLYRICS`() {
        val flac = flacWithComments(
            listOf(
                "UNSYNCEDLYRICS=plain",
                "SYNCEDLYRICS=[00:02.00]synced wins",
            ),
        )
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac)!!
        assertTrue(lyrics.synced)
        assertEquals("synced wins", lyrics.lines[0].text)
        assertEquals(2000L, lyrics.lines[0].startMs)
    }

    @Test
    fun `ogg vorbis comment lyrics parse`() {
        val ogg = oggWithVorbisComments(listOf("LYRICS=ogg plain lyrics"))
        val lyrics = LocalTagReader.readEmbeddedLyrics(ogg)!!
        assertEquals("ogg plain lyrics", lyrics.plainText)
    }

    // ---- sniffing / negatives -----------------------------------------------------------------

    @Test
    fun `unknown container returns null`() {
        assertNull(LocalTagReader.readEmbeddedLyrics(byteArrayOf(0, 1, 2, 3, 4, 5)))
    }

    @Test
    fun `id3 tag without any lyric frame returns null`() {
        val tit2 = byteArrayOf(3) + "Some Title".toByteArray(Charsets.UTF_8)
        val tag = id3v24(listOf("TIT2" to tit2))
        assertNull(LocalTagReader.readEmbeddedLyrics(tag))
    }

    @Test
    fun `flac without a lyrics field returns null`() {
        val flac = flacWithComments(listOf("TITLE=No Lyrics Here"))
        assertNull(LocalTagReader.readEmbeddedLyrics(flac))
    }

    @Test
    fun `too-short input returns null`() {
        assertNull(LocalTagReader.readEmbeddedLyrics(byteArrayOf(1, 2)))
    }

    @Test
    fun `reads embedded lyrics from an input stream`() {
        val flac = flacWithComments(listOf("UNSYNCEDLYRICS=stream lyrics"))
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac.inputStream())!!
        assertEquals("stream lyrics", lyrics.plainText)
    }

    // ---- byte builders ------------------------------------------------------------------------

    private fun textBytes(encoding: Int, s: String): ByteArray = when (encoding) {
        1 -> s.toByteArray(Charsets.UTF_16) // with BOM
        2 -> s.toByteArray(Charsets.UTF_16BE)
        3 -> s.toByteArray(Charsets.UTF_8)
        else -> s.toByteArray(Charsets.ISO_8859_1)
    }

    private fun terminator(encoding: Int): ByteArray =
        if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)

    private fun usltFrame(encoding: Int, descriptor: String, lyrics: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encoding)
        out.write("eng".toByteArray(Charsets.ISO_8859_1)) // language
        out.write(textBytes(encoding, descriptor))
        out.write(terminator(encoding))
        out.write(textBytes(encoding, lyrics))
        return out.toByteArray()
    }

    private fun syltFrame(
        encoding: Int,
        descriptor: String,
        entries: List<Pair<String, Int>>,
        timestampFormat: Int = 2,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encoding)
        out.write("eng".toByteArray(Charsets.ISO_8859_1))
        out.write(timestampFormat)
        out.write(1) // content type: lyrics
        out.write(textBytes(encoding, descriptor))
        out.write(terminator(encoding))
        for ((text, ts) in entries) {
            out.write(textBytes(encoding, text))
            out.write(terminator(encoding))
            out.write(byteArrayOf(
                ((ts ushr 24) and 0xFF).toByte(),
                ((ts ushr 16) and 0xFF).toByte(),
                ((ts ushr 8) and 0xFF).toByte(),
                (ts and 0xFF).toByte(),
            ))
        }
        return out.toByteArray()
    }

    private fun syncSafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun be32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun id3v24(frames: List<Pair<String, ByteArray>>) = id3(versionMajor = 4, frames = frames)

    /** Build a minimal ID3v2 tag containing [frames]. Frame sizes are sync-safe for v2.4, BE for v2.3. */
    private fun id3(versionMajor: Int, frames: List<Pair<String, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, data) in frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(if (versionMajor >= 4) syncSafe(data.size) else be32(data.size))
            body.write(byteArrayOf(0, 0)) // frame flags
            body.write(data)
        }
        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(versionMajor) // version major
        out.write(0)            // version revision
        out.write(0)            // flags
        out.write(syncSafe(bodyBytes.size)) // tag size is always sync-safe
        out.write(bodyBytes)
        return out.toByteArray()
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )

    /** A Vorbis-comment block body: vendor + count + length-prefixed `KEY=value` UTF-8 fields. */
    private fun vorbisCommentBody(comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "test".toByteArray(Charsets.UTF_8)
        out.write(le32(vendor.size))
        out.write(vendor)
        out.write(le32(comments.size))
        for (c in comments) {
            val b = c.toByteArray(Charsets.UTF_8)
            out.write(le32(b.size))
            out.write(b)
        }
        return out.toByteArray()
    }

    /** A minimal FLAC file: "fLaC" + a single (last) VORBIS_COMMENT metadata block. */
    private fun flacWithComments(comments: List<String>): ByteArray {
        val body = vorbisCommentBody(comments)
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        out.write(0x84) // last-block flag (0x80) | type 4 (VORBIS_COMMENT)
        out.write(byteArrayOf(
            ((body.size ushr 16) and 0xFF).toByte(),
            ((body.size ushr 8) and 0xFF).toByte(),
            (body.size and 0xFF).toByte(),
        ))
        out.write(body)
        return out.toByteArray()
    }

    /** A minimal Ogg blob: "OggS" ... vorbis-comment packet ("\x03vorbis" + comment body). */
    private fun oggWithVorbisComments(comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OggS".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(22)) // dummy page header remainder
        out.write(byteArrayOf(3)) // \x03
        out.write("vorbis".toByteArray(Charsets.ISO_8859_1))
        out.write(vorbisCommentBody(comments))
        return out.toByteArray()
    }
}
