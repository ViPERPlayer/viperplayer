package com.viperplayer.data.stats

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per counted play, in the app's dedicated **stats** database ("viperplayer_stats").
 *
 * This is intentionally SEPARATE from the library's `play_events` table: those rows are foreign-keyed
 * to a local [com.viperplayer.data.local.entity.SongEntity] and therefore only exist for songs in the
 * local library. Listening stats must cover EVERY source (streaming plugins included), so each play
 * is fully denormalized here — title / artist / album / plugin are copied onto the row and it depends
 * on nothing else. That also keeps this feature off the shared library schema.
 *
 * @param timestamp epoch millis when the play was recorded (playback session end).
 * @param mediaId the track's opaque id string (`pluginId=…&sourceId=…`) — the "top songs" key.
 * @param title track title at time of play.
 * @param artist joined artist name(s); blank when unknown.
 * @param album album name; null when unknown.
 * @param pluginId source plugin id (e.g. "local", "testsource").
 * @param durationMs full track duration in ms (0 if unknown).
 * @param listenedMs actual time the track was playing (excludes pauses/buffering).
 * @param artworkUrl representative artwork url, if any.
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["timestamp"])],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val pluginId: String,
    val durationMs: Long,
    val listenedMs: Long,
    val artworkUrl: String?,
)
