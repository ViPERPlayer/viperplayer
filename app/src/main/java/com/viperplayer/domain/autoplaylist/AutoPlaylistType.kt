package com.viperplayer.domain.autoplaylist

import com.viperplayer.domain.model.MediaId

/**
 * The kinds of dynamic ("smart") auto-playlists surfaced in the Library. Each is computed live from
 * the current library and the listening play-history — see [AutoPlaylistGenerator]. Auto-playlists
 * are virtual: they carry a synthetic [MediaId] under the reserved [PLUGIN_ID] plugin whose
 * `sourceId` is the enum's [id], and they are re-derived on demand rather than stored.
 *
 * @param id stable string key used as the auto-playlist's [MediaId.sourceId] (and in navigation).
 * @param usesPlayHistory true when generation reads the stats play-history (vs. library-only). Used
 *   to hide play-based playlists until there is any history to derive them from.
 */
enum class AutoPlaylistType(val id: String, val usesPlayHistory: Boolean) {
    /** Library songs by date-added, newest first (library-only). */
    RECENTLY_ADDED("recently_added", usesPlayHistory = false),

    /** Songs ranked by all-time play count from the play-history. */
    MOST_PLAYED("most_played", usesPlayHistory = true),

    /** Distinct songs by most-recent play, newest first, from the play-history. */
    RECENTLY_PLAYED("recently_played", usesPlayHistory = true),

    /** The user's liked/favorite songs (library-only). */
    FAVORITES("favorites", usesPlayHistory = false),

    /** Played before but not recently — rediscovery candidates (play-history). */
    FORGOTTEN("forgotten", usesPlayHistory = true),

    /** Every library song, shuffled (library-only). */
    SHUFFLE_ALL("shuffle_all", usesPlayHistory = false),
    ;

    /** The synthetic [MediaId] identifying this auto-playlist across the app. */
    val mediaId: MediaId get() = MediaId.Plugin(PLUGIN_ID, id)

    companion object {
        /** Reserved plugin id for virtual auto-playlists (never a real installed plugin). */
        const val PLUGIN_ID = "auto"

        /** The [AutoPlaylistType] for [id], or null if it is not a known auto-playlist. */
        fun fromId(id: String): AutoPlaylistType? = entries.firstOrNull { it.id == id }

        /** True when [mediaId] refers to a virtual auto-playlist (its plugin is [PLUGIN_ID]). */
        fun isAutoPlaylist(mediaId: MediaId): Boolean =
            mediaId is MediaId.Plugin && mediaId.pluginId == PLUGIN_ID
    }
}
