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
// v2: no schema change — bumped to force the destructive reset (fallbackToDestructiveMigration) so
// play_history rows written with the pre-#12 mediaId string format (pluginId=…&sourceId=…) are wiped
// rather than lingering and failing to join against the new MediaId.encode() key (t=plugin&p=…&s=…).
@Database(
    entities = [PlayHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
}
