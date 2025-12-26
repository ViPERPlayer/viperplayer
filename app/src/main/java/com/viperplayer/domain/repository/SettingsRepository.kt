package com.viperplayer.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val cacheSize: Flow<Long>

    suspend fun setCacheSize(size: Long)

}

