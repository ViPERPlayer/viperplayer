package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Artist.
 */
@Entity(
    tableName = "artists",
    indices = [Index(value = ["pluginId", "sourceId"], unique = true)]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val imageUrl: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

