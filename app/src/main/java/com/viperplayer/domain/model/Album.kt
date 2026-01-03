package com.viperplayer.domain.model

/**
 * Album type.
 */
enum class AlbumType {
    ALBUM, SINGLE, EP, COMPILATION
}

/**
 * Represents an album.
 */
data class Album(
    val id: MediaId,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val trackCount: Int = 0,
    val type: AlbumType = AlbumType.ALBUM,
    val songs: List<Song>? = null
) {
    val artistName: String
        get() = artists.firstOrNull()?.name ?: "Unknown Artist"
}

