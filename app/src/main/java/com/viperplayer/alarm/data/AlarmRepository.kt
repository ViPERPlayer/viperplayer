package com.viperplayer.alarm.data

import com.viperplayer.alarm.domain.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Repository over the alarm DAO. Exposes alarms as a [Flow] and mutates them, delegating the actual
 * OS scheduling to the [com.viperplayer.alarm.scheduling.AlarmScheduler] so callers (the ViewModel)
 * never touch `AlarmManager` directly.
 */
interface AlarmRepository {

    /** Live list of all alarms, ordered by time. */
    val alarms: Flow<List<Alarm>>

    suspend fun getAll(): List<Alarm>

    suspend fun getEnabled(): List<Alarm>

    suspend fun getById(id: Long): Alarm?

    /** Insert or update; returns the (possibly newly-assigned) row id. Does NOT schedule. */
    suspend fun upsert(alarm: Alarm): Long

    suspend fun delete(alarm: Alarm)

    suspend fun setEnabled(id: Long, enabled: Boolean)
}
