package com.viperplayer.domain.radio

import android.net.Uri
import com.viperplayer.domain.model.MediaId

/**
 * Synthetic identity for a "Song radio" playlist (issue #7). Radio is a virtual playlist generated
 * live from a seed track and its related songs — see [com.viperplayer.domain.repository.RadioPlaylistRepository].
 * Like the dynamic auto-playlists (see [com.viperplayer.domain.autoplaylist.AutoPlaylistType]) it
 * carries a synthetic [MediaId] under the reserved [PLUGIN_ID] plugin, so it flows through the normal
 * `PlaylistDetail` navigation and renders in the standard playlist detail screen rather than a bespoke
 * sheet.
 *
 * The radio's `sourceId` encodes the seed song's own [MediaId] (URL-encoded via [Uri.encode]) so the
 * seed round-trips exactly through navigation — [buildMediaId] packs it and [parseSeedId] unpacks it,
 * recovering the original seed (e.g. a `local` `content://…` URI or a plugin id) without corruption.
 */
object RadioPlaylist {

    /** Reserved plugin id for virtual radio playlists (never a real installed plugin). */
    const val PLUGIN_ID = "radio"

    /** True when [mediaId] refers to a virtual radio playlist (its plugin is [PLUGIN_ID]). */
    fun isRadioPlaylist(mediaId: MediaId): Boolean = mediaId.pluginId == PLUGIN_ID

    /**
     * The synthetic radio [MediaId] for the radio seeded by [seedId]. The seed's canonical string form
     * ([MediaId.toString]) is URL-encoded into the radio id's `sourceId` so [parseSeedId] can recover it
     * intact regardless of the reserved characters in the seed's plugin/source ids.
     */
    fun buildMediaId(seedId: MediaId): MediaId =
        MediaId(PLUGIN_ID, Uri.encode(seedId.toString()))

    /**
     * The original seed [MediaId] packed into [radioId] by [buildMediaId], or null when [radioId] is
     * not a radio id or its encoded seed is malformed. Reverses [buildMediaId]: URL-decode the
     * `sourceId` back to the seed's canonical string and parse it via [MediaId.fromString].
     */
    fun parseSeedId(radioId: MediaId): MediaId? {
        if (!isRadioPlaylist(radioId)) return null
        return try {
            MediaId.fromString(Uri.decode(radioId.sourceId))
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
