package com.viperplayer.domain.model

/**
 * Lyrics for a track. [synced] reports whether [lines] carry real timestamps; [plainText] is an
 * unsynced fallback. A lyrics-capable plugin fills whichever it has.
 */
data class Lyrics(
    val synced: Boolean,
    val lines: List<LyricsLine>,
    val plainText: String?,
) {
    val isEmpty: Boolean
        get() = lines.isEmpty() && plainText.isNullOrBlank()

    /** True when at least one line carries per-word timings, i.e. word-by-word highlight is possible. */
    val wordSynced: Boolean
        get() = synced && lines.any { it.words.isNotEmpty() }

    /**
     * Index of the line that should be highlighted at [positionMs] for synced lyrics
     * (the last line whose start time has passed), or -1 before the first line / when not synced.
     */
    fun currentLineIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var index = -1
        for (i in lines.indices) {
            if (lines[i].startMs <= positionMs) index = i else break
        }
        return index
    }
}

/**
 * A single timed lyric line (for synced lyrics). [words], when non-empty, carries per-word timings
 * so the line can be highlighted word-by-word; otherwise the whole line highlights at [startMs].
 */
data class LyricsLine(
    val startMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
) {
    /**
     * Index of the word being sung at [positionMs] (the last word whose start has passed), or -1
     * before the first word. Only meaningful when [words] is non-empty.
     */
    fun currentWordIndex(positionMs: Long): Int {
        var index = -1
        for (i in words.indices) {
            if (words[i].startMs <= positionMs) index = i else break
        }
        return index
    }
}

/** A single timed word/syllable within a [LyricsLine]. */
data class LyricsWord(
    val startMs: Long,
    val text: String,
)
