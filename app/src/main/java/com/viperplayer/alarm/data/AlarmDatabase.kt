package com.viperplayer.alarm.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for the alarm feature. Deliberately separate from the app's shared
 * [com.viperplayer.data.local.ViperPlayerDatabase] so the alarm feature stays self-contained and
 * doesn't force a version bump / destructive wipe of the main library DB.
 */
@Database(entities = [AlarmEntity::class], version = 1, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
