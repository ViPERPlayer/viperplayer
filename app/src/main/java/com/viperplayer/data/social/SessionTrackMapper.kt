package com.viperplayer.data.social

import com.viperplayer.domain.model.SessionTrack
import com.viperplayer.domain.model.Song

/**
 * Maps a domain [Song] to the wire-facing [SessionTrack] the host broadcasts (layer 2, part B).
 *
 * The portable identity comes from the song's [Song.id] (plugin + source); display metadata (title,
 * artist byline, artwork, duration) rides along so followers lacking that plugin still render a
 * labelled entry. [SessionTrack.album] is left empty to match the backend MediaRef (which carries no
 * album field) — the round-trip through the server would drop it anyway.
 */
fun Song.toSessionTrack(): SessionTrack = SessionTrack(
    pluginId = id.routingPluginId,
    sourceId = id.sourceId,
    title = title,
    artist = artistNames.orEmpty(),
    album = "",
    artworkUrl = artworkUrl.orEmpty(),
    durationMs = durationMs ?: 0L,
)
