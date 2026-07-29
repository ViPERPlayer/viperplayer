package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.GenreDao
import com.viperplayer.data.local.dao.GenreWithSongCount
import com.viperplayer.data.local.dao.PlaylistSongCount
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.AlbumArtistCrossRef
import com.viperplayer.data.local.entity.ArtistGenreCrossRef
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.PlaylistSongCrossRef
import com.viperplayer.data.local.entity.QueueSongCrossRef
import com.viperplayer.data.local.entity.SongArtistCrossRef
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.SongGenreCrossRef
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TasteState
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared in-memory fakes for [DaylistRepositoryImplTest]. The two mega-interfaces reuse the existing
 * `Unsupported…` delegates (overriding only what the daylist repository touches); the DAOs +
 * [TasteRepository] have no such helper, so those fakes implement only the read methods used and throw
 * via [nope] for the rest (the same pattern TasteRepositoryImplTest uses for its SongDao fake).
 */

private fun nope(): Nothing = throw NotImplementedError("Daylist test fake: method not faked")

/**
 * A [SettingsRepository] fake exposing only [recommendationsEnabled] (the daylist gate). The flag is
 * mutable so a test can flip the opt-in off mid-slot and assert the daylist vanishes.
 */
class FakeDaylistSettingsRepository(
    recommendationsOn: Boolean,
) : SettingsRepository by UnsupportedSettingsRepository() {
    private val enabled = MutableStateFlow(recommendationsOn)
    override val recommendationsEnabled: Flow<Boolean> get() = enabled
    fun setEnabled(value: Boolean) { enabled.value = value }
}

/** A [MediaLibraryRepository] fake resolving songs from an in-memory id->Song map ([getSong]). */
class FakeDaylistMediaLibraryRepository(
    private val songsById: Map<MediaId, Song>,
) : MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
    override fun getSong(mediaId: MediaId): Flow<Song?> = flowOf(songsById[mediaId])
}

/** A [TasteRepository] fake returning a fixed [currentBucketTaste]; everything else is unused. */
class FakeDaylistTasteRepository(
    private val bucketTaste: TasteState,
) : TasteRepository {
    override suspend fun currentBucketTaste(): TasteState = bucketTaste
    override suspend fun taste(): TasteState = nope()
    override suspend fun moodCentroids(): List<FloatArray> = nope()
    override suspend fun suppressDiscoveryId(id: String) = nope()
    override suspend fun suppressedDiscoveryIds(): Set<String> = nope()
    override suspend fun recordServed(servedIds: List<Long>) = nope()
    override suspend fun servedRecency(): Map<Long, Float> = nope()
    override suspend fun servedCounts(): Map<Long, Int> = nope()
    override suspend fun onInteraction(interaction: Interaction) = nope()
    override suspend fun invalidate() = nope()
}

/** A [GenreDao] fake resolving genre names by id ([getByIds]); everything else is unused. */
class FakeDaylistGenreDao(
    private val genres: List<GenreEntity> = emptyList(),
) : GenreDao {
    override suspend fun getByIds(ids: List<Long>): List<GenreEntity> = genres.filter { it.id in ids }
    override suspend fun getByName(name: String): GenreEntity? = nope()
    override suspend fun getById(id: Long): GenreEntity? = nope()
    override suspend fun insert(genre: GenreEntity): Long = nope()
    override suspend fun insertAll(genres: List<GenreEntity>): List<Long> = nope()
    override suspend fun getIdByName(name: String): Long? = nope()
    override fun getGenresWithSongCounts(): Flow<List<GenreWithSongCount>> = nope()
    override fun getSongsForGenre(genreId: Long): Flow<List<SongEntity>> = nope()
}

