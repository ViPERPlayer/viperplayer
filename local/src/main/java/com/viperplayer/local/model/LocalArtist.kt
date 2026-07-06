package com.viperplayer.local.model

/**
 * Internal model representing an artist aggregated from MediaStore songs.
 * [name] is null for the "unknown artist" bucket (files with no artist tag).
 */
data class LocalArtist(
    val id: Long,
    val name: String?,
    val songs: List<LocalSong>,
    val albums: List<LocalAlbum>,
)
