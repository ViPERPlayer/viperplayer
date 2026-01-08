package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Song.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["pluginId", "sourceId"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["isLiked"]),
        Index(value = ["isSaved"]),
        Index(value = ["isDownloaded"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pluginId: String,
    val sourceId: String,
    val title: String,
    val albumId: Long? = null, // Reference to album
    val durationMs: Long? = 0,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isExplicit: Boolean = false,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadPath: String? = null,
    val localArtworkPath: String? = null, // Local path to cached artwork
    // Audio normalization
    val replayGainDb: Float? = null, // ReplayGain value in dB
    val peakAmplitude: Float? = null, // Peak amplitude (0.0-1.0+)
    val playCount: Long = 0,
    val lastPlayed: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)

