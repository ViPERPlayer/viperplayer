package com.viperplayer.data.lastfm

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The dedicated Last.fm offline-scrobble-queue Room database ("viperplayer_lastfm"), holding only
 * [PendingScrobbleEntity].
 *
 * Deliberately its own database (not part of the shared library DB) so the scrobbling feature owns its
 * schema end-to-end and never touches the library DB or its migrations — matching the stats / follows
 * / alarm features.
 */
@Database(
    entities = [PendingScrobbleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScrobbleQueueDatabase : RoomDatabase() {
    abstract fun pendingScrobbleDao(): PendingScrobbleDao
}
