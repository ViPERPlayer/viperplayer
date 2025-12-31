package com.viperplayer.data.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ViperPlayerResolver @Inject constructor() : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        return runBlocking { suspendingResolveDataSpec(dataSpec) }
    }

    private suspend fun suspendingResolveDataSpec(dataSpec: DataSpec): DataSpec {
        return dataSpec
    }
}