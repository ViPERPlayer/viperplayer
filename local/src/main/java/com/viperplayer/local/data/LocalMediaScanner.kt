package com.viperplayer.local.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.viperplayer.local.model.LocalAlbum
import com.viperplayer.local.model.LocalArtist
import com.viperplayer.local.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LocalMediaScanner"
private const val CACHE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

/**
 * Scans and caches local media files from MediaStore.
 */
class LocalMediaScanner(context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    private var cachedSongs: List<LocalSong>? = null
    private var cachedAlbums: List<LocalAlbum>? = null
    private var cachedArtists: List<LocalArtist>? = null
    private var lastScanTime: Long = 0

    /**
     * Scan media files from MediaStore.
     * Uses cache if available and not expired.
     */
    suspend fun scanMedia() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // Use cache if valid
        if (cachedSongs != null && (currentTime - lastScanTime) < CACHE_TIMEOUT_MS) {
            Log.d(TAG, "Using cached media data")
            return@withContext
        }

        Log.d(TAG, "Scanning media files from MediaStore")
        
        val songs = mutableListOf<LocalSong>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artistName = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val artistId = cursor.getLong(artistIdColumn)
                    val albumName = cursor.getString(albumColumn) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val track = cursor.getInt(trackColumn)
                    val year = cursor.getInt(yearColumn).takeIf { it > 0 }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    songs.add(
                        LocalSong(
                            contentUri = contentUri,
                            id = id,
                            title = title,
                            artistId = artistId,
                            artistName = artistName,
                            albumId = albumId,
                            albumName = albumName,
                            duration = duration,
                            trackNumber = track,
                            year = year,
                            albumArtUri = getAlbumArtUri(albumId)
                        )
                    )
                }
            }
            
            Log.d(TAG, "Scanned ${songs.size} songs")
            
            // Cache the results
            cachedSongs = songs
            lastScanTime = currentTime
            
            // Build album and artist aggregations
            buildAlbumsAndArtists()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media", e)
        }
    }

    private fun buildAlbumsAndArtists() {
        val songs = cachedSongs ?: return
        
        // Group songs by album
        val albumMap = songs.groupBy { it.albumId }
        cachedAlbums = albumMap.map { (albumId, albumSongs) ->
            val firstSong = albumSongs.first()
            LocalAlbum(
                id = albumId,
                name = firstSong.albumName,
                artistId = firstSong.artistId,
                artistName = firstSong.artistName,
                year = albumSongs.mapNotNull { it.year }.minOrNull(),
                artworkUri = firstSong.albumArtUri,
                songs = albumSongs.sortedBy { it.trackNumber }
            )
        }.sortedBy { it.name }

        // Group songs by artist
        val artistMap = songs.groupBy { it.artistId }
        cachedArtists = artistMap.map { (artistId, artistSongs) ->
            val artistName = artistSongs.first().artistName
            
            // Get albums for this artist
            val artistAlbums = cachedAlbums?.filter { it.artistId == artistId } ?: emptyList()
            
            LocalArtist(
                id = artistId,
                name = artistName,
                songs = artistSongs.sortedBy { it.title },
                albums = artistAlbums
            )
        }.sortedBy { it.name }
        
        Log.d(TAG, "Built ${cachedAlbums?.size} albums and ${cachedArtists?.size} artists")
    }

    fun getAllSongs(): List<LocalSong> {
        return cachedSongs ?: emptyList()
    }

    fun getAllAlbums(): List<LocalAlbum> {
        return cachedAlbums ?: emptyList()
    }

    fun getAllArtists(): List<LocalArtist> {
        return cachedArtists ?: emptyList()
    }

    private fun getAlbumArtUri(albumId: Long): Uri? {
        val albumArtUri = "content://media/external/audio/albumart".toUri()
        return ContentUris.withAppendedId(albumArtUri, albumId)
    }

    /**
     * Clear cache to force a rescan on next access.
     */
    fun clearCache() {
        cachedSongs = null
        cachedAlbums = null
        cachedArtists = null
        lastScanTime = 0
    }
}
