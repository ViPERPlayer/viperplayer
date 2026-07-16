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

    // ---- Full text tags: ID3v2 ----------------------------------------------------------------

    @Test
    fun `id3v24 text frames map to AudioTags`() {
        val tag = id3v24(
            listOf(
                "TIT2" to textFrame("Bohemian Rhapsody"),
                "TPE1" to textFrame("Queen"),
                "TALB" to textFrame("A Night at the Opera"),
                "TPE2" to textFrame("Queen"),
                "TCOM" to textFrame("Freddie Mercury"),
                "TCON" to textFrame("Rock"),
                "TRCK" to textFrame("11/12"),
                "TPOS" to textFrame("1/1"),
                "TDRC" to textFrame("1975"),
                "TENC" to textFrame("LAME 3.100"),
                "COMM" to commFrame(description = "", comment = "remastered"),
            ),
        )
        val tags = LocalTagReader.readTags(tag)
        assertEquals("Bohemian Rhapsody", tags.title)
        assertEquals(listOf("Queen"), tags.artists)
        assertEquals("A Night at the Opera", tags.album)
        assertEquals("Queen", tags.albumArtist)
        assertEquals("Freddie Mercury", tags.composer)
        assertEquals("Rock", tags.genre)
        assertEquals("1975", tags.date)
        assertEquals("LAME 3.100", tags.encoder)
        assertEquals("remastered", tags.comment)
        assertEquals(TrackPosition(11, 12), tags.track)
        assertEquals(TrackPosition(1, 1), tags.disc)
        assertTrue(tags.hasAny())
    }

    @Test
    fun `id3 track number without a total leaves total null`() {
        val tag = id3v24(listOf("TRCK" to textFrame("5")))
        val tags = LocalTagReader.readTags(tag)
        assertEquals(TrackPosition(5, null), tags.track)
    }

    @Test
    fun `id3 numeric genre reference resolves to name`() {
        // "(17)" -> ID3v1 genre index 17 == "Rock".
        val tag = id3v24(listOf("TCON" to textFrame("(17)")))
        assertEquals("Rock", LocalTagReader.readTags(tag).genre)
    }

    @Test
    fun `id3v24 multi-value artist frame (NUL-separated) yields multiple artists`() {
        val tag = id3v24(listOf("TPE1" to textFrame("Simon\u0000Garfunkel")))
        val tags = LocalTagReader.readTags(tag)
        assertEquals(listOf("Simon", "Garfunkel"), tags.artists)
        assertEquals("Simon, Garfunkel", tags.artist)
    }

    @Test
    fun `id3 slash inside a value is not split into multiple artists`() {
        val tag = id3v24(listOf("TPE1" to textFrame("AC/DC")))
        assertEquals(listOf("AC/DC"), LocalTagReader.readTags(tag).artists)
    }

    @Test
    fun `id3 missing frames are omitted from AudioTags`() {
        val tag = id3v24(listOf("TIT2" to textFrame("Only a Title")))
        val tags = LocalTagReader.readTags(tag)
        assertEquals("Only a Title", tags.title)
        assertTrue(tags.artists.isEmpty())
        assertNull(tags.album)
        assertNull(tags.albumArtist)
        assertNull(tags.composer)
        assertNull(tags.genre)
        assertNull(tags.date)
        assertNull(tags.track)
        assertNull(tags.disc)
        assertNull(tags.comment)
        assertNull(tags.encoder)
    }

    @Test
    fun `id3 COMM with a non-empty description keeps only the comment text`() {
        val tag = id3v24(
            listOf("COMM" to commFrame(description = "iTunNORM", comment = "the real comment")),
        )
        assertEquals("the real comment", LocalTagReader.readTags(tag).comment)
    }

    @Test
    fun `id3 TCOM multi-value composers are joined`() {
        // Two NUL-separated composer values collapse into one comma-joined field.
        val tag = id3v24(listOf("TCOM" to textFrame("Lennon\u0000McCartney")))
        assertEquals("Lennon, McCartney", LocalTagReader.readTags(tag).composer)
    }

    @Test
    fun `id3v23 text frames (BE sizes) are read`() {
        val tag = id3(
            versionMajor = 3,
            frames = listOf(
                "TIT2" to textFrame("v2.3 Title"),
                "TYER" to textFrame("2001"),
            ),
        )
        val tags = LocalTagReader.readTags(tag)
        assertEquals("v2.3 Title", tags.title)
        assertEquals("2001", tags.date)
    }

    @Test
    fun `id3v23 TDAT does not shadow the TYER year`() {
        // In ID3v2.3, TDAT is a 4-char DDMM (day+month) string, NOT a year or ISO date; the year lives
        // in TYER. A file carrying both must resolve to the TYER year, never the DDMM value.
        val tag = id3(
            versionMajor = 3,
            frames = listOf(
                "TYER" to textFrame("1975"),
                "TDAT" to textFrame("0112"), // DD=01, MM=12 — must not become the date
            ),
        )
        val tags = LocalTagReader.readTags(tag)
        assertEquals("1975", tags.date)
    }

    @Test
    fun `id3v24 TDRC full timestamp takes precedence for the date`() {
        val tag = id3v24(listOf("TDRC" to textFrame("1994-08-15")))
        assertEquals("1994-08-15", LocalTagReader.readTags(tag).date)
    }

    @Test
    fun `id3v22 three-char text frames map to AudioTags`() {
        val tag = id3v22(
            listOf(
                "TT2" to textFrame("v2.2 Title"),
                "TP1" to textFrame("v2.2 Artist"),
                "TAL" to textFrame("v2.2 Album"),
            ),
        )
        val tags = LocalTagReader.readTags(tag)
        assertEquals("v2.2 Title", tags.title)
        assertEquals(listOf("v2.2 Artist"), tags.artists)
        assertEquals("v2.2 Album", tags.album)
    }

    @Test
    fun `id3 UTF-16 text frame decodes`() {
        val tag = id3v24(listOf("TIT2" to textFrame("café ☕", encoding = 1)))
        assertEquals("café ☕", LocalTagReader.readTags(tag).title)
    }

    // ---- Full text tags: FLAC / Vorbis --------------------------------------------------------

    @Test
    fun `flac vorbis comments map to AudioTags`() {
        val flac = flacWithComments(
            listOf(
                "TITLE=Money",
                "ARTIST=Pink Floyd",
                "ALBUM=The Dark Side of the Moon",
                "ALBUMARTIST=Pink Floyd",
                "COMPOSER=Roger Waters",
                "GENRE=Progressive Rock",
                "DATE=1973",
                "DISCNUMBER=1",
                "DISCTOTAL=1",
                "TRACKNUMBER=6",
                "TRACKTOTAL=10",
                "COMMENT=classic",
                "ENCODER=libFLAC 1.4",
            ),
        )
        val tags = LocalTagReader.readTags(flac)
        assertEquals("Money", tags.title)
        assertEquals(listOf("Pink Floyd"), tags.artists)
        assertEquals("The Dark Side of the Moon", tags.album)
        assertEquals("Pink Floyd", tags.albumArtist)
        assertEquals("Roger Waters", tags.composer)
        assertEquals("Progressive Rock", tags.genre)
        assertEquals("1973", tags.date)
        assertEquals("classic", tags.comment)
        assertEquals("libFLAC 1.4", tags.encoder)
        assertEquals(TrackPosition(6, 10), tags.track)
        assertEquals(TrackPosition(1, 1), tags.disc)
    }

    @Test
    fun `flac repeated ARTIST fields accumulate`() {
        val flac = flacWithComments(listOf("ARTIST=Daft Punk", "ARTIST=The Weeknd"))
        assertEquals(listOf("Daft Punk", "The Weeknd"), LocalTagReader.readTags(flac).artists)
    }

    @Test
    fun `flac TRACKNUMBER holding n-of-total is parsed without a TRACKTOTAL field`() {
        val flac = flacWithComments(listOf("TRACKNUMBER=3/9"))
        assertEquals(TrackPosition(3, 9), LocalTagReader.readTags(flac).track)
    }

    @Test
    fun `flac with only a title omits every other tag`() {
        val flac = flacWithComments(listOf("TITLE=Lonely Field"))
        val tags = LocalTagReader.readTags(flac)
        assertEquals("Lonely Field", tags.title)
        assertTrue(tags.artists.isEmpty())
        assertNull(tags.album)
        assertNull(tags.track)
    }

    @Test
    fun `flac tags are read from a stream past a large PICTURE block`() {
        val flac = flacWithPictureThenComments(
            pictureSize = 3 * 1024 * 1024,
            comments = listOf("TITLE=Behind Cover", "ARTIST=Streamed"),
        )
        val tags = LocalTagReader.readTags(flac.inputStream())
        assertEquals("Behind Cover", tags.title)
        assertEquals(listOf("Streamed"), tags.artists)
    }

    @Test
    fun `ogg vorbis comments map to AudioTags`() {
        val ogg = oggWithVorbisComments(listOf("TITLE=Ogg Track", "ARTIST=Ogg Artist"))
        val tags = LocalTagReader.readTags(ogg)
        assertEquals("Ogg Track", tags.title)
        assertEquals(listOf("Ogg Artist"), tags.artists)
    }

    @Test
    fun `unknown container yields empty AudioTags`() {
        val tags = LocalTagReader.readTags(byteArrayOf(0, 1, 2, 3, 4, 5))
        assertFalse(tags.hasAny())
        assertEquals(AudioTags.EMPTY, tags)
    }

    @Test
    fun `TrackPosition parse handles blanks and malformed input`() {
        assertNull(TrackPosition.parse(null))
        assertNull(TrackPosition.parse("   "))
        assertNull(TrackPosition.parse("abc"))
        assertEquals(TrackPosition(7, null), TrackPosition.parse("7"))
        assertEquals(TrackPosition(7, null), TrackPosition.parse("7/"))
        assertEquals(TrackPosition(2, 5), TrackPosition.parse(" 2 / 5 "))
    }

    // ---- byte builders ------------------------------------------------------------------------

    /** A plain ID3 text frame body: `encoding(1) | text` (text encoded per [encoding]). */
    private fun textFrame(text: String, encoding: Int = 3): ByteArray =
        byteArrayOf(encoding.toByte()) + textBytes(encoding, text)

    /** A COMM frame body: `encoding(1) | language(3) | description(text+term) | comment(text)`. */
    private fun commFrame(description: String, comment: String, encoding: Int = 3): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encoding)
        out.write("eng".toByteArray(Charsets.ISO_8859_1))
        out.write(textBytes(encoding, description))
        out.write(terminator(encoding))
        out.write(textBytes(encoding, comment))
        return out.toByteArray()
    }


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
