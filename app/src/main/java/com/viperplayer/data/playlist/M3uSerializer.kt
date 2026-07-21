package com.viperplayer.data.playlist

import com.viperplayer.domain.model.Song

/**
 * A single parsed entry from an M3U playlist: a [location] (URI or file path) plus optional
 * metadata carried by a preceding `#EXTINF` directive.
 */
data class M3uEntry(
    val location: String,
    val title: String? = null,
    val durationSec: Int? = null,
)

/**
 * Pure, Android-free (unit-testable) M3U (extended) reader/writer.
 *
 * ViPER songs are identified by a [com.viperplayer.domain.model.MediaId] rather than a file path,
 * so exported location lines use a custom `viper://<pluginId>/<sourceId>` URI, letting exported
 * playlists round-trip back into the app. Local-plugin songs whose `sourceId` is already a
 * playable `content://` / `file://` URI are written as that URI directly for interoperability.
 */
object M3uSerializer {

    private const val LOCAL_PLUGIN_MARKER = "local"
    const val VIPER_SCHEME = "viper://"

    /**
     * Serialize [songs] into an extended-M3U document. [playlistName] is written as a leading
     * comment for humans; it is not part of the standard and is ignored on parse.
     */
    fun serialize(playlistName: String, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        if (playlistName.isNotBlank()) {
            sb.append("#PLAYLIST:").append(playlistName.trim()).append('\n')
        }
        for (song in songs) {
            val seconds = song.durationMs?.let { (it / 1000).toInt() } ?: -1
            val artist = song.artistNames
            val label = if (artist != null) "$artist - ${song.title}" else song.title
            sb.append("#EXTINF:").append(seconds).append(',').append(label).append('\n')
            sb.append(locationFor(song)).append('\n')
        }
        return sb.toString()
    }

    /**
     * The location line for [song]: for a local-plugin song whose sourceId is already a playable
     * URI, that URI; otherwise a `viper://<pluginId>/<sourceId>` URI so the entry round-trips.
     */
    private fun locationFor(song: Song): String {
        val pluginId = song.id.routingPluginId
        val sourceId = song.id.sourceId
        if (pluginId.contains(LOCAL_PLUGIN_MARKER) && looksLikeUri(sourceId)) {
            return sourceId
        }
        return viperUri(pluginId, sourceId)
    }

    /** Builds a `viper://<pluginId>/<sourceId>` URI with both components percent-escaped. */
    fun viperUri(pluginId: String, sourceId: String): String =
        VIPER_SCHEME + encode(pluginId) + "/" + encode(sourceId)

    private fun looksLikeUri(value: String): Boolean =
        value.startsWith("content://") || value.startsWith("file://") || value.startsWith("/")

    /**
     * Parse an extended-M3U [content] into ordered [M3uEntry] rows. Blank lines and comment
     * directives other than `#EXTINF` are ignored; a `#EXTINF` attaches its title/duration to the
     * next location line.
     */
    fun parse(content: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:") -> {
                    val payload = line.removePrefix("#EXTINF:")
                    val commaIndex = payload.indexOf(',')
                    if (commaIndex >= 0) {
                        val durationPart = payload.substring(0, commaIndex).trim()
                        // Some writers append extra attributes after the seconds (e.g. "12 tvg-id=..");
                        // take the leading integer token only.
                        pendingDuration = durationPart.substringBefore(' ').toIntOrNull()
                            ?.takeIf { it >= 0 }
                        pendingTitle = payload.substring(commaIndex + 1).trim().ifEmpty { null }
                    } else {
                        pendingDuration = payload.trim().toIntOrNull()?.takeIf { it >= 0 }
                    }
                }
                // Any other comment/directive line is ignored, but must not consume the pending EXTINF.
                line.startsWith("#") -> Unit
                else -> {
                    entries.add(M3uEntry(location = line, title = pendingTitle, durationSec = pendingDuration))
                    pendingTitle = null
                    pendingDuration = null
                }
            }
        }
        return entries
    }

    /**
     * Minimal percent-encoding for the reserved characters that would otherwise break a
     * `viper://a/b` location line ('/', '%', whitespace). Kept Android-free for unit testing;
     * mirror the decode in [decode].
     */
    private fun encode(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '%' -> sb.append("%25")
                '/' -> sb.append("%2F")
                ' ' -> sb.append("%20")
                '\n', '\r', '\t' -> sb.append("%20")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun decode(value: String): String {
        if ('%' !in value) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * Parses a `viper://<pluginId>/<sourceId>` location into a (pluginId, sourceId) pair, or null
     * when [location] is not a viper URI or is malformed.
     */
    fun parseViperUri(location: String): Pair<String, String>? {
        if (!location.startsWith(VIPER_SCHEME)) return null
        val rest = location.removePrefix(VIPER_SCHEME)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.length - 1) return null
        val pluginId = decode(rest.substring(0, slash))
        val sourceId = decode(rest.substring(slash + 1))
        if (pluginId.isBlank() || sourceId.isBlank()) return null
        return pluginId to sourceId
    }
}
