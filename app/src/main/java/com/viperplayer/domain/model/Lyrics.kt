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

/** A single timed lyric line (for synced lyrics). */
data class LyricsLine(
    val startMs: Long,
    val text: String,
)
