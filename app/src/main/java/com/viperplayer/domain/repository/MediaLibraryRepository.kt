package com.viperplayer.domain.repository

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
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
    suspend fun saveArtist(artist: Artist)
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

    // Playlists
    fun getPlaylist(mediaId: MediaId): Flow<Playlist?>
    fun getAllLikedPlaylists(): Flow<List<Playlist>>
    fun getAllSavedPlaylists(): Flow<List<Playlist>>
    fun getLikedSongsPlaylist(): Flow<Playlist>
    suspend fun savePlaylist(playlist: Playlist)
    suspend fun setPlaylistLiked(mediaId: MediaId, isLiked: Boolean)
    suspend fun setPlaylistSaved(mediaId: MediaId, isSaved: Boolean)
    suspend fun isPlaylistSaved(mediaId: MediaId): Boolean
    suspend fun setPlaylistDownloaded(mediaId: MediaId, isDownloaded: Boolean)

    // Local Files
    suspend fun scanLocalFiles()
}

