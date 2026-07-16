package com.viperplayer.data.stats

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The dedicated **stats** Room database ("viperplayer_stats"), holding only [PlayHistoryEntity].
 *
 * Deliberately its own database (not part of [com.viperplayer.data.local.ViperPlayerDatabase]) so the
 * listening-stats feature owns its schema end-to-end and never touches the shared library DB / its
 * migrations.
 */
@Database(
    entities = [PlayHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
}
