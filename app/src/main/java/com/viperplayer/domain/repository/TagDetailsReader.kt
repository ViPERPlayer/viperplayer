package com.viperplayer.domain.repository

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.TagDetails

/**
 * Reads the full tag / metadata detail of a local audio file. Local-files only — [mediaId]'s
 * `sourceId` is the song's `content://` URI. Kept behind this interface so the Tag-details ViewModel
 * depends on an abstraction (and can be unit-tested with a fake), while the real implementation does
 * the `ContentResolver` / `MediaMetadataRetriever` / byte-parsing I/O.
 */
interface TagDetailsReader {
    /**
     * Read tags + technical properties for the local song identified by [mediaId], off the caller's
     * thread. Returns null when the id is not a local song, the file can't be opened, or it carries no
     * readable metadata at all. Implementations must not block the main thread.
     */
    suspend fun read(mediaId: MediaId): TagDetails?
}
