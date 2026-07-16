package com.viperplayer.alarm.domain

import com.viperplayer.domain.model.MediaId

/**
 * "What to play" when an alarm fires. Kept as a small sealed hierarchy so new sources can be added
 * without touching the schedule/fire machinery. Persisted as a compact string via [encode] /
 * [decode] (no JSON — matches the app's [MediaId] string convention).
 */
sealed interface AlarmContentSpec {

    /** Shuffle everything in the local library (all saved songs). The default. */
    data object ShuffleAll : AlarmContentSpec

    /** Play a specific playlist (shuffled if [shuffle]). */
    data class PlaylistContent(val playlistId: MediaId, val shuffle: Boolean = false) : AlarmContentSpec

    fun encode(): String = when (this) {
        ShuffleAll -> SHUFFLE_ALL
        is PlaylistContent -> "$PLAYLIST_PREFIX${if (shuffle) "1" else "0"}|${playlistId}"
    }

    companion object {
        private const val SHUFFLE_ALL = "shuffle_all"
        private const val PLAYLIST_PREFIX = "playlist:"

        val Default: AlarmContentSpec = ShuffleAll

        /** Inverse of [encode]; falls back to [ShuffleAll] on any malformed input. */
        fun decode(raw: String?): AlarmContentSpec {
            if (raw.isNullOrBlank() || raw == SHUFFLE_ALL) return ShuffleAll
            if (raw.startsWith(PLAYLIST_PREFIX)) {
                val body = raw.removePrefix(PLAYLIST_PREFIX)
                val shuffle = body.startsWith("1|")
                val idPart = body.substringAfter('|', missingDelimiterValue = "")
                return runCatching { MediaId.fromString(idPart) }
                    .map { PlaylistContent(it, shuffle) as AlarmContentSpec }
                    .getOrDefault(ShuffleAll)
            }
            return ShuffleAll
        }
    }
}
