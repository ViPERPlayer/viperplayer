package com.viperplayer.data.source

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.TagDetails
import com.viperplayer.domain.repository.TagDetailsReader
import com.viperplayer.local.data.AudioTags
import com.viperplayer.local.data.LocalTagReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TagDetailsReader] for local files. Given a local song's `content://` URI it:
 *  - byte-parses the full text-tag set from the file head via the pure [LocalTagReader.readTags]
 *    (shared with the embedded-lyrics reader), and
 *  - reads technical/audio properties (container, MIME, size, duration, bitrate, sample rate,
 *    channels, bits-per-sample, embedded-cover size) from [MediaMetadataRetriever] plus a MediaStore
 *    query for the display name / path / size.
 *
 * All I/O runs on [Dispatchers.IO]; every step is best-effort and degrades to null rather than
 * throwing, so a file that can't be probed still yields whatever partial detail was readable.
 */
@Singleton
class AndroidTagDetailsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : TagDetailsReader {

    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun read(mediaId: MediaId): TagDetails? = withContext(Dispatchers.IO) {
        if (mediaId !is MediaId.Local) return@withContext null
        val uri = runCatching { mediaId.sourceId.toUri() }.getOrNull() ?: return@withContext null

        val tags = readTextTags(uri)
        val fileInfo = readFileInfo(uri)
        val technical = readTechnical(uri)

        val details = TagDetails(
            title = tags.title,
            artists = tags.artists,
            album = tags.album,
            albumArtist = tags.albumArtist,
            composer = tags.composer,
            genre = tags.genre,
            date = tags.date,
            trackNumber = tags.track?.number,
            trackTotal = tags.track?.total,
            discNumber = tags.disc?.number,
            discTotal = tags.disc?.total,
            comment = tags.comment,
            encoder = tags.encoder,
            fileName = fileInfo.displayName,
            filePath = fileInfo.path,
            container = technical.container ?: fileInfo.displayName?.let(::containerFromName),
            mimeType = technical.mimeType ?: fileInfo.mimeType,
            fileSizeBytes = fileInfo.sizeBytes,
            durationMs = technical.durationMs,
            bitrate = technical.bitrate,
            sampleRate = technical.sampleRate,
            channelCount = null,
            bitsPerSample = technical.bitsPerSample,
            coverArtBytes = technical.coverArtBytes,
        )
        details.takeIf { it.hasAny() }
    }

    /** Byte-parse the head of the file for its full text-tag set; empty on any error. */
    private fun readTextTags(uri: Uri): AudioTags = runCatching {
        contentResolver.openInputStream(uri)?.use { LocalTagReader.readTags(it) }
    }.getOrNull() ?: AudioTags.EMPTY

    /** Query MediaStore for the display name / absolute path / size of [uri]. */
    private fun readFileInfo(uri: Uri): FileInfo = runCatching {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use FileInfo()
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            FileInfo(
                displayName = nameIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }?.trim()?.ifEmpty { null },
                path = dataIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }?.trim()?.ifEmpty { null },
                sizeBytes = sizeIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getLong(it) }?.takeIf { it > 0 },
                mimeType = mimeIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }?.trim()?.ifEmpty { null },
            )
        }
    }.getOrNull() ?: FileInfo()

    /** Pull audio properties out of [MediaMetadataRetriever]; every field best-effort. */
    private fun readTechnical(uri: Uri): Technical {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val mime = retriever.extract(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            Technical(
                container = mime?.let(::containerFromMime),
                mimeType = mime,
                durationMs = retriever.extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                bitrate = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                // SAMPLERATE / BITS_PER_SAMPLE are public keys since API 31; on older devices they
                // simply return null. Channel count has no retriever key, so it is left to the
                // runtime AudioFormat surfaced elsewhere (Song info's Audio format section).
                sampleRate = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE),
                bitsPerSample = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE),
                coverArtBytes = runCatching { retriever.embeddedPicture?.size }.getOrNull()?.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Timber.d(e, "MediaMetadataRetriever could not read %s", uri)
            Technical()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.extract(key: Int): String? =
        runCatching { extractMetadata(key) }.getOrNull()?.trim()?.ifEmpty { null }

    private fun MediaMetadataRetriever.extractInt(key: Int): Int? =
        extract(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.extractLong(key: Int): Long? =
        extract(key)?.toLongOrNull()?.takeIf { it > 0 }

    /** Friendly container name from a file extension (e.g. "01.flac" -> "FLAC"). */
    private fun containerFromName(name: String): String? =
        name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.uppercase()

    /** Friendly container name from a MIME type (e.g. "audio/flac" -> "FLAC"). */
    private fun containerFromMime(mime: String): String? = when {
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "MP3"
        mime.contains("ogg", ignoreCase = true) -> "OGG"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("aac", ignoreCase = true) || mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "AAC"
        mime.contains("wav", ignoreCase = true) -> "WAV"
        mime.contains("x-matroska", ignoreCase = true) -> "MKA"
        else -> mime.substringAfter('/', "").takeIf { it.isNotEmpty() }?.uppercase()
    }

    private data class FileInfo(
        val displayName: String? = null,
        val path: String? = null,
        val sizeBytes: Long? = null,
        val mimeType: String? = null,
    )

    private data class Technical(
        val container: String? = null,
        val mimeType: String? = null,
        val durationMs: Long? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val bitsPerSample: Int? = null,
        val coverArtBytes: Int? = null,
    )
}
