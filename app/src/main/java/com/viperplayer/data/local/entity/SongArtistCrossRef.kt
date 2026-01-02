package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for many-to-many relationship between Songs and Artists.
 */
@Entity(
    tableName = "song_artists",
    primaryKeys = ["songId", "artistId"],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["artistId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
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
data class SongArtistCrossRef(
    val songId: Long,
    val artistId: Long,
    val order: Int = 0 // Order of artist in the list (for featured artists, etc.)
)

