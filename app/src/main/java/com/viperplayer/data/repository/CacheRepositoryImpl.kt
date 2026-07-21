package com.viperplayer.data.repository

import android.content.Context
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.player.ExoPlayerCache
import com.viperplayer.domain.repository.CacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val exoPlayerCache: ExoPlayerCache
) : CacheRepository {

    override suspend fun getDownloadedSongsSize(): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        try {
            songDao.getAllDownloaded().first().forEach { song ->
                song.downloadPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        totalSize += file.length()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate downloaded songs size")
        }
        totalSize
    }

    override suspend fun clearAllDownloads(): Long = withContext(Dispatchers.IO) {
        var totalBytesCleared = 0L
        try {
            val downloadedSongs = songDao.getAllDownloaded().first()
            downloadedSongs.forEach { song ->
                song.downloadPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        totalBytesCleared += file.length()
                        file.delete()
                    }
                }
                songDao.updateDownloaded(song.idType, song.pluginId, song.sourceId, false, null)
            }
            Timber.d("Cleared downloads: ${totalBytesCleared / (1024 * 1024)} MB")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear downloads")
        }
        totalBytesCleared
    }

    override suspend fun clearSongCache(): Long = withContext(Dispatchers.IO) {
        var totalBytesCleared = 0L
        try {
            // Clear ExoPlayer cache
            totalBytesCleared += exoPlayerCache.clearCache()

            // Clear DASH manifests
            val dashDir = File(context.cacheDir, "dash_manifests")
            if (dashDir.exists()) {
                totalBytesCleared += deleteDirectory(dashDir)
            }

            // Clear any other song cache files
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != "artwork" && file.name != "dash_manifests" && file.name != "exoplayer_cache") {
                    totalBytesCleared += deleteDirectory(file)
                } else if (file.isFile && !file.name.startsWith("artwork")) {
                    totalBytesCleared += file.length()
                    file.delete()
                }
            }

            Timber.d("Cleared song cache: ${totalBytesCleared / (1024 * 1024)} MB")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear song cache")
        }
        totalBytesCleared
    }

    override suspend fun clearImageCache(): Long = withContext(Dispatchers.IO) {
        var totalBytesCleared = 0L
        try {
            // Clear artwork cache in filesDir
            val artworkDir = File(context.filesDir, "artwork")
            if (artworkDir.exists()) {
                totalBytesCleared += deleteDirectory(artworkDir)
            }

            // Clear artwork cache in cacheDir
            val cacheArtworkDir = File(context.cacheDir, "artwork")
            if (cacheArtworkDir.exists()) {
                totalBytesCleared += deleteDirectory(cacheArtworkDir)
            }

            Timber.d("Cleared image cache: ${totalBytesCleared / (1024 * 1024)} MB")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear image cache")
        }
        totalBytesCleared
    }

    override suspend fun getSongCacheSize(): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        try {
            // Get ExoPlayer cache size
            totalSize += exoPlayerCache.getCacheSize()

            // Get DASH manifests size
            val dashDir = File(context.cacheDir, "dash_manifests")
            if (dashDir.exists()) {
                totalSize += getDirectorySize(dashDir)
            }

            // Get other song cache files size
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != "artwork" && file.name != "dash_manifests" && file.name != "exoplayer_cache") {
                    totalSize += getDirectorySize(file)
                } else if (file.isFile && !file.name.startsWith("artwork")) {
                    totalSize += file.length()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate song cache size")
        }
        totalSize
    }

    override suspend fun getImageCacheSize(): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        try {
            // Get artwork cache size in filesDir
            val artworkDir = File(context.filesDir, "artwork")
            if (artworkDir.exists()) {
                totalSize += getDirectorySize(artworkDir)
            }

            // Get artwork cache size in cacheDir
            val cacheArtworkDir = File(context.cacheDir, "artwork")
            if (cacheArtworkDir.exists()) {
                totalSize += getDirectorySize(cacheArtworkDir)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate image cache size")
        }
        totalSize
    }

    private fun deleteDirectory(directory: File): Long {
        var totalSize = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                totalSize += if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    val size = file.length()
                    file.delete()
                    size
                }
            }
            directory.delete()
        }
        return totalSize
    }

    private fun getDirectorySize(directory: File): Long {
        var totalSize = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                totalSize += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return totalSize
    }
}

