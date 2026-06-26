package com.viperplayer.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton ExoPlayer cache manager that provides a Cache instance
 * configured with the max cache size from SettingsRepository.
 */
@OptIn(UnstableApi::class)
@Singleton
class ExoPlayerCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    val maxCacheSize = runBlocking { settingsRepository.maxSongCacheSize.first() }

    val cache = SimpleCache(
        context.cacheDir.resolve("exoplayer_cache"),
        LeastRecentlyUsedCacheEvictor(maxCacheSize),
        StandaloneDatabaseProvider(context)
    )

    fun getCacheSize(): Long {
        return cache.cacheSpace
    }

    /** Remove all cached content from the live cache. Returns the number of bytes freed. */
    fun clearCache(): Long {
        val before = cache.cacheSpace
        cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
        return before - cache.cacheSpace
    }
}
