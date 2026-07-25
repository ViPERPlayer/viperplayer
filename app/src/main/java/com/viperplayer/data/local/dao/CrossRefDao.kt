package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.viperplayer.data.local.entity.AlbumArtistCrossRef
import com.viperplayer.data.local.entity.ArtistGenreCrossRef
import com.viperplayer.data.local.entity.PlaylistSongCrossRef
import com.viperplayer.data.local.entity.QueueSongCrossRef
import com.viperplayer.data.local.entity.SongArtistCrossRef
import com.viperplayer.data.local.entity.SongGenreCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cross-reference table operations.
 */
@Dao
interface CrossRefDao {

    // Song-Artist relationships
    @Query("SELECT artistId FROM song_artists WHERE songId = :songId ORDER BY `order` ASC")
    suspend fun getArtistIdsForSong(songId: Long): List<Long>

    @Query("SELECT songId FROM song_artists WHERE artistId = :artistId ORDER BY `order` ASC")
    suspend fun getSongIdsForArtist(artistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongArtist(crossRef: SongArtistCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongArtists(crossRefs: List<SongArtistCrossRef>)

    @Query("DELETE FROM song_artists WHERE songId = :songId")
    suspend fun deleteSongArtists(songId: Long)

    @Query("DELETE FROM song_artists WHERE artistId = :artistId")
    suspend fun deleteArtistSongs(artistId: Long)

    // Album-Artist relationships
    @Query("SELECT artistId FROM album_artists WHERE albumId = :albumId ORDER BY `order` ASC")
    suspend fun getArtistIdsForAlbum(albumId: Long): List<Long>

    @Query("SELECT albumId FROM album_artists WHERE artistId = :artistId ORDER BY `order` ASC")
    suspend fun getAlbumIdsForArtist(artistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumArtist(crossRef: AlbumArtistCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumArtists(crossRefs: List<AlbumArtistCrossRef>)

    @Query("DELETE FROM album_artists WHERE albumId = :albumId")
    suspend fun deleteAlbumArtists(albumId: Long)

    @Query("DELETE FROM album_artists WHERE artistId = :artistId")
    suspend fun deleteArtistAlbums(artistId: Long)

    // Playlist-Song relationships
    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long>

    /** Reactive song ids for a playlist; re-emits whenever playlist_songs membership/order changes. */
    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongIdsForPlaylistFlow(playlistId: Long): Flow<List<Long>>

    /**
     * Reactive song counts per playlist, keyed by playlistId. Re-emits whenever playlist_songs
     * membership changes so callers can keep counts fresh without polling.
     */
    @Query("SELECT playlistId, COUNT(*) AS songCount FROM playlist_songs GROUP BY playlistId")
    fun getPlaylistSongCounts(): Flow<List<PlaylistSongCount>>

    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId ORDER BY position ASC")
    suspend fun getPlaylistIdsForSong(songId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun deleteSongPlaylists(songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    /** Highest position currently used in a playlist, or null when the playlist has no songs. */
    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxPositionForPlaylist(playlistId: Long): Int?

    /** Whether a specific song is already in a playlist (used to avoid duplicate inserts). */
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun countSongInPlaylist(playlistId: Long, songId: Long): Int

    // Artist-Genre relationships
    @Query("SELECT genreId FROM artist_genres WHERE artistId = :artistId")
    suspend fun getGenreIdsForArtist(artistId: Long): List<Long>

    @Query("SELECT artistId FROM artist_genres WHERE genreId = :genreId")
    suspend fun getArtistIdsForGenre(genreId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistGenre(crossRef: ArtistGenreCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistGenres(crossRefs: List<ArtistGenreCrossRef>)

    @Query("DELETE FROM artist_genres WHERE artistId = :artistId")
    suspend fun deleteArtistGenres(artistId: Long)

    @Query("DELETE FROM artist_genres WHERE genreId = :genreId")
    suspend fun deleteGenreArtists(genreId: Long)

    // Song-Genre relationships
    @Query("SELECT genreId FROM song_genres WHERE songId = :songId")
    suspend fun getGenreIdsForSong(songId: Long): List<Long>

    @Query("SELECT songId FROM song_genres WHERE genreId = :genreId")
    suspend fun getSongIdsForGenre(genreId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongGenre(crossRef: SongGenreCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongGenres(crossRefs: List<SongGenreCrossRef>)

    @Query("DELETE FROM song_genres WHERE songId = :songId")
    suspend fun deleteSongGenres(songId: Long)

    // Queue-Song relationships
    @Query("SELECT songId FROM queue_songs ORDER BY position ASC")
    suspend fun getSongIdsForQueue(): List<Long>

    @Query("SELECT position FROM queue_songs WHERE songId = :songId LIMIT 1")
    suspend fun getQueuePositionForSong(songId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSong(crossRef: QueueSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSongs(crossRefs: List<QueueSongCrossRef>)

    @Query("DELETE FROM queue_songs")
    suspend fun clearQueue()

    @Query("DELETE FROM queue_songs WHERE songId = :songId")
    suspend fun removeSongFromQueue(songId: Long)
}

/** Projection of the number of songs in a single playlist, keyed by [playlistId]. */
data class PlaylistSongCount(
    val playlistId: Long,
    val songCount: Int,
)

