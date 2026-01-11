package com.viperplayer.domain.model

/**
 * Represents an artist.
 * Based on AIDL Artist model - only contains fields that exist in the plugin API.
 */
data class Artist(
    override val id: MediaId,
    val name: String,
    val imageUrl: String? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val featuring: List<Playlist> = emptyList(),
    val appearsOn: List<MediaItem> = emptyList(),
    val similarArtists: List<Artist> = emptyList()
) : MediaItem
