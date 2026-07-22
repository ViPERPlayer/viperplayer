package com.viperplayer.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Album type.
 */
@Serializable
@Parcelize
enum class AlbumType : Parcelable {
    ALBUM, SINGLE, EP, COMPILATION
}

/**
 * Represents an album.
 */
@Serializable
@Parcelize
@Immutable
data class Album(
    override val id: MediaId,
    val name: String,
    val artists: List<ArtistRef> = emptyList(),
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val trackCount: Int = 0,
    val type: AlbumType = AlbumType.ALBUM,
    val songs: List<Song>? = null
) : MediaItem, Parcelable {
    val artistName: String?
        get() = artists.firstOrNull()?.name
}

