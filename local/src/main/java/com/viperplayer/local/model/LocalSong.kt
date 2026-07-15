package com.viperplayer.local.model

import android.net.Uri

/**
 * Internal model representing a song from MediaStore.
 *
 * Tag-backed fields ([artistName], [albumName], [albumArtist], [genre], ...) are null when the file
 * carries no such tag — MediaStore's literal `<unknown>` placeholder is normalised to null at scan
 * time. Display fallbacks ("Unknown artist", file name as title, ...) are applied by the scanner /
 * mapper, never stored here.
 */
data class LocalSong(
    val contentUri: Uri,
    val id: Long,
    val title: String,
    val fileName: String?,
    val artistId: Long,
    val artistName: String?,
    val albumId: Long,
    val albumName: String?,
    val albumArtist: String?,
    val duration: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val mimeType: String?,
    val isCompilation: Boolean,
    val dateAdded: Long?,
    val dateModified: Long?,
    val albumArtUri: Uri?,
    val folderPath: String? = null,
    /** Absolute file-system path (MediaStore `DATA`), when known; used to find sidecar lyric files. */
    val filePath: String? = null,
)
