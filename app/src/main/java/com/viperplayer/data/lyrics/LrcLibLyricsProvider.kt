package com.viperplayer.data.lyrics

import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsCandidate
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
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val duration: Double? = null,
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
        val results = searchRecords(title, artist)
        return results.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: results.firstOrNull()
    }

    /**
     * Manual lyric-match search: return the LRCLIB candidates for [title]/[artist] with their
     * display metadata and already-parsed lyrics, so the caller can present a picker and apply a
     * chosen result with no second round-trip. Instrumental and empty-lyric records are dropped; the
     * list is ordered synced-first (best matches on top). Returns an empty list on any miss/error.
     */
    suspend fun searchLyrics(title: String, artist: String?): List<LyricsCandidate> {
        val trackName = title.takeIf { it.isNotBlank() } ?: return emptyList()
        val records = runCatching { searchRecords(trackName, artist?.takeIf { it.isNotBlank() }) }
            .onFailure { Timber.w(it, "LRCLIB manual search failed for '$trackName'") }
            .getOrDefault(emptyList())
        return records.mapNotNull { record ->
            if (record.instrumental) return@mapNotNull null
            val text = record.syncedLyrics?.takeIf { it.isNotBlank() }
                ?: record.plainLyrics?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val lyrics = toDomain(text)
            if (lyrics.isEmpty) return@mapNotNull null
            LyricsCandidate(
                trackName = record.trackName?.takeIf { it.isNotBlank() } ?: trackName,
                artistName = record.artistName?.takeIf { it.isNotBlank() },
                albumName = record.albumName?.takeIf { it.isNotBlank() },
                durationMs = record.duration?.takeIf { it > 0 }?.let { (it * 1000).toLong() },
                synced = lyrics.synced,
                lyrics = lyrics,
            )
        }.sortedByDescending { it.synced }
    }

    private suspend fun searchRecords(title: String, artist: String?): List<LrcLibRecord> {
        val response = httpClient.get(SEARCH_URL) {
            header("User-Agent", USER_AGENT)
            parameter("track_name", title)
            if (artist != null) parameter("artist_name", artist)
        }
        if (!response.status.isSuccess()) return emptyList()
        return json.decodeFromString<List<LrcLibRecord>>(response.bodyAsText())
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
