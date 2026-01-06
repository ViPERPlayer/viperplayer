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
import com.viperplayer.domain.repository.PluginRepository
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
    private val crossRefDao: CrossRefDao,
    private val pluginRepository: PluginRepository,
    private val artworkDownloader: ArtworkDownloader,
    private val networkConnectivityChecker: NetworkConnectivityChecker
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
    
    // Helper function to compute isPlayable at runtime based on plugin connection, download status, and internet availability
    private fun computeIsPlayable(
        songEntity: com.viperplayer.data.local.entity.SongEntity,
        connectedPluginIds: Set<String>,
        requiresInternet: Boolean,
        isInternetAvailable: Boolean
    ): Boolean {
        val isPluginConnected = songEntity.pluginId in connectedPluginIds
        
        // If song is downloaded, it's always playable (offline)
        if (songEntity.isDownloaded) {
            return true
        }
        
        // If plugin is not connected, song is not playable
        if (!isPluginConnected) {
            return false
        }
        
        // If song requires internet but internet is not available, song is not playable
        if (requiresInternet && !isInternetAvailable) {
            return false
        }
        
        return true
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
            crossRefDao.insertArtistGenre(
                ArtistGenreCrossRef(
                    artistId = artistId,
                    genreId = genreId
                )
            )
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
            songDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.albumId },
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { songEntity, songId, albumId, connectedPlugins, isInternetAvailable ->
            if (songEntity == null) return@combine null
            
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
            // Default to true for database songs (assume streaming)
            val requiresInternet = true
            val isPlayable = computeIsPlayable(songEntity, connectedPluginIds, requiresInternet, isInternetAvailable)
            
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
            
            songEntity.toDomain(album, artists, isPlayable, requiresInternet)
        }
    }
    
    override fun getAllLikedSongs(): Flow<List<Song>> {
        return combine(
            songDao.getAllLiked(),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { entities, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
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
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(entity, connectedPluginIds, requiresInternet, isInternetAvailable)
                entity.toDomain(album, artists, isPlayable, requiresInternet)
            }
        }
    }
    
    override fun getAllSavedSongs(): Flow<List<Song>> {
        return combine(
            songDao.getAllSaved(),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { entities, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
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
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(entity, connectedPluginIds, requiresInternet, isInternetAvailable)
                entity.toDomain(album, artists, isPlayable, requiresInternet)
            }
        }
    }
    
    override fun getAllDownloadedSongs(): Flow<List<Song>> {
        return combine(
            songDao.getAllDownloaded(),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { entities, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
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
                // Downloaded songs don't require internet
                val requiresInternet = false
                val isPlayable = computeIsPlayable(entity, connectedPluginIds, requiresInternet, isInternetAvailable)
                entity.toDomain(album, artists, isPlayable, requiresInternet)
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
        
        // Check if song already exists to preserve liked/downloaded/artwork status
        val existingSong = songDao.getByMediaId(song.id.pluginId, song.id.sourceId)
        val songEntity = song.toEntity(albumId).copy(
            isLiked = existingSong?.isLiked ?: song.isLiked,
            isDownloaded = existingSong?.isDownloaded ?: song.isDownloaded,
            downloadPath = existingSong?.downloadPath ?: null,
            localArtworkPath = existingSong?.localArtworkPath ?: null
        )
        val songId = songDao.insert(songEntity)
        
        // Download artwork if song is liked or saved
        if ((existingSong?.isLiked ?: song.isLiked) || (existingSong?.isSaved ?: false)) {
            song.artworkUrl?.let { artworkUrl ->
                if (existingSong?.localArtworkPath == null) {
                    val localPath = artworkDownloader.downloadArtwork(artworkUrl, song.id)
                    localPath?.let {
                        songDao.updateLocalArtworkPath(song.id.pluginId, song.id.sourceId, it)
                    }
                }
            }
        }
        
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
        
        // Download artwork if song is being liked
        if (isLiked) {
            val song = getSong(mediaId).first()
            song?.artworkUrl?.let { artworkUrl ->
                val localPath = artworkDownloader.downloadArtwork(artworkUrl, mediaId)
                localPath?.let {
                    songDao.updateLocalArtworkPath(mediaId.pluginId, mediaId.sourceId, it)
                }
            }
        } else {
            // Optionally delete artwork when unliked (or keep it for offline access)
            // For now, we'll keep it cached
        }
    }
    
    override suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean) {
        songDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
        
        // Download artwork if song is being saved
        if (isSaved) {
            val song = getSong(mediaId).first()
            song?.artworkUrl?.let { artworkUrl ->
                val localPath = artworkDownloader.downloadArtwork(artworkUrl, mediaId)
                localPath?.let {
                    songDao.updateLocalArtworkPath(mediaId.pluginId, mediaId.sourceId, it)
                }
            }
        }
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
            playlistDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId).map { it?.id },
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { playlistEntity, playlistId, connectedPlugins, isInternetAvailable ->
            if (playlistEntity == null) return@combine null
            
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
            
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
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(songEntity, connectedPluginIds, requiresInternet, isInternetAvailable)
                songEntity.toDomain(album, artists, isPlayable, requiresInternet)
            }
            
            // Get artwork from first song with non-null artwork, or use playlist's existing artwork
            val artworkUrl = playlistEntity.artworkUrl 
                ?: songs.firstOrNull { it.artworkUrl != null }?.artworkUrl
            
            val playlist = playlistEntity.toDomain(songs)
            playlist.copy(artworkUrl = artworkUrl)
        }
    }
    
    override fun getAllLikedPlaylists(): Flow<List<Playlist>> {
        return combine(
            playlistDao.getAllLiked(),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { entities, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
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
                    // Default to true for database songs (assume streaming)
                    val requiresInternet = true
                    val isPlayable = computeIsPlayable(songEntity, connectedPluginIds, requiresInternet, isInternetAvailable)
                    songEntity.toDomain(album, artists, isPlayable, requiresInternet)
                }
                // Get artwork from first song with non-null artwork, or use playlist's existing artwork
                val artworkUrl = entity.artworkUrl 
                    ?: songs.firstOrNull { it.artworkUrl != null }?.artworkUrl
                val playlist = entity.toDomain(songs)
                playlist.copy(artworkUrl = artworkUrl)
            }
        }
    }
    
    override fun getAllSavedPlaylists(): Flow<List<Playlist>> {
        return combine(
            playlistDao.getAllSaved(),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { entities, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
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
                    // Default to true for database songs (assume streaming)
                    val requiresInternet = true
                    val isPlayable = computeIsPlayable(songEntity, connectedPluginIds, requiresInternet, isInternetAvailable)
                    songEntity.toDomain(album, artists, isPlayable, requiresInternet)
                }
                // Get artwork from first song with non-null artwork, or use playlist's existing artwork
                val artworkUrl = entity.artworkUrl 
                    ?: songs.firstOrNull { it.artworkUrl != null }?.artworkUrl
                val playlist = entity.toDomain(songs)
                playlist.copy(artworkUrl = artworkUrl)
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
                
                // Download artwork for songs added to local playlists
                if (it.localArtworkPath == null && song.artworkUrl != null) {
                    val localPath = artworkDownloader.downloadArtwork(song.artworkUrl, song.id)
                    localPath?.let { path ->
                        songDao.updateLocalArtworkPath(song.id.pluginId, song.id.sourceId, path)
                    }
                }
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
    
    override fun getLikedSongsPlaylist(): Flow<Playlist> {
        return getAllLikedSongs().map { songs ->
            // Get artwork from first song with non-null artwork
            val artworkUrl = songs.firstOrNull { it.artworkUrl != null }?.artworkUrl
            
            Playlist(
                id = MediaId("local", "liked_songs"),
                name = "Liked Songs",
                description = "Songs you've liked",
                artworkUrl = artworkUrl,
                ownerName = null,
                songCount = songs.size,
                isPublic = false,
                isEditable = false,
                songs = songs
            )
        }
    }
}

