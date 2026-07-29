package com.viperplayer.data.rec

import android.content.Context
import androidx.core.net.toUri
import com.viperplayer.data.local.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves + decodes a [SongEntity]'s local audio to PCM. Abstracted behind an interface so the
 * [LibraryEmbedder] pipeline can be unit-tested with a fake (the production [SongAudioSourceResolver]
 * touches the filesystem / `ContentResolver`, which need a device/Robolectric).
 */
fun interface SongAudioSource {
    /** Decode [song]'s local audio head via [decoder], or null when unavailable/undecodable. */
    fun decode(song: SongEntity, decoder: AudioDecoder): DecodedAudio?
}

/**
 * Turns a [SongEntity] with local bytes (see [SongEmbedCandidates.hasLocalBytes]) into DECODED PCM by
 * opening the right on-disk source and handing it to an [AudioDecoder]. This is the one place that
 * knows the two shapes of local audio in the app:
 *
 *  - **Downloaded** tracks: [SongEntity.downloadPath] is an absolute file path under the app's
 *    `filesDir/downloads` (written by `DownloadManager`) — decoded via [AudioDecoder.decode] by path.
 *  - **Local-library** tracks (`idType == "local"`): the `sourceId` is a MediaStore `content://` URI
 *    string (`LocalMediaDataSource` builds it with `ContentUris.withAppendedId`). Opened through the
 *    `ContentResolver` to a `FileDescriptor` and decoded via the FD overload.
 *
 * Android-coupled (needs a `Context` for the `ContentResolver`), so it lives in the data layer and is
 * injected into the worker; the pure "which songs / what order" logic stays in [SongEmbedCandidates].
 */
@Singleton
class SongAudioSourceResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SongAudioSource {

    /**
     * Decodes the head of [song]'s local audio to PCM, or null when it has no usable local source or
     * decoding fails (the caller skips that song). A downloaded file whose path no longer exists on
     * disk (deleted out from under us) returns null.
     */
    override fun decode(song: SongEntity, decoder: AudioDecoder): DecodedAudio? {
        // Prefer a concrete downloaded file when present — it's the cheapest, most reliable source.
        val downloadPath = song.downloadPath
        if (song.isDownloaded && !downloadPath.isNullOrBlank()) {
            val file = File(downloadPath)
            if (file.isFile) {
                return decoder.decode(file.absolutePath)
            }
            Timber.w("SongAudioSource: downloadPath missing on disk for songId=${song.id}")
            // Fall through: a local-library song might still be openable via its content URI.
        }

        if (song.idType == SongEmbedCandidates.LOCAL_ID_TYPE) {
            return decodeContentUri(song, decoder)
        }
        return null
    }

    /** Opens the local-library [song]'s `content://` (or `file://`) source through the resolver. */
    private fun decodeContentUri(song: SongEntity, decoder: AudioDecoder): DecodedAudio? = try {
        val uri = song.sourceId.toUri()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            decoder.decode(pfd.fileDescriptor)
        }
    } catch (e: Exception) {
        Timber.w(e, "SongAudioSource: could not open local uri for songId=${song.id}")
        null
    }
}
