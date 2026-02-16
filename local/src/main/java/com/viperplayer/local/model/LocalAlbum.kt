package com.viperplayer.local.model

import android.net.Uri

/**
 * Internal model representing an album aggregated from MediaStore songs.
 */
data class LocalAlbum(
    val id: Long,
    val name: String,
    val artistId: Long,
    val artistName: String,
    val year: Int?,
    val artworkUri: Uri?,
    val songs: List<LocalSong>
) {
    val trackCount: Int get() = songs.size
}
