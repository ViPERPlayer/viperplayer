package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Genre.
 */
@Entity(
    tableName = "genres",
    indices = [Index(value = ["name"], unique = true)]
)
data class GenreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)

