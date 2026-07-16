package com.viperplayer.domain.repository

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SongWithStats
import com.viperplayer.domain.model.StatPeriod
import com.viperplayer.domain.model.StatsSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing media library data (songs, albums, artists, playlists)
 * with real-time updates via Room database.
 */
interface MediaLibraryRepository {

    // Artists
    fun getArtist(mediaId: MediaId): Flow<Artist?>
    fun getAllLikedArtists(): Flow<List<Artist>>
    fun getAllSavedArtists(): Flow<List<Artist>>
    suspend fun saveArtist(artist: ArtistDetail)
    suspend fun setArtistLiked(mediaId: MediaId, isLiked: Boolean)
    suspend fun setArtistSaved(mediaId: MediaId, isSaved: Boolean)

    // Albums
    fun getAlbum(mediaId: MediaId): Flow<Album?>
    fun getAllLikedAlbums(): Flow<List<Album>>
    fun getAllSavedAlbums(): Flow<List<Album>>
    fun getAllDownloadedAlbums(): Flow<List<Album>>
    suspend fun saveAlbum(album: Album)
    suspend fun setAlbumLiked(mediaId: MediaId, isLiked: Boolean)
    suspend fun setAlbumSaved(mediaId: MediaId, isSaved: Boolean)
    suspend fun setAlbumDownloaded(mediaId: MediaId, isDownloaded: Boolean)

    // Songs
    fun getSong(mediaId: MediaId): Flow<Song?>
    fun getAllLikedSongs(): Flow<List<Song>>
    fun getAllSavedSongs(): Flow<List<Song>>

    /**
     * Saved library songs ordered newest-added first (by insertion order). Source for the
     * "Recently Added" auto-playlist. Scoped to local library songs — there is no cross-plugin
     * "date added" for remote tracks.
     */
    fun getSongsByDateAdded(): Flow<List<Song>>
    fun getAllDownloadedSongs(): Flow<List<Song>>
    suspend fun saveSong(song: Song)
    suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean)
    suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean)
    suspend fun setSongDownloaded(
        mediaId: MediaId,
        isDownloaded: Boolean,
        downloadPath: String? = null
    )

    suspend fun incrementSongPlayCount(mediaId: MediaId)

    /**
     * Record the actual time a song was listened to (real play time, excluding pauses) for the most
     * recent play of [mediaId]. Called when the player reports a finished playback session.
     */
    suspend fun recordListenedTime(mediaId: MediaId, durationListenedMs: Long)

    // History & Stats (backed by per-play events)
    /** Songs ranked by play count within [period], with per-period play count and listening time. */
    fun getMostPlayedSongs(period: StatPeriod, limit: Int = 50): Flow<List<SongWithStats>>

    /** Aggregate totals (plays, distinct songs, listening time) for [period]. */
    fun getStatsSummary(period: StatPeriod): Flow<StatsSummary>

    /** Chronological listening history (newest first), one entry per play, capped to [limit]. */
    fun getHistory(limit: Int = 200): Flow<List<HistoryEntry>>

    /** Epoch millis of the first ever recorded play, or null if there is no history yet. */
    suspend fun getFirstPlayTimestamp(): Long?

    /** Delete the entire listening history. */
    suspend fun clearHistory()

    /** Delete play history older than [cutoffMillis] (epoch millis); enforces the retention window. */
    suspend fun pruneHistory(cutoffMillis: Long)

    // Playlists
    fun getPlaylist(mediaId: MediaId): Flow<Playlist?>
    fun getAllLikedPlaylists(): Flow<List<Playlist>>
    fun getAllSavedPlaylists(): Flow<List<Playlist>>
    fun getLikedSongsPlaylist(): Flow<Playlist>

    /**
     * User-created local playlists (the targets of "Add to playlist"), newest first, kept in sync
     * with the database. Does not include the virtual "Liked Songs" playlist.
     */
    fun getLocalPlaylists(): Flow<List<Playlist>>

    /**
     * Create a new, empty local playlist named [name] and return its [MediaId]. The name is trimmed;
     * a blank name is stored as a generic fallback.
     */
    suspend fun createLocalPlaylist(name: String): MediaId

    /**
     * Rename the user-created local playlist identified by [mediaId] to [newName] (trimmed). Only
     * local playlists (`pluginId == "local"`, excluding the virtual "Liked Songs" list) can be
     * renamed; any other [mediaId], or a blank [newName], is a no-op.
     */
    suspend fun renamePlaylist(mediaId: MediaId, newName: String)

    /**
     * Append [song] to the end of the playlist identified by [playlistId]. Persists the song first so
     * a not-yet-saved song (e.g. a plugin track from the player) still lands in the playlist. A song
     * already present in the playlist is left untouched (its position is preserved). No-op if the
     * playlist does not exist locally.
     */
    suspend fun addSongToPlaylist(playlistId: MediaId, song: Song)

    /**
     * Remove the song at [songId] from the playlist [playlistId] and compact the remaining positions
     * so they stay contiguous (0..n-1). No-op if the playlist or song is not found.
     */
    suspend fun removeSongFromPlaylist(playlistId: MediaId, songId: MediaId)

    /**
     * Move the song at [fromIndex] to [toIndex] within playlist [playlistId], rewriting the stored
     * positions to reflect the new order. Out-of-range indices or a no-op move leave the playlist
     * unchanged.
     */
    suspend fun reorderPlaylistSongs(playlistId: MediaId, fromIndex: Int, toIndex: Int)

    suspend fun savePlaylist(playlist: Playlist)
    suspend fun setPlaylistLiked(mediaId: MediaId, isLiked: Boolean)
    suspend fun setPlaylistSaved(mediaId: MediaId, isSaved: Boolean)
    suspend fun isPlaylistSaved(mediaId: MediaId): Boolean
    suspend fun setPlaylistDownloaded(mediaId: MediaId, isDownloaded: Boolean)

    // Local Files
    suspend fun scanLocalFiles()

    // Playlist import/export (M3U)
    /**
     * Serialize a saved playlist (looked up by [mediaId]) to an extended-M3U document, or null if
     * the playlist can't be found.
     */
    suspend fun exportPlaylistToM3u(mediaId: MediaId): String?

    /**
     * Parse an extended-M3U [content], resolve its entries into songs, and persist them as a new
     * local playlist named [playlistName]. Returns an [M3uImportResult] with counts.
     */
    suspend fun importPlaylistFromM3u(playlistName: String, content: String): M3uImportResult
}

/** Outcome of an M3U import: how many entries became songs and how many were skipped. */
data class M3uImportResult(
    val imported: Int,
    val skipped: Int,
)

