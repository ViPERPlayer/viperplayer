package com.viperplayer.local.data

import android.content.ContentResolver
import android.net.Uri
import com.viperplayer.local.model.LocalSong
import com.viperplayer.plugin.model.Lyrics
import com.viperplayer.plugin.util.LyricsFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves lyrics for a local song, mirroring a reference app: first embedded tags (ID3 SYLT/USLT, Vorbis
 * `LYRICS`/`UNSYNCEDLYRICS`), then a sidecar file (`.lrc`/`.ttml`/`.srt`) with the same base name next
 * to the audio file. Synced always wins over unsynced.
 *
 * Embedded tags are read from the song's `content://` URI via the [ContentResolver]; sidecars are
 * read from the file system by the audio file's own path (best-effort — a miss just means no
 * sidecar). The pure format/selection rules live in [LyricsFiles] and [LocalTagReader]; this class is
 * only the I/O glue.
 */
class LocalLyricsSource(
    private val contentResolver: ContentResolver,
) {
    /** Lyrics for [song], or null when neither embedded tags nor a sidecar file yield any. */
    suspend fun getLyrics(song: LocalSong): Lyrics? = withContext(Dispatchers.IO) {
        val embedded = readEmbedded(song.contentUri)
        if (embedded != null && embedded.synced) return@withContext embedded

        val sidecar = song.filePath?.let { readSidecar(it) }
        LyricsFiles.pickBest(listOf(embedded, sidecar))
    }

    /** Open the audio file and pull embedded lyric tags from its head; null on any error. */
    private fun readEmbedded(uri: Uri): Lyrics? = runCatching {
        contentResolver.openInputStream(uri)?.use { LocalTagReader.readEmbeddedLyrics(it) }
    }.getOrNull()

    /**
     * Probe for a sidecar lyric file next to [audioPath] in preference order and parse the first that
     * exists and yields lyrics. The parent directory is listed and matched case-insensitively so an
     * uppercase extension (`Song.LRC`) is still found. Returns null when none are found/readable.
     */
    private fun readSidecar(audioPath: String): Lyrics? {
        val parent = File(audioPath).parentFile ?: return null
        val siblingNames = runCatching { parent.list()?.toList() }.getOrNull() ?: return null
        val match = LyricsFiles.resolveSidecarName(audioPath, siblingNames) ?: return null
        val file = File(parent, match)
        val content = runCatching { if (file.isFile) file.readText() else null }.getOrNull() ?: return null
        return LyricsFiles.parseSidecar(match, content)
    }
}
