package com.viperplayer.local.data.cover

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/**
 * Android-layer helper that resolves the best folder-based cover image for an album directory.
 *
 * It lists image files in a folder, cheaply reads each one's pixel dimensions with a bounds-only
 * [BitmapFactory] decode (no pixels loaded into memory), builds pure [CoverCandidate] descriptors,
 * and delegates the actual choice to [AlbumCoverScorer]. All Android/bitmap work lives here so the
 * scoring heuristic stays a pure, unit-testable function.
 *
 * Results are memoised per folder path for the lifetime of this resolver, since a single scan
 * touches every song in a folder and they all share the same directory.
 */
class FolderCoverResolver {

    private val cache = HashMap<String, Uri?>()

    /**
     * Returns a `file://` [Uri] for the best cover image in [folderPath], or null when the folder
     * is missing/unreadable or contains no usable image. Repeated calls for the same folder are
     * served from an in-memory cache.
     */
    fun resolve(folderPath: String?): Uri? {
        if (folderPath.isNullOrEmpty()) return null
        cache[folderPath]?.let { return it }
        if (cache.containsKey(folderPath)) return null // cached miss

        val result = resolveUncached(folderPath)
        cache[folderPath] = result
        return result
    }

    private fun resolveUncached(folderPath: String): Uri? {
        val dir = File(folderPath)
        val files = dir.takeIf { it.isDirectory }?.listFiles() ?: return null

        val candidates = files.mapNotNull { file ->
            if (!file.isFile) return@mapNotNull null
            if (!AlbumCoverScorer.isSupportedImage(file.name)) return@mapNotNull null
            val (width, height) = readImageBounds(file)
            CoverCandidate(
                fileName = file.name,
                width = width,
                height = height,
                fileSizeBytes = file.length().takeIf { it > 0 },
            ) to file
        }

        if (candidates.isEmpty()) return null

        val best = AlbumCoverScorer.pickBest(candidates.map { it.first }) ?: return null
        val bestFile = candidates.first { it.first === best }.second
        return bestFile.toUri()
    }

    /**
     * Reads an image's pixel dimensions without decoding its pixels (bounds-only). Returns
     * (null, null) when the file can't be decoded as an image.
     */
    private fun readImageBounds(file: File): Pair<Int?, Int?> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val w = options.outWidth.takeIf { it > 0 }
            val h = options.outHeight.takeIf { it > 0 }
            w to h
        } catch (_: Exception) {
            null to null
        }
    }

    /** Drops all memoised folder results. */
    fun clear() = cache.clear()
}
