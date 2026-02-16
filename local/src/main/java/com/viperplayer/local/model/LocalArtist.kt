package com.viperplayer.local.model

/**
 * Internal model representing an artist aggregated from MediaStore songs.
 */
data class LocalArtist(
    val id: Long,
    val name: String,
    val songs: List<LocalSong>,
    val albums: List<LocalAlbum>
)
