package com.viperplayer.alarm.data

import com.viperplayer.alarm.domain.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao,
) : AlarmRepository {

    override val alarms: Flow<List<Alarm>> =
        alarmDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Alarm> =
        alarmDao.getAll().map { it.toDomain() }

    override suspend fun getEnabled(): List<Alarm> =
        alarmDao.getEnabled().map { it.toDomain() }

    override suspend fun getById(id: Long): Alarm? =
        alarmDao.getById(id)?.toDomain()

    override suspend fun upsert(alarm: Alarm): Long {
        val entity = AlarmEntity.fromDomain(alarm)
        return if (alarm.id == 0L) {
            alarmDao.insert(entity)
        } else {
            alarmDao.update(entity)
            alarm.id
        }
    }

    override suspend fun delete(alarm: Alarm) {
        alarmDao.delete(AlarmEntity.fromDomain(alarm))
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        alarmDao.setEnabled(id, enabled)
    }
}
