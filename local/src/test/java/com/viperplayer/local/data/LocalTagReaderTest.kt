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

    @Test
    fun `id3v22 ULT frame (6-byte header, 3-char id) is parsed`() {
        val ult = usltFrame(encoding = 3, descriptor = "", lyrics = "v2.2 unsynced")
        val tag = id3v22(listOf("ULT" to ult))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertFalse(lyrics.synced)
        assertEquals("v2.2 unsynced", lyrics.plainText)
    }

    @Test
    fun `id3v22 SLT synced frame is parsed to synced lines`() {
        val slt = syltFrame(encoding = 3, descriptor = "", entries = listOf("Hola" to 750, "Mundo" to 2250))
        val tag = id3v22(listOf("SLT" to slt))
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertTrue(lyrics.synced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(750L, lyrics.lines[0].startMs)
        assertEquals("Hola", lyrics.lines[0].text)
        assertEquals(2250L, lyrics.lines[1].startMs)
        assertEquals("Mundo", lyrics.lines[1].text)
    }

    @Test
    fun `id3v23 with an extended header still finds a following lyric frame`() {
        val uslt = usltFrame(encoding = 3, descriptor = "", lyrics = "after ext header")
        val tag = id3(versionMajor = 3, frames = listOf("USLT" to uslt), extendedHeaderPad = 8)
        val lyrics = LocalTagReader.readEmbeddedLyrics(tag)!!
        assertEquals("after ext header", lyrics.plainText)
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

    @Test
    fun `flac finds VORBIS_COMMENT after a large PICTURE block via stream walk`() {
        // A ~3 MiB PICTURE block precedes the comment — past the 2 MiB head cap. Reading from a stream
        // must skip the picture by its length and still surface the comment without buffering it.
        val flac = flacWithPictureThenComments(
            pictureSize = 3 * 1024 * 1024,
            comments = listOf("UNSYNCEDLYRICS=lyrics behind a big cover"),
        )
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac.inputStream())!!
        assertFalse(lyrics.synced)
        assertEquals("lyrics behind a big cover", lyrics.plainText)
    }

    @Test
    fun `flac synced lyrics after a large PICTURE block are read from a stream`() {
        val flac = flacWithPictureThenComments(
            pictureSize = 3 * 1024 * 1024,
            comments = listOf("SYNCEDLYRICS=[00:03.00]behind cover"),
        )
        val lyrics = LocalTagReader.readEmbeddedLyrics(flac.inputStream())!!
        assertTrue(lyrics.synced)
        assertEquals(3000L, lyrics.lines[0].startMs)
        assertEquals("behind cover", lyrics.lines[0].text)
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

    private fun be24(value: Int): ByteArray = byteArrayOf(
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun id3v24(frames: List<Pair<String, ByteArray>>) = id3(versionMajor = 4, frames = frames)

    /**
     * Build a minimal ID3v2 tag containing [frames]. Frame sizes are sync-safe for v2.4 and BE for
     * v2.3. When [extendedHeaderPad] > 0 (v2.3 only) a synthetic extended header of that many padding
     * bytes is emitted after the main header; its 4-byte BE size field EXCLUDES its own length, as the
     * v2.3 spec requires — the reader must skip `4 + size` to land on the first frame.
     */
    private fun id3(
        versionMajor: Int,
        frames: List<Pair<String, ByteArray>>,
        extendedHeaderPad: Int = 0,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        var flags = 0
        if (extendedHeaderPad > 0 && versionMajor == 3) {
            flags = flags or 0x40 // extended-header present
            // v2.3 extended header: size(4 BE, excludes these 4 bytes) | flags(2) | paddingSize(4) | ...
            val extBody = byteArrayOf(0, 0) + be32(extendedHeaderPad) + ByteArray(extendedHeaderPad)
            body.write(be32(extBody.size)) // size excludes its own 4 bytes
            body.write(extBody)
        }
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
        out.write(flags)        // flags
        out.write(syncSafe(bodyBytes.size)) // tag size is always sync-safe
        out.write(bodyBytes)
        return out.toByteArray()
    }

    /** Build a minimal ID3v2.2 tag: 6-byte frame headers (3-char id + 3-byte non-sync-safe size). */
    private fun id3v22(frames: List<Pair<String, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, data) in frames) {
            require(id.length == 3) { "v2.2 frame ids are 3 chars" }
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(be24(data.size)) // 3-byte non-sync-safe size
            body.write(data)
        }
        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(2) // version major = 2
        out.write(0) // revision
        out.write(0) // flags
        out.write(syncSafe(bodyBytes.size))
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

    /**
     * A FLAC file whose first metadata block is a large PICTURE (type 6) of [pictureSize] bytes,
     * followed by the (last) VORBIS_COMMENT block. Exercises the stream walk skipping a big cover to
     * reach a comment that sits past the in-memory head cap.
     */
    private fun flacWithPictureThenComments(pictureSize: Int, comments: List<String>): ByteArray {
        val body = vorbisCommentBody(comments)
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        // PICTURE block (type 6), not last.
        out.write(0x06)
        out.write(byteArrayOf(
            ((pictureSize ushr 16) and 0xFF).toByte(),
            ((pictureSize ushr 8) and 0xFF).toByte(),
            (pictureSize and 0xFF).toByte(),
        ))
        out.write(ByteArray(pictureSize)) // dummy cover data
        // VORBIS_COMMENT block (type 4), last.
        out.write(0x84)
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
