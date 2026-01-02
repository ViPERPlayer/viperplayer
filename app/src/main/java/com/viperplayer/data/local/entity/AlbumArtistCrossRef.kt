package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for many-to-many relationship between Albums and Artists.
 */
@Entity(
    tableName = "album_artists",
    primaryKeys = ["albumId", "artistId"],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["artistId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AlbumArtistCrossRef(
    val albumId: Long,
    val artistId: Long,
    val order: Int = 0 // Order of artist in the list
)

