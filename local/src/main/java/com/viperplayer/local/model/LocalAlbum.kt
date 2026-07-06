package com.viperplayer.local.model

import android.net.Uri

/**
 * Internal model representing an album aggregated from MediaStore songs.
 *
 * [albumArtistName]/[albumArtistId] come from the ALBUM_ARTIST tag when present, otherwise from the
 * majority-song-artist heuristic (see LocalMediaScanner.findBestAlbumArtist); both are null when no
 * confident owner could be determined (e.g. a compilation with mixed artists).
 */
data class LocalAlbum(
    val id: Long,
    val name: String?,
    val albumArtistId: Long?,
    val albumArtistName: String?,
    val year: Int?,
    val isCompilation: Boolean,
    val artworkUri: Uri?,
    /** Sorted by disc number, then track number. */
    val songs: List<LocalSong>,
) {
    val trackCount: Int get() = songs.size
}
