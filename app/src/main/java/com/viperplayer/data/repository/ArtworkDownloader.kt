package com.viperplayer.data.repository

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.viperplayer.domain.model.MediaId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for downloading and caching artwork images.
 */
@Singleton
class ArtworkDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // Stable, collision-free cache key. sourceId.hashCode() (32-bit) collided -> wrong image served.
    private fun cacheFilename(mediaId: MediaId): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(mediaId.encode().toByteArray())
        return "artwork_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ".jpg"
    }

    /**
     * Downloads artwork from URL and saves it to local cache.
     * @param artworkUrl The URL of the artwork to download
     * @param mediaId The media ID to use for generating a unique filename
     * @return The local file path if successful, null otherwise
     */
    suspend fun downloadArtwork(artworkUrl: String, mediaId: MediaId): String? {
        if (artworkUrl.isBlank()) return null

        return try {
            withContext(Dispatchers.IO) {
                // Create artwork cache directory
                val artworkCacheDir = File(context.filesDir, "artwork")
                if (!artworkCacheDir.exists()) {
                    artworkCacheDir.mkdirs()
                }

                // Generate unique filename based on mediaId
                val filename = cacheFilename(mediaId)
                val artworkFile = artworkCacheDir.resolve(filename)

                // Skip download if file already exists
                if (artworkFile.exists()) {
                    Timber.d("Artwork already cached: ${artworkFile.absolutePath}")
                    return@withContext artworkFile.absolutePath
                }

                // Download artwork using Coil
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()

                val result = context.imageLoader.execute(request)
                val bitmap = result.image?.toBitmap() ?: run {
                    Timber.e("Failed to decode bitmap from $artworkUrl")
                    return@withContext null
                }

                // Save bitmap to file
                artworkFile.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                }

                Timber.d("Artwork downloaded and cached: ${artworkFile.absolutePath}")
                artworkFile.absolutePath
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download artwork from $artworkUrl")
            null
        }
    }

    /**
     * Gets the local artwork path if it exists, otherwise returns null.
     */
    fun getLocalArtworkPath(mediaId: MediaId): String? {
        val artworkCacheDir = File(context.filesDir, "artwork")
        val filename = cacheFilename(mediaId)
        val artworkFile = artworkCacheDir.resolve(filename)

        return if (artworkFile.exists()) {
            artworkFile.absolutePath
        } else {
            null
        }
    }

    /**
     * Deletes cached artwork for a given media ID.
     */
    suspend fun deleteArtwork(mediaId: MediaId) {
        withContext(Dispatchers.IO) {
            try {
                val artworkCacheDir = File(context.filesDir, "artwork")
                val filename = cacheFilename(mediaId)
                val artworkFile = artworkCacheDir.resolve(filename)

                if (artworkFile.exists()) {
                    artworkFile.delete()
                    Timber.d("Deleted cached artwork: ${artworkFile.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete artwork for ${mediaId}")
            }
        }
    }
}

