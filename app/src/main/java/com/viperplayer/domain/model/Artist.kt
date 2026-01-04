package com.viperplayer.domain.model

/**
 * Represents an artist.
 */
data class Artist(
    override val id: MediaId,
    val name: String,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val followerCount: Long? = null,
    val bio: String? = null
) : MediaItem
