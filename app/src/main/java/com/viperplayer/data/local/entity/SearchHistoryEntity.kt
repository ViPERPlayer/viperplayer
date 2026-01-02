package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Search History.
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["query"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

