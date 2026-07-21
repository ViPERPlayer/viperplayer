package com.viperplayer.domain.radio

import com.viperplayer.domain.model.MediaId

/**
 * Synthetic identity for a "Song radio" playlist (issue #7). Radio is a virtual playlist generated
 * live from a seed track and its related songs — see [com.viperplayer.domain.repository.RadioPlaylistRepository].
 * Like the dynamic auto-playlists (see [com.viperplayer.domain.autoplaylist.AutoPlaylistType]) it
 * carries a synthetic [MediaId] so it flows through the normal `PlaylistDetail` navigation and renders
 * in the standard playlist detail screen rather than a bespoke sheet.
 *
 * The radio's identity is the typed [MediaId.Radio] variant, which holds the seed [MediaId] directly —
 * there is no magic plugin-id string and no URL-packing of the seed; [buildMediaId] wraps the seed and
 * [parseSeedId] unwraps it, recovering the original seed (e.g. a `local` `content://…` URI or a plugin
 * id) exactly.
 */
object RadioPlaylist {

    /** True when [mediaId] refers to a virtual radio playlist. */
    fun isRadioPlaylist(mediaId: MediaId): Boolean = mediaId is MediaId.Radio

    /** The synthetic radio [MediaId] for the radio seeded by [seedId]. */
    fun buildMediaId(seedId: MediaId): MediaId = MediaId.Radio(seedId)

    /** The original seed [MediaId] wrapped in [radioId], or null when [radioId] is not a radio id. */
    fun parseSeedId(radioId: MediaId): MediaId? = (radioId as? MediaId.Radio)?.seed
}
