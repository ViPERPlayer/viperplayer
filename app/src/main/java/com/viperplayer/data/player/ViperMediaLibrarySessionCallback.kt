package com.viperplayer.data.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.core.net.toUri
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.viperplayer.data.player.MediaItemMapper.toMediaItem
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.usecase.search.SearchUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import timber.log.Timber
import javax.inject.Inject

class ViperMediaLibrarySessionCallback @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val searchUseCase: SearchUseCase,
    private val playerRepository: PlayerRepository,
    private val pluginRepository: PluginRepository
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val ROOT_ID = "root"
        private const val ARTISTS_ID = "artists"
        private const val ALBUMS_ID = "albums"
        private const val PLAYLISTS_ID = "playlists"
        private const val SONGS_ID = "songs"

        private const val PREFIX_ARTIST = "artist/"
        private const val PREFIX_ALBUM = "album/"
        private const val PREFIX_PLAYLIST = "playlist/"
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle("Library")
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                val items = when {
                    parentId == ROOT_ID -> getRootChildren()
                    parentId == ARTISTS_ID -> getAllArtists()
                    parentId == ALBUMS_ID -> getAllAlbums()
                    parentId == PLAYLISTS_ID -> getAllPlaylists()
                    parentId == SONGS_ID -> getAllSongs()
                    parentId.startsWith(PREFIX_ARTIST) -> getArtistChildren(
                        parentId.removePrefix(
                            PREFIX_ARTIST
                        )
                    )

                    parentId.startsWith(PREFIX_ALBUM) -> getAlbumChildren(
                        parentId.removePrefix(
                            PREFIX_ALBUM
                        )
                    )

                    parentId.startsWith(PREFIX_PLAYLIST) -> getPlaylistChildren(
                        parentId.removePrefix(
                            PREFIX_PLAYLIST
                        )
                    )

                    else -> emptyList()
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            } catch (e: Exception) {
                Timber.e(e, "Error in onGetChildren for parentId: $parentId")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return serviceScope.future {
            try {
                // Try to resolve as Song first
                val songId = try {
                    MediaId.fromString(mediaId)
                } catch (e: Exception) {
                    null
                }
                if (songId != null) {
                    val song = mediaLibraryRepository.getSong(songId).first()
                        ?: pluginRepository.getSong(songId).getOrNull()

                    if (song != null) {
                        return@future LibraryResult.ofItem(song.toMediaItem(), null)
                    }
                }

                // Fallback or other types if needed (usually onGetItem used for playable items)
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } catch (e: Exception) {
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return serviceScope.future {
            // Initiate search but don't return results here as per API spec
            // Results are retrieved via onGetSearchResult
            session.notifySearchResultChanged(browser, query, 0, params)
            LibraryResult.ofVoid(params)
        }
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                val result = searchUseCase(query)
                val searchResult = result.getOrNull()

                if (searchResult != null) {
                    val mediaItems = searchResult.items.mapNotNull {
                        when (it) {
                            is Song -> it.toMediaItem()
                            is Artist -> buildBrowsableItem(
                                PREFIX_ARTIST + it.id.toString(),
                                it.name,
                                it.imageUrl,
                                MediaMetadata.MEDIA_TYPE_ARTIST
                            )

                            is Album -> buildBrowsableItem(
                                PREFIX_ALBUM + it.id.toString(),
                                it.name,
                                it.artworkUrl,
                                MediaMetadata.MEDIA_TYPE_ALBUM
                            )

                            is Playlist -> buildBrowsableItem(
                                PREFIX_PLAYLIST + it.id.toString(),
                                it.name,
                                it.artworkUrl,
                                MediaMetadata.MEDIA_TYPE_PLAYLIST
                            )

                            else -> null
                        }
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                } else {
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in onGetSearchResult for query: $query")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        return serviceScope.future {
            val resolvedMediaItems = mediaItems.mapNotNull { mediaItem ->
                try {
                    val mediaId = MediaId.fromString(mediaItem.mediaId)
                    val song = mediaLibraryRepository.getSong(mediaId).first()
                        ?: pluginRepository.getSong(mediaId).getOrNull()

                    song?.toMediaItem() ?: mediaItem
                } catch (e: Exception) {
                    // If parsing fails or fetch fails, pass original item 
                    // (though it likely won't play if ID was invalid)
                    mediaItem
                }
            }.toMutableList()

            resolvedMediaItems
        }
    }

    private fun getRootChildren(): List<MediaItem> {
        return listOf(
            buildBrowsableItem(ARTISTS_ID, "Artists", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            buildBrowsableItem(ALBUMS_ID, "Albums", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            buildBrowsableItem(PLAYLISTS_ID, "Playlists", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            buildBrowsableItem(SONGS_ID, "Songs", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        )
    }

    private suspend fun getAllArtists(): List<MediaItem> {
        val artists = mediaLibraryRepository.getAllSavedArtists().first()
        return artists.map { artist ->
            buildBrowsableItem(
                PREFIX_ARTIST + artist.id.toString(), artist.name, artist.imageUrl,
                MediaMetadata.MEDIA_TYPE_ARTIST
            )
        }
    }

    private suspend fun getAllAlbums(): List<MediaItem> {
        val albums = mediaLibraryRepository.getAllSavedAlbums().first()
        return albums.map { album ->
            buildBrowsableItem(
                PREFIX_ALBUM + album.id.toString(), album.name, album.artworkUrl,
                MediaMetadata.MEDIA_TYPE_ALBUM
            )
        }
    }

    private suspend fun getAllPlaylists(): List<MediaItem> {
        val playlists = mediaLibraryRepository.getAllSavedPlaylists().first()
        return playlists.map { playlist ->
            buildBrowsableItem(
                PREFIX_PLAYLIST + playlist.id.toString(),
                playlist.name,
                playlist.artworkUrl,
                MediaMetadata.MEDIA_TYPE_PLAYLIST
            )
        }
    }

    private suspend fun getAllSongs(): List<MediaItem> {
        val songs = mediaLibraryRepository.getAllSavedSongs().first()
        return songs.map { it.toMediaItem() }
    }

    private suspend fun getArtistChildren(artistIdString: String): List<MediaItem> {
        val artistId = MediaId.fromString(artistIdString)
        // Try fetching from plugin repository first (remote)
        val artist = pluginRepository.getArtist(artistId).getOrNull()
        if (artist != null && artist.topSongs.isNotEmpty()) {
            return artist.topSongs.map { it.toMediaItem() }
        }

        // Fallback to local DB
        val allSongs = mediaLibraryRepository.getAllSavedSongs().first()
        return allSongs.filter { song ->
            song.artists.any { it.id == artistId }
        }.map { it.toMediaItem() }
    }

    private suspend fun getAlbumChildren(albumIdString: String): List<MediaItem> {
        val albumId = MediaId.fromString(albumIdString)
        val album = pluginRepository.getAlbum(albumId).getOrNull()
        if (album?.songs != null && album.songs.isNotEmpty()) {
            return album.songs.map { it.toMediaItem() }
        }

        val allSongs = mediaLibraryRepository.getAllSavedSongs().first()
        return allSongs.filter { song ->
            song.album?.id == albumId
        }.map { it.toMediaItem() }
    }

    private suspend fun getPlaylistChildren(playlistIdString: String): List<MediaItem> {
        val playlistId = MediaId.fromString(playlistIdString)
        val playlist = pluginRepository.getPlaylist(playlistId).getOrNull()
        if (playlist?.songs != null && playlist.songs.isNotEmpty()) {
            return playlist.songs.map { it.toMediaItem() }
        }

        val localPlaylist = mediaLibraryRepository.getPlaylist(playlistId).first()
        return localPlaylist?.songs?.map { it.toMediaItem() } ?: emptyList()
    }

    private fun buildBrowsableItem(
        id: String,
        title: String,
        iconUri: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)

        // Artwork so Android Auto / the media browser render a thumbnail rather than a plain row.
        if (!iconUri.isNullOrBlank()) {
            metadata.setArtworkUri(iconUri.toUri())
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
            .build()
    }
}
