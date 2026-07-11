package com.viperplayer.data.lyrics

import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.LyricsWord
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.util.LrcParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host-side lyrics fallback backed by LRCLIB (lrclib.net) — free, no auth, community line-synced
 * lyrics. Used only when the source plugin can't supply lyrics itself. Returns null on any miss or
 * error so the caller degrades gracefully.
 *
 * LRCLIB serves standard LRC (line-level) rather than word-timed lyrics; word-by-word highlighting
 * therefore only lights up when a plugin returns enhanced/word-timed data, which the pipeline
 * already supports via [LrcParser].
 */
@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LrcLibRecord(
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
        val instrumental: Boolean = false,
    )

    /** Fetch lyrics for [song]: exact metadata match first, then a fuzzy search fallback. */
    suspend fun getLyrics(song: Song): Lyrics? {
        val title = song.title.takeIf { it.isNotBlank() } ?: return null
        val artist = song.artistNames?.takeIf { it.isNotBlank() }

        val record = runCatching { getExact(title, artist, song.album?.name, song.durationMs) }
            .onFailure { Timber.w(it, "LRCLIB get failed for '$title'") }
            .getOrNull()
            ?: runCatching { search(title, artist) }
                .onFailure { Timber.w(it, "LRCLIB search failed for '$title'") }
                .getOrNull()
        record ?: return null
        if (record.instrumental) return null

        val text = record.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: record.plainLyrics?.takeIf { it.isNotBlank() }
            ?: return null
        return toDomain(text)
    }

    private suspend fun getExact(title: String, artist: String?, album: String?, durationMs: Long?): LrcLibRecord? {
        val response = httpClient.get(GET_URL) {
            header("User-Agent", USER_AGENT)
            parameter("track_name", title)
            if (artist != null) parameter("artist_name", artist)
            if (!album.isNullOrBlank()) parameter("album_name", album)
            if (durationMs != null && durationMs > 0) parameter("duration", (durationMs / 1000).toString())
        }
        if (!response.status.isSuccess()) return null
        return json.decodeFromString<LrcLibRecord>(response.bodyAsText())
    }

    private suspend fun search(title: String, artist: String?): LrcLibRecord? {
        val response = httpClient.get(SEARCH_URL) {
            header("User-Agent", USER_AGENT)
            parameter("track_name", title)
            if (artist != null) parameter("artist_name", artist)
        }
        if (!response.status.isSuccess()) return null
        val results = json.decodeFromString<List<LrcLibRecord>>(response.bodyAsText())
        return results.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: results.firstOrNull()
    }

    private fun toDomain(text: String): Lyrics {
        if (!LrcParser.looksLikeLrc(text)) {
            return Lyrics(synced = false, lines = emptyList(), plainText = text)
        }
        val parsed = LrcParser.parse(text)
        return Lyrics(
            synced = parsed.lines.isNotEmpty(),
            lines = parsed.lines.map { line ->
                LyricsLine(
                    startMs = line.startMs,
                    text = line.text,
                    words = line.words.map { LyricsWord(startMs = it.startMs, text = it.text) },
                )
            },
            plainText = if (parsed.lines.isEmpty()) text else null,
        )
    }

    private companion object {
        const val GET_URL = "https://lrclib.net/api/get"
        const val SEARCH_URL = "https://lrclib.net/api/search"
        const val USER_AGENT = "ViPER-Player (https://github.com/iscle/ViPER-Player)"
    }
}
