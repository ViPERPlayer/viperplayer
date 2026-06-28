package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per individual play of a song — the raw signal behind the History and Stats screens.
 *
 * Unlike [SongEntity.playCount] / [SongEntity.lastPlayed] (which only keep running totals), each
 * play is recorded here with its own [timestamp] so we can compute time-windowed statistics
 * (this week / month / year / all-time) and a chronological listening history.
 *
 * Cascades on song delete so events never outlive their song.
 */
@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["timestamp"])
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
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Local [SongEntity.id] this play belongs to. */
    val songId: Long,
    /** Epoch millis when the play was recorded. */
    val timestamp: Long,
    /**
     * Optional actual listened duration in ms. Currently unused for stats (time-listened is derived
     * as playCount × song duration), but persisted for future precise listening-time tracking.
     */
    val durationListenedMs: Long? = null
)
