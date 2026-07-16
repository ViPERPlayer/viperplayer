package com.viperplayer.local

import android.content.Context
import com.viperplayer.local.data.LocalLyricsSource
import com.viperplayer.plugin.author.LyricsProvider
import com.viperplayer.plugin.model.Lyrics
import com.viperplayer.plugin.model.LyricsRequest

/**
 * [LyricsProvider] for the embedded `local` plugin. The host's lyrics pipeline (plugin-first, then
 * LRCLIB) calls this for local songs; [LyricsRequest.songId] is the song's `content://` URI, which we
 * resolve back to its [com.viperplayer.local.model.LocalSong] (for the on-disk file path) before
 * reading embedded tags and any sidecar lyric file via [LocalLyricsSource].
 *
 * Returns null when the song isn't found or carries no lyrics, so the host falls back to LRCLIB.
 */
class LocalLyricsProvider(
    context: Context,
    private val source: LocalSource,
) : LyricsProvider {

    private val lyricsSource = LocalLyricsSource(context.applicationContext.contentResolver)

    override suspend fun getLyrics(request: LyricsRequest): Lyrics? {
        val song = source.findSongByUri(request.songId) ?: return null
        return lyricsSource.getLyrics(song)
    }
}
