package com.viperplayer.presentation.widget

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist

/**
 * A single tappable row in the playlist / quick-launch widget: the [mediaId] to start playing, a
 * display [name], an optional [artworkUrl], and the [songCount] to format into a subtitle. The id
 * is kept as a typed [MediaId] (encoded only at the RemoteViews boundary) so this stays Android-free.
 */
data class PlaylistWidgetEntry(
    val mediaId: MediaId,
    val name: String,
    val artworkUrl: String?,
    val songCount: Int,
)

/**
 * Pure selection of the widget's playlist rows: the virtual "Liked Songs" playlist first (only when
 * it has songs), then the user's local playlists (already newest-first from the repository), capped
 * at [limit]. Kept Android-free so it can be unit-tested directly.
 */
object PlaylistWidgetData {

    const val MAX_ENTRIES = 12

    fun select(
        likedSongs: Playlist,
        localPlaylists: List<Playlist>,
        limit: Int = MAX_ENTRIES,
    ): List<PlaylistWidgetEntry> {
        val rows = ArrayList<PlaylistWidgetEntry>(localPlaylists.size + 1)
        if (likedSongs.songCount > 0) rows += likedSongs.toEntry()
        localPlaylists.forEach { rows += it.toEntry() }
        return if (rows.size > limit) rows.subList(0, limit).toList() else rows
    }

    private fun Playlist.toEntry(): PlaylistWidgetEntry = PlaylistWidgetEntry(
        mediaId = id,
        name = name,
        artworkUrl = artworkUrl,
        songCount = songCount,
    )
}
