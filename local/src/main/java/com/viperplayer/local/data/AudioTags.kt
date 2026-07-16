package com.viperplayer.local.data

/**
 * The full text-tag / metadata set parsed out of a local audio file's tag region, container-agnostic
 * (ID3v2 for MP3, Vorbis comments for FLAC/Ogg). Every field is nullable and only populated when the
 * corresponding frame/comment is actually present — the UI omits empties.
 *
 * A [TrackPosition] carries the common `n/total` form (ID3 `TRCK`/`TPOS`, Vorbis
 * `TRACKNUMBER`+`TRACKTOTAL`/`DISCNUMBER`+`DISCTOTAL`), where `total` may be absent.
 *
 * This is a pure data holder produced by [LocalTagReader.readTags]; it carries no Android types so the
 * parsing that fills it stays unit-testable on the JVM.
 */
data class AudioTags(
    val title: String? = null,
    /** All performer/artist values, in file order; multi-value tags are split. */
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    /** Recording year/date as written in the tag (e.g. "2021" or "2021-06-14"). */
    val date: String? = null,
    val track: TrackPosition? = null,
    val disc: TrackPosition? = null,
    val comment: String? = null,
    val encoder: String? = null,
) {
    /** True when at least one field carries a value — the caller can skip a whole empty section. */
    fun hasAny(): Boolean =
        title != null || artists.isNotEmpty() || album != null || albumArtist != null ||
            composer != null || genre != null || date != null || track != null ||
            disc != null || comment != null || encoder != null

    /** Convenience: the artists joined for display, or null when there are none. */
    val artist: String? get() = artists.joinToString(", ").takeIf { it.isNotEmpty() }

    companion object {
        val EMPTY = AudioTags()
    }
}

/**
 * A `number` optionally out of a `total`, as tags encode track and disc positions. [total] is null
 * when the tag carries only the number (e.g. `TRCK` = "5" rather than "5/12").
 */
data class TrackPosition(val number: Int, val total: Int? = null) {
    companion object {
        /**
         * Parse an ID3 `n` / `n/total` position string (used by `TRCK` and `TPOS`). Returns null when
         * no leading integer can be read. Leading/trailing whitespace is tolerated; a blank or absent
         * total yields [TrackPosition.total] == null.
         */
        fun parse(raw: String?): TrackPosition? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val slash = text.indexOf('/')
            val numberPart = if (slash >= 0) text.substring(0, slash) else text
            val number = numberPart.trim().toIntOrNull() ?: return null
            val total = if (slash >= 0) text.substring(slash + 1).trim().toIntOrNull() else null
            return TrackPosition(number, total)
        }

        /** Combine a separate number field and total field (Vorbis TRACKNUMBER + TRACKTOTAL). */
        fun of(number: String?, total: String?): TrackPosition? {
            val n = number?.trim()?.toIntOrNull() ?: return null
            val t = total?.trim()?.toIntOrNull()
            return TrackPosition(n, t)
        }
    }
}
