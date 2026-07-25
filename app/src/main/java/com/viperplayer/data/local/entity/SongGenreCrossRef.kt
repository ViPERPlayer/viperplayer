package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for the many-to-many relationship between Songs and Genres.
 *
 * Genre is a per-song tag (plugins carry it on [com.viperplayer.plugin.model.Song.genres], local files
 * read it from their metadata), so the browsable "songs in a genre" relation lives here rather than being
 * derived through the artist → genre link. A song may belong to several genres and a genre to many songs.
 */
@Entity(
    tableName = "song_genres",
    primaryKeys = ["songId", "genreId"],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["genreId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
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
data class SongGenreCrossRef(
    val songId: Long,
    val genreId: Long
)