/** A [CrossRefDao] fake resolving a song's genre ids ([getGenreIdsForSong]); everything else is unused. */
class FakeDaylistCrossRefDao(
    private val genreIdsBySong: Map<Long, List<Long>> = emptyMap(),
) : CrossRefDao {
    override suspend fun getGenreIdsForSong(songId: Long): List<Long> = genreIdsBySong[songId].orEmpty()
    override suspend fun getArtistIdsForSong(songId: Long): List<Long> = nope()
    override suspend fun getSongIdsForArtist(artistId: Long): List<Long> = nope()
    override suspend fun insertSongArtist(crossRef: SongArtistCrossRef) = nope()
    override suspend fun insertSongArtists(crossRefs: List<SongArtistCrossRef>) = nope()
    override suspend fun deleteSongArtists(songId: Long) = nope()
    override suspend fun deleteArtistSongs(artistId: Long) = nope()
    override suspend fun getArtistIdsForAlbum(albumId: Long): List<Long> = nope()
    override suspend fun getAlbumIdsForArtist(artistId: Long): List<Long> = nope()
    override suspend fun insertAlbumArtist(crossRef: AlbumArtistCrossRef) = nope()
    override suspend fun insertAlbumArtists(crossRefs: List<AlbumArtistCrossRef>) = nope()
    override suspend fun deleteAlbumArtists(albumId: Long) = nope()
    override suspend fun deleteArtistAlbums(artistId: Long) = nope()
    override suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long> = nope()
    override fun getSongIdsForPlaylistFlow(playlistId: Long): Flow<List<Long>> = nope()
    override fun getPlaylistSongCounts(): Flow<List<PlaylistSongCount>> = nope()
    override suspend fun getPlaylistIdsForSong(songId: Long): List<Long> = nope()
    override suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef) = nope()
    override suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>) = nope()
    override suspend fun deletePlaylistSongs(playlistId: Long) = nope()
    override suspend fun deleteSongPlaylists(songId: Long) = nope()
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = nope()
    override suspend fun getMaxPositionForPlaylist(playlistId: Long): Int? = nope()
    override suspend fun countSongInPlaylist(playlistId: Long, songId: Long): Int = nope()
    override suspend fun getGenreIdsForArtist(artistId: Long): List<Long> = nope()
    override suspend fun getArtistIdsForGenre(genreId: Long): List<Long> = nope()
    override suspend fun insertArtistGenre(crossRef: ArtistGenreCrossRef) = nope()
    override suspend fun insertArtistGenres(crossRefs: List<ArtistGenreCrossRef>) = nope()
    override suspend fun deleteArtistGenres(artistId: Long) = nope()
    override suspend fun deleteGenreArtists(genreId: Long) = nope()
    override suspend fun getSongIdsForGenre(genreId: Long): List<Long> = nope()
    override suspend fun insertSongGenre(crossRef: SongGenreCrossRef) = nope()
    override suspend fun insertSongGenres(crossRefs: List<SongGenreCrossRef>) = nope()
    override suspend fun deleteSongGenres(songId: Long) = nope()
    override suspend fun getSongIdsForQueue(): List<Long> = nope()
    override suspend fun getQueuePositionForSong(songId: Long): Int? = nope()
    override suspend fun insertQueueSong(crossRef: QueueSongCrossRef) = nope()
    override suspend fun insertQueueSongs(crossRefs: List<QueueSongCrossRef>) = nope()
    override suspend fun clearQueue() = nope()
    override suspend fun removeSongFromQueue(songId: Long) = nope()
}

/**
 * A [SongDao] fake serving the four methods the daylist repository reads: the embedding index
 * ([getAllEmbeddings]), row hydration by id ([getByIds]), and the liked/recent exclude pools.
 */
class FakeDaylistSongDao(
    private val embeddings: List<SongEmbeddingRow> = emptyList(),
    private val rows: List<SongEntity> = emptyList(),
    private val liked: List<SongEntity> = emptyList(),
    private val recent: List<SongEntity> = emptyList(),
) : SongDao {
    override suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow> = embeddings
    override suspend fun getByIds(ids: List<Long>): List<SongEntity> = rows.filter { it.id in ids }
    override fun getAllLiked(): Flow<List<SongEntity>> = flowOf(liked)
    override fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>> = flowOf(recent)
    override suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity? = nope()
    override fun getById(id: Long): Flow<SongEntity?> = nope()
    override suspend fun getByIdSync(id: Long): SongEntity? = nope()
    override fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?> = nope()
    override fun getByAlbum(albumId: Long): Flow<List<SongEntity>> = nope()
    override fun getAllSaved(): Flow<List<SongEntity>> = nope()
    override fun getAllSavedByDateAddedDesc(): Flow<List<SongEntity>> = nope()
    override fun getAllDownloaded(): Flow<List<SongEntity>> = nope()
    override fun observeLikedCount(): Flow<Int> = nope()
    override fun observeDownloadedCount(): Flow<Int> = nope()
    override fun getAll(): Flow<List<SongEntity>> = nope()
    override fun search(query: String): Flow<List<SongEntity>> = nope()
    override fun getMostPlayed(limit: Int): Flow<List<SongEntity>> = nope()
    override suspend fun insert(song: SongEntity): Long = nope()
    override suspend fun insertAll(songs: List<SongEntity>) = nope()
    override suspend fun update(song: SongEntity) = nope()
    override suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean) = nope()
    override suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean) = nope()
    override suspend fun updateDownloaded( idType: String, pluginId: String, sourceId: String, isDownloaded: Boolean, downloadPath: String?) = nope()
    override suspend fun updateLocalArtworkPath( idType: String, pluginId: String, sourceId: String, localArtworkPath: String? ) = nope()
    override suspend fun incrementPlayCount( idType: String, pluginId: String, sourceId: String, timestamp: Long ) = nope()
    override suspend fun setEmbedding( idType: String, pluginId: String, sourceId: String, embedding: ByteArray?, modelVersion: String?, computedAtMs: Long? ) = nope()
    override suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray? = nope()
    override suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int): List<SongEntity> = nope()
    override suspend fun getSongsMissingEmbeddingPaged( currentModelVersion: String, limit: Int, offset: Int, ): List<SongEntity> = nope()
    override fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
    override suspend fun getStreamingSongsMissingEmbeddingPaged( currentModelVersion: String, limit: Int, offset: Int, ): List<SongEntity> = nope()
    override fun countStreamingSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
    override suspend fun delete(idType: String, pluginId: String, sourceId: String) = nope()
    override suspend fun deleteAll() = nope()
    override suspend fun deleteOrphanedSongs(): Int = nope()
}
