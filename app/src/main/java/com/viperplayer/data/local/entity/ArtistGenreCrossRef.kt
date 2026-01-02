package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for many-to-many relationship between Artists and Genres.
 */
@Entity(
    tableName = "artist_genres",
    primaryKeys = ["artistId", "genreId"],
    indices = [
        Index(value = ["artistId"]),
        Index(value = ["genreId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArtistGenreCrossRef(
    val artistId: Long,
    val genreId: Long
)

