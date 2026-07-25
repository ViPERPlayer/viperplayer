package com.viperplayer.data.lyrics

import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.LyricsWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON (de)serialization for a [Lyrics] value, used to persist a manually-picked lyric-match
 * override in Room. The domain [Lyrics] model is intentionally framework-free (not `@Serializable`),
 * so it is mapped through the private DTOs below rather than annotated directly.
 */
object LyricsJson {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LyricsDto(
        val synced: Boolean,
        val lines: List<LineDto>,
        val plainText: String? = null,
    )

    @Serializable
    private data class LineDto(
        val startMs: Long,
        val text: String,
        val words: List<WordDto> = emptyList(),
    )

    @Serializable
    private data class WordDto(
        val startMs: Long,
        val text: String,
    )

    fun encode(lyrics: Lyrics): String = json.encodeToString(
        LyricsDto(
            synced = lyrics.synced,
            lines = lyrics.lines.map { line ->
                LineDto(
                    startMs = line.startMs,
                    text = line.text,
                    words = line.words.map { WordDto(it.startMs, it.text) },
                )
            },
            plainText = lyrics.plainText,
        )
    )

    fun decode(text: String): Lyrics {
        val dto = json.decodeFromString<LyricsDto>(text)
        return Lyrics(
            synced = dto.synced,
            lines = dto.lines.map { line ->
                LyricsLine(
                    startMs = line.startMs,
                    text = line.text,
                    words = line.words.map { LyricsWord(it.startMs, it.text) },
                )
            },
            plainText = dto.plainText,
        )
    }
}
