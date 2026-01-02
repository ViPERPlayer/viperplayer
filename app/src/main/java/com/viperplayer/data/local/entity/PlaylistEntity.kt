package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Playlist.
 */
@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["pluginId", "sourceId"], unique = true),
        Index(value = ["isLiked"]),
        Index(value = ["isSaved"])
    ]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val ownerName: String? = null,
    val songCount: Int = 0,
    val isPublic: Boolean = true,
    val isEditable: Boolean = false,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isDownloaded: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

