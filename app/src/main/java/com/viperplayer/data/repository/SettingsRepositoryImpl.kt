package com.viperplayer.data.repository

import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor() : SettingsRepository {
    override val cacheSize: Flow<Long>
        get() = flowOf(1000) // TODO

    override suspend fun setCacheSize(size: Long) {
        TODO("Not yet implemented")
    }
}