package com.viperplayer.local.model

import android.net.Uri

/**
 * Internal model representing a song from MediaStore.
 */
data class LocalSong(
    val contentUri: Uri,
    val id: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long,
    val albumName: String,
    val duration: Long,
    val trackNumber: Int,
    val year: Int?,
    val albumArtUri: Uri?
)
