package com.viperplayer.domain.repository

/**
 * Repository for cache management operations.
 * This handles clearing caches and getting cache sizes, which are not settings.
 */
interface CacheRepository {
    /**
     * Gets the total size of downloaded songs in bytes.
     */
    suspend fun getDownloadedSongsSize(): Long
    
    /**
     * Clears all downloaded songs and returns the number of bytes cleared.
     */
    suspend fun clearAllDownloads(): Long
    
    /**
     * Clears the song cache (ExoPlayer cache, DASH manifests, etc.) and returns the number of bytes cleared.
     */
    suspend fun clearSongCache(): Long
    
    /**
     * Clears the image cache (artwork) and returns the number of bytes cleared.
     */
    suspend fun clearImageCache(): Long
    
    /**
     * Gets the current song cache size in bytes.
     */
    suspend fun getSongCacheSize(): Long
    
    /**
     * Gets the current image cache size in bytes.
     */
    suspend fun getImageCacheSize(): Long
}

