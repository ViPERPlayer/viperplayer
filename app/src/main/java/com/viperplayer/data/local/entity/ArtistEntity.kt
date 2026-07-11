package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Artist.
 *
 * [sourceId] is the plugin's stable id and the mark of a *navigable* artist. It is null for an
 * unlinked byline artist — a plain credited name (e.g. an un-hyperlinked "feat." artist) that has a
 * name to display but no artist page to open. Such a row is the sole home for that name; unlinked
 * bylines are never serialized elsewhere. The (pluginId, sourceId) unique index dedups navigable
 * artists; unlinked rows (null sourceId) are deduped by (pluginId, name) at the repository layer.
 */
@Entity(
    tableName = "artists",
    indices = [Index(value = ["pluginId", "sourceId"], unique = true)]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pluginId: String,
    val sourceId: String?,
    val name: String,
    val imageUrl: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

