package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table for player queue.
 * Links songs to their position in the queue.
 */
@Entity(
    tableName = "queue_songs",
    primaryKeys = ["songId"],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["position"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QueueSongCrossRef(
    val songId: Long,
    val position: Int = 0 // Position in queue (0-indexed)
)
