package com.viperplayer.local.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
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
 *
 * Metadata extraction follows a reference app's libphonograph reader
 * (https://github.com/FoedusProgramme/a reference app): read every useful column in a single pass,
 * normalise MediaStore's literal `<unknown>` placeholders to null, fall back to the file name for
 * untagged titles and track numbers, decode disc-in-track encodings (1002 = disc 1, track 2), and
 * infer each album's artist from its songs when the ALBUM_ARTIST tag is missing.
 */
class LocalMediaScanner(
    context: Context,
    private val settings: LocalScanSettings = LocalScanSettings(context),
) {
    private val appContext: Context = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    private var cachedSongs: List<LocalSong>? = null
    private var cachedAlbums: List<LocalAlbum>? = null
    private var cachedArtists: List<LocalArtist>? = null
    private var lastScanTime: Long = 0

    /** Whether the runtime permission needed to read MediaStore audio is currently granted. */
    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Scan media files from MediaStore.
     * Uses cache if available and not expired. Skips (without caching) when the audio permission
     * is missing, so the very next call after a grant sees the real library.
     */
    suspend fun scanMedia() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()

        // Use cache if valid
        if (cachedSongs != null && (currentTime - lastScanTime) < CACHE_TIMEOUT_MS) {
            Log.d(TAG, "Using cached media data")
            return@withContext
        }

        if (!hasAudioPermission()) {
            Log.w(TAG, "Audio permission not granted; skipping scan")
            return@withContext
        }

        Log.d(TAG, "Scanning media files from MediaStore")

        val songs = mutableListOf<LocalSong>()

        // Read filter settings once for this scan pass.
        val blacklist = settings.blacklistedFolders
        val whitelist = settings.whitelistedFolders
        val minDurationMs = settings.minTrackLengthSeconds.toLong() * 1000L

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Columns only queryable on API 30+.
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                add(MediaStore.Audio.Media.DISC_NUMBER)
                add(MediaStore.Audio.Media.COMPILATION)
            }
        }.toTypedArray()

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
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                @Suppress("DEPRECATION")
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                // API 30+ columns; -1 when absent from the projection.
                val albumArtistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val cdTrackNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                val discNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                val compilationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.COMPILATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val fileName = cursor.getString(displayNameColumn)

                    // Issue #3: use the title tag when present, otherwise fall back to the file
                    // name (without its extension) as the song name.
                    val rawTitle = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() }
                    val title = rawTitle
                        ?: fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: fileName
                        ?: "Unknown"

                    val artistName = cursor.getString(artistColumn).takeIfRealTag()
                    val artistId = cursor.getLong(artistIdColumn)
                    val albumName = cursor.getString(albumColumn).takeIfRealTag()
                    val albumId = cursor.getLong(albumIdColumn)
                    val albumArtist = cursor.getStringOrNull(albumArtistColumn).takeIfRealTag()
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val duration = cursor.getLong(durationColumn).takeIf { it > 0 }
                    val year = cursor.getInt(yearColumn).takeIf { it > 0 }
                    val genre = cursor.getStringOrNull(genreColumn).takeIfRealTag()
                    val isCompilation = cursor.getStringOrNull(compilationColumn) != null
                    val dateAdded = cursor.getLong(dateAddedColumn).takeIf { it > 0 }
                    val dateModified = cursor.getLong(dateModifiedColumn).takeIf { it > 0 }

                    var trackNumber = cursor.getInt(trackColumn).takeIf { it > 0 }
                    var discNumber = if (discNumberColumn >= 0) {
                        cursor.getInt(discNumberColumn).takeIf { it > 0 }
                    } else null

                    // Untagged track number: try the CD track tag, then a "04. Song.flac"-style
                    // file name (only when the number isn't just the start of the actual title).
                    if (trackNumber == null && cdTrackNumberColumn >= 0) {
                        trackNumber = cursor.getString(cdTrackNumberColumn)?.toIntOrNull()?.takeIf { it > 0 }
                    }
                    if (trackNumber == null && fileName != null) {
                        val match = TRACK_NUMBER_REGEX.matchEntire(fileName)
                        if (match != null && (rawTitle == null || !rawTitle.startsWith(match.groupValues[1]))) {
                            trackNumber = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
                        }
                    }
                    // MediaStore encodes disc+track as e.g. 1002 (disc 1, track 2) even when the
                    // file's own tag doesn't.
                    if (trackNumber != null && trackNumber >= 1000 &&
                        (discNumber == null || discNumber == trackNumber / 1000)
                    ) {
                        discNumber = trackNumber / 1000
                        trackNumber %= 1000
                    }

                    val filePath = cursor.getStringOrNull(dataColumn)
                    val folderPath = filePath
                        ?.substringBeforeLast('/', "")
                        ?.takeIf { it.isNotEmpty() }

                    // Folder blacklist: drop songs in an excluded folder (or any subfolder of one).
                    if (folderPath != null && blacklist.any { folderPath.isUnder(it) }) {
                        continue
                    }
                    // Folder whitelist: when set, only keep songs under a whitelisted folder.
                    if (whitelist.isNotEmpty() &&
                        (folderPath == null || whitelist.none { folderPath.isUnder(it) })
                    ) {
                        continue
                    }
                    // Minimum track length: drop known-short tracks when the filter is enabled.
                    if (minDurationMs > 0 && duration != null && duration < minDurationMs) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    songs.add(
                        LocalSong(
                            contentUri = contentUri,
                            id = id,
                            title = title,
                            fileName = fileName,
                            artistId = artistId,
                            artistName = artistName,
                            albumId = albumId,
                            albumName = albumName,
                            albumArtist = albumArtist,
                            duration = duration,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            year = year,
                            genre = genre,
                            mimeType = mimeType,
                            isCompilation = isCompilation,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            albumArtUri = getAlbumArtUri(albumId),
                            folderPath = folderPath
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
        val albums = albumMap.map { (albumId, albumSongs) ->
            val sorted = albumSongs.sortedBy { ((it.discNumber ?: 0) * 1000) + (it.trackNumber ?: 0) }
            val (albumArtistName, albumArtistId) = findBestAlbumArtist(albumSongs)
            LocalAlbum(
                id = albumId,
                name = sorted.first().albumName,
                albumArtistId = albumArtistId,
                albumArtistName = albumArtistName,
                year = albumSongs.mapNotNull { it.year }.maxOrNull(),
                isCompilation = albumSongs.any { it.isCompilation },
                artworkUri = sorted.first().albumArtUri,
                songs = sorted
            )
        }.sortedBy { it.name?.lowercase() ?: "￿" }
        cachedAlbums = albums

        // Group songs by artist. An artist's albums are those they own (album artist) plus the
        // ones they appear on (e.g. compilations without a confident album artist).
        val artistMap = songs.groupBy { it.artistId }
        cachedArtists = artistMap.map { (artistId, artistSongs) ->
            LocalArtist(
                id = artistId,
                name = artistSongs.firstNotNullOfOrNull { it.artistName },
                songs = artistSongs.sortedBy { it.title.lowercase() },
                albums = albums.filter { album ->
                    album.albumArtistId == artistId ||
                        (album.albumArtistId == null && album.songs.any { it.artistId == artistId })
                }
            )
        }.sortedBy { it.name?.lowercase() ?: "￿" }

        Log.d(TAG, "Built ${cachedAlbums?.size} albums and ${cachedArtists?.size} artists")
    }

    /**
     * Chooses an album's artist the way a reference app does: prefer the ALBUM_ARTIST tag (as long as
     * tagged songs aren't outnumbered by untagged ones and the tags don't conflict), otherwise
     * fall back to the song artist covering at least 60% of the album's tracks. Returns
     * (null, null) when there's no confident answer.
     */
    private fun findBestAlbumArtist(songs: List<LocalSong>): Pair<String?, Long?> {
        val byAlbumArtist = songs.groupBy { it.albumArtist }.mapValues { it.value.size }
        val tagged = byAlbumArtist.keys.filterNotNull()
        if (tagged.size > 1) return null to null // conflicting tags; don't guess
        val theAlbumArtist = tagged.firstOrNull()
        if (theAlbumArtist != null) {
            if ((byAlbumArtist[theAlbumArtist] ?: 0) < (byAlbumArtist[null] ?: 0)) return null to null
            // If the album artist made some song on the album, its artist id is the best guess.
            return theAlbumArtist to songs.firstOrNull { it.artistName == theAlbumArtist }?.artistId
        }
        // No album artist tags at all: majority song artist wins if it covers >= 60% of the album.
        val best = songs.groupBy { it.artistName }.maxByOrNull { it.value.size }
        if (best?.key == null) return null to null
        if (best.value.size.toFloat() / songs.size < 0.6f) return null to null
        return best.key to best.value.first().artistId
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

    private fun Cursor.getStringOrNull(column: Int): String? =
        if (column >= 0 && !isNull(column)) getString(column) else null

    /** Normalises blank strings and MediaStore's `<unknown>` placeholder to null. */
    private fun String?.takeIfRealTag(): String? =
        this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }

    /** True when this folder path equals [ancestor] or is a descendant of it. */
    private fun String.isUnder(ancestor: String): Boolean {
        val a = ancestor.trimEnd('/')
        return this == a || startsWith("$a/")
    }

    companion object {
        /** The runtime permission needed to read audio from MediaStore on this Android version. */
        val requiredPermission: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        /** Matches file names like `04. La isla bonita.flac` to recover an untagged track number. */
        private val TRACK_NUMBER_REGEX = Regex("^([0-9]+)\\..*$")
    }
}
