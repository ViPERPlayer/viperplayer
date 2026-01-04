package com.viperplayer.domain.model

/**
 * Represents a song/track.
 */
data class Song(
    override val id: MediaId,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long? = 0,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isExplicit: Boolean = false,
    val isPlayable: Boolean = true
) : MediaItem {
    val artistName: String
        get() = artists.firstOrNull()?.name ?: "Unknown Artist"

    val albumName: String
        get() = album?.name ?: ""

    val effectiveArtworkUrl: String?
        get() = artworkUrl ?: album?.artworkUrl
}

