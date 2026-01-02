package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.AlbumDao
import com.viperplayer.data.local.dao.ArtistDao
import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.GenreDao
import com.viperplayer.data.local.dao.PlaylistDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.ArtistGenreCrossRef
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.mapper.EntityMapper.toDomain
import com.viperplayer.data.local.mapper.EntityMapper.toEntity
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation that syncs media data with Room database
 * and provides real-time updates via Flow.
 */
@Singleton
class MediaLibraryRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
    private val crossRefDao: CrossRefDao
) : MediaLibraryRepository {
    
    // Helper function to load artist with genres
    private suspend fun loadArtistWithGenres(artistId: Long): Artist? {
        val artistEntity = artistDao.getById(artistId).first() ?: return null
        val genreIds = crossRefDao.getGenreIdsForArtist(artistId)
        val genres = genreIds.mapNotNull { genreId ->
            genreDao.getById(genreId)?.name
        }
        return artistEntity.toDomain(genres)
    }
    
    // Helper function to save artist genres
    private suspend fun saveArtistGenres(artistId: Long, genres: List<String>) {
        genres.forEach { genreName ->
            // Get or create genre
            var genreId = genreDao.getIdByName(genreName)
            if (genreId == null) {
                val genreEntity = GenreEntity(name = genreName)
                genreId = genreDao.insert(genreEntity)
            }
            
            // Create cross-ref
            if (genreId != null) {
                crossRefDao.insertArtistGenre(
                    ArtistGenreCrossRef(
                        artistId = artistId,
                        genreId = genreId
                    )
                )
            }
        }
    }
    
    // Artists
    override fun getArtist(mediaId: MediaId): Flow<Artist?> {
        return combine(
            artistDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId),
            artistDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.id }
        ) { artistEntity, artistId ->
            if (artistEntity == null) return@combine null
            
            val genreIds = artistId?.let { crossRefDao.getGenreIdsForArtist(it) } ?: emptyList()
            val genres = genreIds.mapNotNull { genreId ->
                genreDao.getById(genreId)?.name
            }
            
            artistEntity.toDomain(genres)
        }
    }
    
    override fun getAllLikedArtists(): Flow<List<Artist>> {
        return artistDao.getAllLiked()
            .map { entities ->
                entities.map { entity ->
                    val genreIds = crossRefDao.getGenreIdsForArtist(entity.id)
                    val genres = genreIds.mapNotNull { genreId ->
                        genreDao.getById(genreId)?.name
                    }
                    entity.toDomain(genres)
                }
            }
    }
    
    override fun getAllSavedArtists(): Flow<List<Artist>> {
        return artistDao.getAllSaved()
            .map { entities ->
                entities.map { entity ->
                    val genreIds = crossRefDao.getGenreIdsForArtist(entity.id)
                    val genres = genreIds.mapNotNull { genreId ->
                        genreDao.getById(genreId)?.name
                    }
                    entity.toDomain(genres)
                }
            }
    }
    
    override suspend fun saveArtist(artist: Artist) {
        val entity = artist.toEntity()
        val artistId = artistDao.insert(entity)
        
        // Save genres and create cross-refs
        saveArtistGenres(artistId, artist.genres)
        
        // Update liked/saved status if needed
        if (artistDao.getByMediaId(artist.id.pluginId, artist.id.sourceId) != null) {
            artistDao.updateLiked(artist.id.pluginId, artist.id.sourceId, false)
            artistDao.updateSaved(artist.id.pluginId, artist.id.sourceId, false)
        }
    }
    
    override suspend fun setArtistLiked(mediaId: MediaId, isLiked: Boolean) {
        artistDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
    }
    
    override suspend fun setArtistSaved(mediaId: MediaId, isSaved: Boolean) {
        artistDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
    }
    
    // Albums
    override fun getAlbum(mediaId: MediaId): Flow<Album?> {
        return combine(
            albumDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId),
            albumDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.id }
        ) { albumEntity, albumId ->
            if (albumEntity == null) return@combine null
            
            val artistIds = albumId?.let { crossRefDao.getArtistIdsForAlbum(it) } ?: emptyList()
            val artists = artistIds.mapNotNull { artistId ->
                loadArtistWithGenres(artistId)
            }
            
            albumEntity.toDomain(artists)
        }
    }
    
    override fun getAllLikedAlbums(): Flow<List<Album>> {
        return albumDao.getAllLiked()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForAlbum(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    entity.toDomain(artists)
                }
            }
    }
    
    override fun getAllSavedAlbums(): Flow<List<Album>> {
        return albumDao.getAllSaved()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForAlbum(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    entity.toDomain(artists)
                }
            }
    }
    
    override fun getAllDownloadedAlbums(): Flow<List<Album>> {
        return albumDao.getAllDownloaded()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForAlbum(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    entity.toDomain(artists)
                }
            }
    }
    
    override suspend fun saveAlbum(album: Album) {
        val primaryArtist = album.artists.firstOrNull()
        val primaryArtistId = primaryArtist?.let {
            val artistEntity = it.toEntity()
            val artistId = artistDao.insert(artistEntity)
            saveArtistGenres(artistId, it.genres)
            artistId
        }
        
        val albumEntity = album.toEntity(primaryArtistId)
        val albumId = albumDao.insert(albumEntity)
        
        // Save artists and create cross-refs
        album.artists.forEachIndexed { index, artist ->
            val artistEntity = artist.toEntity()
            val artistId = artistDao.insert(artistEntity)
            saveArtistGenres(artistId, artist.genres)
            crossRefDao.insertAlbumArtist(
                com.viperplayer.data.local.entity.AlbumArtistCrossRef(
                    albumId = albumId,
                    artistId = artistId,
                    order = index
                )
            )
        }
    }
    
    override suspend fun setAlbumLiked(mediaId: MediaId, isLiked: Boolean) {
        albumDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
    }
    
    override suspend fun setAlbumSaved(mediaId: MediaId, isSaved: Boolean) {
        albumDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
    }
    
    override suspend fun setAlbumDownloaded(mediaId: MediaId, isDownloaded: Boolean) {
        albumDao.updateDownloaded(mediaId.pluginId, mediaId.sourceId, isDownloaded)
    }
    
    // Songs
    override fun getSong(mediaId: MediaId): Flow<Song?> {
        return combine(
            songDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId),
            songDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.id },
            songDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.albumId }
        ) { songEntity, songId, albumId ->
            if (songEntity == null) return@combine null
            
            val artistIds = songId?.let { crossRefDao.getArtistIdsForSong(it) } ?: emptyList()
            val artists = artistIds.mapNotNull { artistId ->
                loadArtistWithGenres(artistId)
            }
            
            val album = albumId?.let {
                albumDao.getById(it).first()?.let { albumEntity ->
                    val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                    val albumArtists = albumArtistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    albumEntity.toDomain(albumArtists)
                }
            }
            
            songEntity.toDomain(album, artists)
        }
    }
    
    override fun getAllLikedSongs(): Flow<List<Song>> {
        return songDao.getAllLiked()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForSong(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    val album = entity.albumId?.let {
                        albumDao.getById(it).first()?.let { albumEntity ->
                            val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                            val albumArtists = albumArtistIds.mapNotNull { artistId ->
                                loadArtistWithGenres(artistId)
                            }
                            albumEntity.toDomain(albumArtists)
                        }
                    }
                    entity.toDomain(album, artists)
                }
            }
    }
    
    override fun getAllSavedSongs(): Flow<List<Song>> {
        return songDao.getAllSaved()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForSong(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    val album = entity.albumId?.let {
                        albumDao.getById(it).first()?.let { albumEntity ->
                            val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                            val albumArtists = albumArtistIds.mapNotNull { artistId ->
                                loadArtistWithGenres(artistId)
                            }
                            albumEntity.toDomain(albumArtists)
                        }
                    }
                    entity.toDomain(album, artists)
                }
            }
    }
    
    override fun getAllDownloadedSongs(): Flow<List<Song>> {
        return songDao.getAllDownloaded()
            .map { entities ->
                entities.map { entity ->
                    val artistIds = crossRefDao.getArtistIdsForSong(entity.id)
                    val artists = artistIds.mapNotNull { artistId ->
                        loadArtistWithGenres(artistId)
                    }
                    val album = entity.albumId?.let {
                        albumDao.getById(it).first()?.let { albumEntity ->
                            val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                            val albumArtists = albumArtistIds.mapNotNull { artistId ->
                                loadArtistWithGenres(artistId)
                            }
                            albumEntity.toDomain(albumArtists)
                        }
                    }
                    entity.toDomain(album, artists)
                }
            }
    }
    
    override suspend fun saveSong(song: Song) {
        // Save album first if exists
        val albumId = song.album?.let { album ->
            val primaryArtist = album.artists.firstOrNull()
            val primaryArtistId = primaryArtist?.let {
                val artistEntity = it.toEntity()
                val artistId = artistDao.insert(artistEntity)
                saveArtistGenres(artistId, it.genres)
                artistId
            }
            val albumEntity = album.toEntity(primaryArtistId)
            albumDao.insert(albumEntity)
        }
        
        val songEntity = song.toEntity(albumId)
        val songId = songDao.insert(songEntity)
        
        // Save artists and create cross-refs
        song.artists.forEachIndexed { index, artist ->
            val artistEntity = artist.toEntity()
            val artistId = artistDao.insert(artistEntity)
            saveArtistGenres(artistId, artist.genres)
            crossRefDao.insertSongArtist(
                com.viperplayer.data.local.entity.SongArtistCrossRef(
                    songId = songId,
                    artistId = artistId,
                    order = index
                )
            )
        }
    }
    
    override suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean) {
        songDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
    }
    
    override suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean) {
        songDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
    }
    
    override suspend fun setSongDownloaded(mediaId: MediaId, isDownloaded: Boolean, downloadPath: String?) {
        songDao.updateDownloaded(mediaId.pluginId, mediaId.sourceId, isDownloaded, downloadPath)
    }
    
    override suspend fun incrementSongPlayCount(mediaId: MediaId) {
        songDao.incrementPlayCount(mediaId.pluginId, mediaId.sourceId)
    }
    
    // Playlists
    override fun getPlaylist(mediaId: MediaId): Flow<Playlist?> {
        return combine(
            playlistDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId),
            playlistDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.id }
        ) { playlistEntity, playlistId ->
            if (playlistEntity == null) return@combine null
            
            val songIds = playlistId?.let { crossRefDao.getSongIdsForPlaylist(it) } ?: emptyList()
            val songs = songIds.mapNotNull { songId ->
                val songEntity = songDao.getById(songId).first() ?: return@mapNotNull null
                val artistIds = crossRefDao.getArtistIdsForSong(songEntity.id)
                val artists = artistIds.mapNotNull { artistId ->
                    loadArtistWithGenres(artistId)
                }
                val album = songEntity.albumId?.let {
                    albumDao.getById(it).first()?.let { albumEntity ->
                        val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                        val albumArtists = albumArtistIds.mapNotNull { artistId ->
                            loadArtistWithGenres(artistId)
                        }
                        albumEntity.toDomain(albumArtists)
                    }
                }
                songEntity.toDomain(album, artists)
            }
            
            playlistEntity.toDomain(songs)
        }
    }
    
    override fun getAllLikedPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllLiked()
            .map { entities ->
                entities.map { entity ->
                    val songIds = crossRefDao.getSongIdsForPlaylist(entity.id)
                    val songs = songIds.mapNotNull { songId ->
                        val songEntity = songDao.getById(songId).first() ?: return@mapNotNull null
                        val artistIds = crossRefDao.getArtistIdsForSong(songEntity.id)
                        val artists = artistIds.mapNotNull { artistId ->
                            loadArtistWithGenres(artistId)
                        }
                        val album = songEntity.albumId?.let {
                            albumDao.getById(it).first()?.let { albumEntity ->
                                val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                                val albumArtists = albumArtistIds.mapNotNull { artistId ->
                                    artistDao.getById(artistId).first()?.toDomain()
                                }
                                albumEntity.toDomain(albumArtists)
                            }
                        }
                        songEntity.toDomain(album, artists)
                    }
                    entity.toDomain(songs)
                }
            }
    }
    
    override fun getAllSavedPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllSaved()
            .map { entities ->
                entities.map { entity ->
                    val songIds = crossRefDao.getSongIdsForPlaylist(entity.id)
                    val songs = songIds.mapNotNull { songId ->
                        val songEntity = songDao.getById(songId).first() ?: return@mapNotNull null
                        val artistIds = crossRefDao.getArtistIdsForSong(songEntity.id)
                        val artists = artistIds.mapNotNull { artistId ->
                            loadArtistWithGenres(artistId)
                        }
                        val album = songEntity.albumId?.let {
                            albumDao.getById(it).first()?.let { albumEntity ->
                                val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                                val albumArtists = albumArtistIds.mapNotNull { artistId ->
                                    artistDao.getById(artistId).first()?.toDomain()
                                }
                                albumEntity.toDomain(albumArtists)
                            }
                        }
                        songEntity.toDomain(album, artists)
                    }
                    entity.toDomain(songs)
                }
            }
    }
    
    override suspend fun savePlaylist(playlist: Playlist) {
        val playlistEntity = playlist.toEntity()
        val playlistId = playlistDao.insert(playlistEntity)
        
        // Save songs and create cross-refs
        playlist.songs?.forEachIndexed { index, song ->
            saveSong(song)
            val songEntity = songDao.getByMediaId(song.id.pluginId, song.id.sourceId)
            songEntity?.let {
                crossRefDao.insertPlaylistSong(
                    com.viperplayer.data.local.entity.PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = it.id,
                        position = index
                    )
                )
            }
        }
    }
    
    override suspend fun setPlaylistLiked(mediaId: MediaId, isLiked: Boolean) {
        playlistDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
    }
    
    override suspend fun setPlaylistSaved(mediaId: MediaId, isSaved: Boolean) {
        playlistDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
    }
    
    override suspend fun setPlaylistDownloaded(mediaId: MediaId, isDownloaded: Boolean) {
        playlistDao.updateDownloaded(mediaId.pluginId, mediaId.sourceId, isDownloaded)
    }
}

