package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Album.
 */
@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["idType", "pluginId", "sourceId"], unique = true),
        Index(value = ["primaryArtistId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryArtistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // MediaId identity, type-discriminated: idType is "plugin"|"local"; pluginId is "" for local.
    val idType: String,
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val primaryArtistId: Long? = null, // Reference to first artist
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val trackCount: Int = 0,
    val type: AlbumType = AlbumType.ALBUM,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isDownloaded: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

