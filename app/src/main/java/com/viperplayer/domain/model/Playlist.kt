package com.viperplayer.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents a playlist.
 */
@Serializable
@Parcelize
data class Playlist(
    override val id: MediaId,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val ownerName: String? = null,
    val songCount: Int = 0,
    val isPublic: Boolean = true,
    val isEditable: Boolean = false,
    val songs: List<Song>? = null
) : MediaItem, Parcelable
