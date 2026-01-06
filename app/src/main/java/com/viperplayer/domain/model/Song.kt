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
    val isPlayable: Boolean = true,
    val requiresInternet: Boolean = true, // Default to true for streaming services
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false
) : MediaItem {
    val artistNames: String?
        get() = artists.joinToString { it.name }.takeIf { it.isNotEmpty() }
}

