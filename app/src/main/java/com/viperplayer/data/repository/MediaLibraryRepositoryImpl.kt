package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.AlbumDao
import com.viperplayer.data.local.dao.ArtistDao
import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.GenreDao
import com.viperplayer.data.local.dao.PlayEventDao
import com.viperplayer.data.local.dao.PlaylistDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.ArtistGenreCrossRef
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.PlayEventEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.mapper.EntityMapper.toDomain
import com.viperplayer.data.local.mapper.EntityMapper.toEntity
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.toArtist
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SongWithStats
import com.viperplayer.domain.model.StatPeriod
import com.viperplayer.domain.model.StatsSummary
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    private val playEventDao: PlayEventDao,
    private val pluginRepository: PluginRepository,
    private val artworkDownloader: ArtworkDownloader,
    private val networkConnectivityChecker: NetworkConnectivityChecker,
    private val localMediaDataSource: com.viperplayer.data.source.LocalMediaDataSource
) : MediaLibraryRepository {

    // Scope for fire-and-forget background work (e.g. artwork caching) that must not block callers
    // such as the play() path.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Helper function to load artist
    private suspend fun loadArtist(artistId: Long): Artist? {
        return artistDao.getByIdSync(artistId)?.toDomain()
    }

    /**
     * Upsert an artist: insert if doesn't exist, update if exists.
     * Returns the artist ID, preserving existing ID to maintain foreign key relationships.
     */
    private suspend fun upsertArtist(artist: Artist): Long {
        val existing = artistDao.getByMediaId(artist.id.pluginId, artist.id.sourceId)
        val entity = artist.toEntity()

        return if (existing != null) {
            // Update existing artist, preserving ID and status fields
            val updatedEntity = entity.copy(
                id = existing.id,
                isLiked = existing.isLiked,
                isSaved = existing.isSaved,
                lastUpdated = System.currentTimeMillis()
            )
            artistDao.update(updatedEntity)
            existing.id
        } else {
            // Insert new artist
            val insertedId = artistDao.insert(entity)
            if (insertedId == -1L) {
                // Insert was ignored due to conflict, try to get existing
                artistDao.getByMediaId(artist.id.pluginId, artist.id.sourceId)?.id
                    ?: throw IllegalStateException("Failed to insert or find artist: ${artist.id}")
            } else {
                insertedId
            }
        }
    }

    /**
     * Upsert an album: insert if doesn't exist, update if exists.
     * Returns the album ID, preserving existing ID to maintain foreign key relationships.
     */
    private suspend fun upsertAlbum(album: Album, primaryArtistId: Long?): Long {
        val existing = albumDao.getByMediaId(album.id.pluginId, album.id.sourceId)
        val entity = album.toEntity(primaryArtistId)

        return if (existing != null) {
            // Update existing album, preserving ID and status fields
            val updatedEntity = entity.copy(
                id = existing.id,
                isLiked = existing.isLiked,
                isSaved = existing.isSaved,
                isDownloaded = existing.isDownloaded,
                lastUpdated = System.currentTimeMillis()
            )
            albumDao.update(updatedEntity)
            existing.id
        } else {
            // Insert new album
            val insertedId = albumDao.insert(entity)
            if (insertedId == -1L) {
                // Insert was ignored due to conflict, try to get existing
                albumDao.getByMediaId(album.id.pluginId, album.id.sourceId)?.id
                    ?: throw IllegalStateException("Failed to insert or find album: ${album.id}")
            } else {
                insertedId
            }
        }
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
        ) { artistEntity, _ ->
            if (artistEntity == null) return@combine null
            artistEntity.toDomain()
        }
    }

    override fun getAllLikedArtists(): Flow<List<Artist>> {
        return artistDao.getAllLiked()
            .map { entities ->
                entities.map { entity ->
                    entity.toDomain()
                }
            }
    }

    override fun getAllSavedArtists(): Flow<List<Artist>> {
        return artistDao.getAllSaved()
            .map { entities ->
                entities.map { entity ->
                    entity.toDomain()
                }
            }
    }

    override suspend fun saveArtist(artist: ArtistDetail): Unit = withContext(Dispatchers.IO) {
        upsertArtist(artist.toArtist())

        // Save topSongs and albums from artist
        artist.topSongs.forEach { song ->
            saveSong(song)
        }
        artist.albums.forEach { album ->
            saveAlbum(album)
        }

        // Save playlists
        artist.playlists.forEach { playlist ->
            savePlaylist(playlist)
        }

        // Save featuring playlists
        artist.featuring.forEach { playlist ->
            savePlaylist(playlist)
        }

        // Save appearsOn items (albums, songs, playlists, artists)
        artist.appearsOn.forEach { item ->
            when (item) {
                is Album -> saveAlbum(item)
                is Song -> saveSong(item)
                is Playlist -> savePlaylist(item)
                // Shallow record only — see similarArtists below.
                is Artist -> upsertArtist(item)
            }
        }

        // Save similar artists shallowly (record only): recursing with saveArtist would re-enter
        // their similarArtists/appearsOn graphs, which are commonly mutual (A<->B) -> unbounded
        // recursion / StackOverflow.
        artist.similarArtists.forEach { similarArtist ->
            upsertArtist(similarArtist)
        }
    }

    override suspend fun setArtistLiked(mediaId: MediaId, isLiked: Boolean): Unit =
        withContext(Dispatchers.IO) {
            artistDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
        }

    override suspend fun setArtistSaved(mediaId: MediaId, isSaved: Boolean): Unit =
        withContext(Dispatchers.IO) {
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
                loadArtist(artistId)
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
                        loadArtist(artistId)
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
                        loadArtist(artistId)
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
                        loadArtist(artistId)
                    }
                    entity.toDomain(artists)
                }
            }
    }

    override suspend fun saveAlbum(album: Album): Unit = withContext(Dispatchers.IO) {
        // Upsert all artists first, preserving their IDs
        val artistIds = album.artists.map { artist ->
            upsertArtist(artist)
        }

        val primaryArtistId = artistIds.firstOrNull()
        val albumId = upsertAlbum(album, primaryArtistId)

        // Clear existing album-artist relationships and recreate them
        crossRefDao.deleteAlbumArtists(albumId)

        // Create cross-refs for all artists
        album.artists.forEachIndexed { index, artist ->
            val artistId = artistIds[index]
            crossRefDao.insertAlbumArtist(
                com.viperplayer.data.local.entity.AlbumArtistCrossRef(
                    albumId = albumId,
                    artistId = artistId,
                    order = index
                )
            )
        }
    }

    override suspend fun setAlbumLiked(mediaId: MediaId, isLiked: Boolean): Unit =
        withContext(Dispatchers.IO) {
            albumDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
        }

    override suspend fun setAlbumSaved(mediaId: MediaId, isSaved: Boolean): Unit =
        withContext(Dispatchers.IO) {
            albumDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
        }

    override suspend fun setAlbumDownloaded(mediaId: MediaId, isDownloaded: Boolean): Unit =
        withContext(Dispatchers.IO) {
            albumDao.updateDownloaded(mediaId.pluginId, mediaId.sourceId, isDownloaded)
        }

    // Songs
    override fun getSong(mediaId: MediaId): Flow<Song?> {
        // Hydrate the artist/album graph only when the song row itself changes (deduped via
        // distinctUntilChanged on the entity) — NOT on every connectivity tick — and via one-shot
        // sync queries instead of three duplicate row subscriptions + per-row Flow fan-out.
        val hydrated: Flow<Pair<SongEntity, Song>?> =
            songDao.getByMediaIdFlow(mediaId.pluginId, mediaId.sourceId)
                .distinctUntilChanged()
                .map { entity -> entity?.let { it to hydrateSong(it) } }
        // isPlayable is the only connectivity-dependent field, so layer it on cheaply.
        return combine(
            hydrated,
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { pair, connectedPlugins, isInternetAvailable ->
            pair?.let { (entity, song) ->
                val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
                song.copy(
                    isPlayable = computeIsPlayable(entity, connectedPluginIds, true, isInternetAvailable)
                )
            }
        }
    }

    /** Loads a song's artists + album graph using one-shot sync queries (no per-row Flow subscriptions). */
    private suspend fun hydrateSong(entity: SongEntity): Song {
        val artists = crossRefDao.getArtistIdsForSong(entity.id).mapNotNull { loadArtist(it) }
        val album = entity.albumId?.let { albumId ->
            albumDao.getByIdSync(albumId)?.let { albumEntity ->
                val albumArtists =
                    crossRefDao.getArtistIdsForAlbum(albumEntity.id).mapNotNull { loadArtist(it) }
                albumEntity.toDomain(albumArtists)
            }
        }
        return entity.toDomain(album, artists, isPlayable = true, requiresInternet = true)
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
                    loadArtist(artistId)
                }
                val album = entity.albumId?.let {
                    albumDao.getById(it).first()?.let { albumEntity ->
                        val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                        val albumArtists = albumArtistIds.mapNotNull { artistId ->
                            loadArtist(artistId)
                        }
                        albumEntity.toDomain(albumArtists)
                    }
                }
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(
                    entity,
                    connectedPluginIds,
                    requiresInternet,
                    isInternetAvailable
                )
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
                    loadArtist(artistId)
                }
                val album = entity.albumId?.let {
                    albumDao.getById(it).first()?.let { albumEntity ->
                        val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                        val albumArtists = albumArtistIds.mapNotNull { artistId ->
                            loadArtist(artistId)
                        }
                        albumEntity.toDomain(albumArtists)
                    }
                }
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(
                    entity,
                    connectedPluginIds,
                    requiresInternet,
                    isInternetAvailable
                )
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
                    loadArtist(artistId)
                }
                val album = entity.albumId?.let {
                    albumDao.getById(it).first()?.let { albumEntity ->
                        val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                        val albumArtists = albumArtistIds.mapNotNull { artistId ->
                            loadArtist(artistId)
                        }
                        albumEntity.toDomain(albumArtists)
                    }
                }
                // Downloaded songs don't require internet
                val requiresInternet = false
                val isPlayable = computeIsPlayable(
                    entity,
                    connectedPluginIds,
                    requiresInternet,
                    isInternetAvailable
                )
                entity.toDomain(album, artists, isPlayable, requiresInternet)
            }
        }
    }

    override suspend fun saveSong(song: Song): Unit = withContext(Dispatchers.IO) {
        // Upsert album first if exists, preserving its ID
        val albumId = song.album?.let { album ->
            // Upsert all album artists first, preserving their IDs
            val albumArtistIds = album.artists.map { artist ->
                upsertArtist(artist)
            }

            val primaryArtistId = albumArtistIds.firstOrNull()
            val albumId = upsertAlbum(album, primaryArtistId)

            // Clear existing album-artist relationships and recreate them
            crossRefDao.deleteAlbumArtists(albumId)

            // Create cross-refs for all album artists
            album.artists.forEachIndexed { index, artist ->
                val artistId = albumArtistIds[index]
                crossRefDao.insertAlbumArtist(
                    com.viperplayer.data.local.entity.AlbumArtistCrossRef(
                        albumId = albumId,
                        artistId = artistId,
                        order = index
                    )
                )
            }

            albumId
        }

        // Check if song already exists to preserve ID and other status fields
        val existingSong = songDao.getByMediaId(song.id.pluginId, song.id.sourceId)
        val songEntity = song.toEntity(albumId).copy(
            // CRITICAL: Preserve the existing ID to avoid breaking foreign key relationships
            id = existingSong?.id ?: 0L,
            // Preserve the saved flag and album link: a partial re-save (e.g. via saveArtist) must
            // not silently un-save the song or drop its album association.
            isSaved = existingSong?.isSaved ?: false,
            albumId = albumId ?: existingSong?.albumId,
            isLiked = existingSong?.isLiked ?: song.isLiked,
            isDownloaded = existingSong?.isDownloaded ?: song.isDownloaded,
            downloadPath = existingSong?.downloadPath ?: null,
            localArtworkPath = existingSong?.localArtworkPath ?: null,
            playCount = existingSong?.playCount ?: 0L,
            lastPlayed = existingSong?.lastPlayed,
            // Preserve audio format and normalization if not provided in new song data
            replayGainDb = song.replayGainDb ?: existingSong?.replayGainDb,
            peakAmplitude = song.peakAmplitude ?: existingSong?.peakAmplitude,
        )

        // Use update() for existing songs to preserve ID, insert() for new songs
        val songId = if (existingSong != null) {
            songDao.update(songEntity)
            existingSong.id // Return existing ID
        } else {
            val insertedId = songDao.insert(songEntity)
            if (insertedId == -1L) {
                // Insert was ignored due to conflict, try to get existing
                songDao.getByMediaId(song.id.pluginId, song.id.sourceId)?.id
                    ?: throw IllegalStateException("Failed to insert or find song: ${song.id}")
            } else {
                insertedId
            }
        }

        // Cache artwork for liked/saved songs OFF the critical path — saveSong is awaited before
        // setMediaItem in play(), so downloading + re-encoding here would delay first audio.
        if ((existingSong?.isLiked ?: song.isLiked) || (existingSong?.isSaved ?: false)) {
            song.artworkUrl?.let { artworkUrl ->
                if (existingSong?.localArtworkPath == null) {
                    ioScope.launch {
                        val localPath = artworkDownloader.downloadArtwork(artworkUrl, song.id)
                        localPath?.let {
                            songDao.updateLocalArtworkPath(song.id.pluginId, song.id.sourceId, it)
                        }
                    }
                }
            }
        }

        // Rewrite song-artist relationships only when the incoming song actually carries artists;
        // a partial re-save (empty artists) must not strip the existing associations.
        if (song.artists.isNotEmpty()) {
            crossRefDao.deleteSongArtists(songId)
            song.artists.forEachIndexed { index, artist ->
                val artistId = upsertArtist(artist)
                crossRefDao.insertSongArtist(
                    com.viperplayer.data.local.entity.SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        order = index
                    )
                )
            }
        }
    }

    override suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean): Unit =
        withContext(Dispatchers.IO) {
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

    override suspend fun setSongSaved(mediaId: MediaId, isSaved: Boolean): Unit =
        withContext(Dispatchers.IO) {
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

    override suspend fun setSongDownloaded(
        mediaId: MediaId,
        isDownloaded: Boolean,
        downloadPath: String?
    ): Unit = withContext(Dispatchers.IO) {
        songDao.updateDownloaded(mediaId.pluginId, mediaId.sourceId, isDownloaded, downloadPath)
    }

    override suspend fun incrementSongPlayCount(mediaId: MediaId): Unit =
        withContext(Dispatchers.IO) {
            songDao.incrementPlayCount(mediaId.pluginId, mediaId.sourceId)
            // Also record a discrete play event so History and the time-windowed Stats screen have a
            // per-play signal. Resolve the local song row first; if the song isn't persisted yet there
            // is nothing to reference (the FK would fail), so we simply skip the event.
            val songId = songDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)?.id
            if (songId != null) {
                playEventDao.insert(
                    PlayEventEntity(
                        songId = songId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

    override suspend fun recordListenedTime(mediaId: MediaId, durationListenedMs: Long): Unit =
        withContext(Dispatchers.IO) {
            // The player reports the real listened time (excluding pauses) when a session ends.
            // Fill it onto the most recent unmeasured play of this song so Stats reflect actual
            // listening rather than the full-duration estimate used as a fallback.
            if (durationListenedMs <= 0L) return@withContext
            val songId = songDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)?.id
                ?: return@withContext
            playEventDao.recordListenedTimeForLatest(songId, durationListenedMs)
        }

    // History & Stats

    /**
     * Hydrate a [SongEntity] into a domain [Song] with its artists, album, and runtime playability —
     * mirrors the inline logic used by the getAll*Songs flows. Must be called from a coroutine.
     */
    private suspend fun songEntityToDomain(
        entity: SongEntity,
        connectedPluginIds: Set<String>,
        isInternetAvailable: Boolean
    ): Song {
        val artistIds = crossRefDao.getArtistIdsForSong(entity.id)
        val artists = artistIds.mapNotNull { loadArtist(it) }
        val album = entity.albumId?.let { albumId ->
            albumDao.getById(albumId).first()?.let { albumEntity ->
                val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                val albumArtists = albumArtistIds.mapNotNull { loadArtist(it) }
                albumEntity.toDomain(albumArtists)
            }
        }
        // Default to true for database songs (assume streaming), as elsewhere in this repository.
        val requiresInternet = true
        val isPlayable = computeIsPlayable(
            entity,
            connectedPluginIds,
            requiresInternet,
            isInternetAvailable
        )
        return entity.toDomain(album, artists, isPlayable, requiresInternet)
    }

    override fun getMostPlayedSongs(period: StatPeriod, limit: Int): Flow<List<SongWithStats>> {
        // Lower bound captured once when the flow is built; the DAO flow re-emits on any event write.
        // Upper bound is open-ended so plays recorded after the screen opens still count.
        val fromTimestamp = period.fromTimestamp()
        return combine(
            playEventDao.mostPlayedSongs(fromTimestamp, Long.MAX_VALUE, limit),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { rows, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
            rows.map { row ->
                SongWithStats(
                    song = songEntityToDomain(row.song, connectedPluginIds, isInternetAvailable),
                    playCount = row.eventCount.toInt(),
                    timeListenedMs = row.timeListenedMs
                )
            }
        }
    }

    override fun getStatsSummary(period: StatPeriod): Flow<StatsSummary> {
        val fromTimestamp = period.fromTimestamp()
        return playEventDao.statsSummary(fromTimestamp, Long.MAX_VALUE).map { summary ->
            StatsSummary(
                totalPlays = summary.totalPlays,
                distinctSongs = summary.distinctSongs,
                totalTimeMs = summary.totalTimeMs
            )
        }
    }

    override fun getHistory(limit: Int): Flow<List<HistoryEntry>> {
        return combine(
            playEventDao.recentHistory(limit),
            pluginRepository.connectedPlugins,
            networkConnectivityChecker.isInternetAvailable
        ) { rows, connectedPlugins, isInternetAvailable ->
            val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()
            rows.map { row ->
                HistoryEntry(
                    song = songEntityToDomain(row.song, connectedPluginIds, isInternetAvailable),
                    playedAt = row.eventTimestamp
                )
            }
        }
    }

    override suspend fun getFirstPlayTimestamp(): Long? = withContext(Dispatchers.IO) {
        playEventDao.firstEventTimestamp()
    }

    override suspend fun clearHistory(): Unit = withContext(Dispatchers.IO) {
        playEventDao.clearAll()
    }

    override suspend fun pruneHistory(cutoffMillis: Long): Unit = withContext(Dispatchers.IO) {
        playEventDao.pruneOlderThan(cutoffMillis)
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
                    loadArtist(artistId)
                }
                val album = songEntity.albumId?.let {
                    albumDao.getById(it).first()?.let { albumEntity ->
                        val albumArtistIds = crossRefDao.getArtistIdsForAlbum(albumEntity.id)
                        val albumArtists = albumArtistIds.mapNotNull { artistId ->
                            loadArtist(artistId)
                        }
                        albumEntity.toDomain(albumArtists)
                    }
                }
                // Default to true for database songs (assume streaming)
                val requiresInternet = true
                val isPlayable = computeIsPlayable(
                    songEntity,
                    connectedPluginIds,
                    requiresInternet,
                    isInternetAvailable
                )
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
                        loadArtist(artistId)
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
                    val isPlayable = computeIsPlayable(
                        songEntity,
                        connectedPluginIds,
                        requiresInternet,
                        isInternetAvailable
                    )
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
                        loadArtist(artistId)
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
                    val isPlayable = computeIsPlayable(
                        songEntity,
                        connectedPluginIds,
                        requiresInternet,
                        isInternetAvailable
                    )
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

    override suspend fun savePlaylist(playlist: Playlist): Unit = withContext(Dispatchers.IO) {
        // Upsert instead of REPLACE: REPLACE would churn the PK and CASCADE-delete every
        // playlist_songs row (and reset status flags). Preserve the id + flags like saveSong does.
        val existing = playlistDao.getByMediaId(playlist.id.pluginId, playlist.id.sourceId)
        val playlistEntity = playlist.toEntity().copy(
            id = existing?.id ?: 0L,
            isLiked = existing?.isLiked ?: false,
            isSaved = existing?.isSaved ?: false,
            isDownloaded = existing?.isDownloaded ?: false,
        )
        val playlistId = if (existing != null) {
            playlistDao.update(playlistEntity)
            existing.id
        } else {
            playlistDao.insert(playlistEntity)
        }

        // Only rewrite the track list when the incoming playlist actually carries songs; a summary
        // save (songs == null, e.g. from saveArtist) must not empty an existing playlist.
        playlist.songs?.let { songs ->
            crossRefDao.deletePlaylistSongs(playlistId)
            songs.forEachIndexed { index, song ->
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
    }

    override suspend fun setPlaylistLiked(mediaId: MediaId, isLiked: Boolean): Unit =
        withContext(Dispatchers.IO) {
            playlistDao.updateLiked(mediaId.pluginId, mediaId.sourceId, isLiked)
        }

    override suspend fun setPlaylistSaved(mediaId: MediaId, isSaved: Boolean): Unit =
        withContext(Dispatchers.IO) {
            playlistDao.updateSaved(mediaId.pluginId, mediaId.sourceId, isSaved)
        }

    override suspend fun isPlaylistSaved(mediaId: MediaId): Boolean =
        withContext(Dispatchers.IO) {
            playlistDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)?.isSaved ?: false
        }

    override suspend fun setPlaylistDownloaded(mediaId: MediaId, isDownloaded: Boolean): Unit =
        withContext(Dispatchers.IO) {
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

    override suspend fun scanLocalFiles(): Unit = withContext(Dispatchers.IO) {
        val localSongs = localMediaDataSource.getAllSongs()
        localSongs.forEach { song ->
            try {
                saveSong(song)
            } catch (e: Exception) {
                // Log and continue, one bad file shouldn't stop the scan
                Timber.e(e, "Failed to save local song: ${song.title}")
            }
        }
    }
}

