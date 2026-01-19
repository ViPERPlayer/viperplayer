package com.viperplayer.data.plugin.handler

import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.data.source.PluginException
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.ErrorCodes
import com.viperplayer.plugin.v1.HomeContent
import com.viperplayer.plugin.v1.IAlbumCallback
import com.viperplayer.plugin.v1.IAlbumsCallback
import com.viperplayer.plugin.v1.IArtistCallback
import com.viperplayer.plugin.v1.IArtistsCallback
import com.viperplayer.plugin.v1.IHomeContentCallback
import com.viperplayer.plugin.v1.IPlaylistCallback
import com.viperplayer.plugin.v1.IPlaylistsCallback
import com.viperplayer.plugin.v1.ISearchCallback
import com.viperplayer.plugin.v1.ISearchSuggestionsCallback
import com.viperplayer.plugin.v1.ISongCallback
import com.viperplayer.plugin.v1.ISongsCallback
import com.viperplayer.plugin.v1.IStreamSourceCallback
import com.viperplayer.plugin.v1.IViperPluginV1
import com.viperplayer.plugin.v1.PluginCapabilities
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.viperplayer.plugin.v1.Album as AidlAlbum
import com.viperplayer.plugin.v1.Artist as AidlArtist
import com.viperplayer.plugin.v1.Playlist as AidlPlaylist
import com.viperplayer.plugin.v1.SearchResult as AidlSearchResult
import com.viperplayer.plugin.v1.Song as AidlSong

/**
 * Handler for V1 API plugins.
 * Implements all plugin operations for the V1 API.
 */
class PluginHandlerV1(
    private val pluginId: String,
    private val service: IViperPluginV1
) : PluginHandler {
    override val apiVersion: Int = 1
    
    /**
     * Direct access to the V1 service interface.
     * This is needed for capabilities and other service-specific operations.
     */
    val v1Service: IViperPluginV1
        get() = service
    
    override fun getCapabilities(): Result<PluginCapabilities> {
        return runCatching { service.capabilities }
    }
    
    override suspend fun getSearchSuggestions(query: String): Result<SearchSuggestionsResultV1> {
        return runCatching {
            suspendCancellableCoroutine { continuation ->
                Timber.d("Getting search suggestions from plugin: $pluginId, query: $query")
                service.getSearchSuggestions(query, object : ISearchSuggestionsCallback.Stub() {
                    override fun onSuccess(result: SearchSuggestionsResultV1) {
                        Timber.d("Search suggestions received from plugin: $pluginId, suggestions: ${result.suggestions.size}")
                        continuation.resume(result)
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        Timber.e("Search suggestions failed from plugin: $pluginId, error: $errorCode, message: $message")
                        continuation.resumeWithException(PluginException(errorCode, message))
                    }
                })
            }
        }
    }
    
    override suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): AidlSearchResult {
        val types = when (filter) {
            SearchFilter.SONG -> AidlSearchResult.TYPE_SONG
            SearchFilter.ALBUM -> AidlSearchResult.TYPE_ALBUM
            SearchFilter.ARTIST -> AidlSearchResult.TYPE_ARTIST
            SearchFilter.PLAYLIST -> AidlSearchResult.TYPE_PLAYLIST
            else -> AidlSearchResult.TYPE_ALL
        }
        Timber.d("Searching in plugin: $pluginId, query: $query, filter: $filter (types: $types), limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.search(query, types, cursor, limit, object : ISearchCallback.Stub() {
                    override fun onSuccess(result: AidlSearchResult) {
                        Timber.d("Search result received from plugin: $pluginId, items: ${result.items.size}")
                        cont.resume(result)
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        Timber.e("Search failed from plugin: $pluginId, error: $errorCode, message: $message")
                        cont.resumeWithException(PluginException(errorCode, message))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Search failed for plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int
    ): PagedResult<BrowseCategory> {
        // TODO: Implement when V1 API supports this
        return PagedResult(emptyList())
    }
    
    override suspend fun getCategoryContents(
        categoryId: String,
        cursor: String?,
        limit: Int
    ): AidlSearchResult {
        // TODO: Implement when V1 API supports this
        return AidlSearchResult(emptyList(), null)
    }
    
    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting library songs from plugin: $pluginId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getLibrarySongs(cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Library songs received from plugin: $pluginId, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library songs from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }

    override suspend fun getHomeSections(): HomeContent {
        Timber.d("Getting home content from plugin: $pluginId")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getHomeSections(object : IHomeContentCallback.Stub() {
                    override fun onSuccess(content: HomeContent) {
                        Timber.d("Home content received from plugin: $pluginId, quickPicks: ${content.quickPicks?.size}, sections: ${content.sections.size}")
                        cont.resume(content)
                    }

                    override fun onFailure(errorMessage: String?) {
                        Timber.e("Home content failed from plugin: $pluginId, error: $errorMessage")
                        cont.resumeWithException(PluginException(ErrorCodes.UNKNOWN, errorMessage))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get home content from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        Timber.d("Getting library albums from plugin: $pluginId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getLibraryAlbums(cursor, limit, object : IAlbumsCallback.Stub() {
                    override fun onSuccess(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                        Timber.d("Library albums received from plugin: $pluginId, count: ${albums.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(albums.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library albums from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int
    ): PagedResult<Artist> {
        Timber.d("Getting library artists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getLibraryArtists(cursor, limit, object : IArtistsCallback.Stub() {
                    override fun onSuccess(artists: MutableList<AidlArtist>, nextCursor: String?) {
                        Timber.d("Library artists received from plugin: $pluginId, count: ${artists.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(artists.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library artists from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int
    ): PagedResult<Playlist> {
        Timber.d("Getting library playlists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getLibraryPlaylists(
                    cursor,
                    limit,
                    object : IPlaylistsCallback.Stub() {
                        override fun onSuccess(
                            playlists: MutableList<AidlPlaylist>,
                            nextCursor: String?
                        ) {
                            Timber.d("Library playlists received from plugin: $pluginId, count: ${playlists.size}, nextCursor: $nextCursor")
                            cont.resume(
                                PagedResult(
                                    playlists.map { it.toDomain(pluginId) },
                                    nextCursor
                                )
                            )
                        }
                    })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library playlists from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getSong(id: String): Song {
        Timber.d("Getting song from plugin: $pluginId, songId: $id")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getSong(id, object : ISongCallback.Stub() {
                    override fun onSuccess(song: AidlSong) {
                        try {
                            Timber.d("Song received from plugin: $pluginId, title: ${song.title}")
                            cont.resume(song.toDomain(pluginId))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing song from plugin $pluginId")
                            cont.resumeWithException(e)
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get song from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getAlbum(id: String): Album {
        Timber.d("Getting album from plugin: $pluginId, albumId: $id")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getAlbum(id, object : IAlbumCallback.Stub() {
                    override fun onSuccess(album: AidlAlbum) {
                        try {
                            Timber.d("Album received from plugin: $pluginId, name: ${album.name}")
                            cont.resume(album.toDomain(pluginId))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing album from plugin $pluginId")
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        Timber.e("Album failed from plugin: $pluginId, error: $errorCode, message: $message")
                        cont.resumeWithException(PluginException(errorCode, message))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get album from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getArtist(id: String): Artist {
        Timber.d("Getting artist from plugin: $pluginId, artistId: $id")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getArtist(id, object : IArtistCallback.Stub() {
                    override fun onSuccess(aidlArtist: AidlArtist) {
                        try {
                            Timber.d("Artist received from plugin: $pluginId, name: ${aidlArtist.name}, topSongs: ${aidlArtist.topSongs.size}, albums: ${aidlArtist.albums.size}")
                            // Map the artist - topSongs and albums are already included in the AIDL Artist
                            // and will be available through the artist object
                            cont.resume(aidlArtist.toDomain(pluginId))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing artist from plugin $pluginId")
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        Timber.e("Artist failed from plugin: $pluginId, error: $errorCode, message: $message")
                        cont.resumeWithException(PluginException(errorCode, message))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getPlaylist(id: String): Playlist {
        Timber.d("Getting playlist from plugin: $pluginId, playlistId: $id")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getPlaylist(id, object : IPlaylistCallback.Stub() {
                    override fun onSuccess(playlist: AidlPlaylist) {
                        try {
                            Timber.d("Playlist received from plugin: $pluginId, name: ${playlist.name}")
                            cont.resume(playlist.toDomain(pluginId))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing playlist from plugin $pluginId")
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        Timber.e("Playlist failed from plugin: $pluginId, error: $errorCode, message: $message")
                        cont.resumeWithException(PluginException(errorCode, message))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playlist from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getArtistSongs(
        artistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting artist songs from plugin: $pluginId, artistId: $artistId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getArtistSongs(artistId, cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Artist songs received from plugin: $pluginId, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist songs from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getArtistAlbums(
        artistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        Timber.d("Getting artist albums from plugin: $pluginId, artistId: $artistId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getArtistAlbums(artistId, cursor, limit, object : IAlbumsCallback.Stub() {
                    override fun onSuccess(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                        Timber.d("Artist albums received from plugin: $pluginId, count: ${albums.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(albums.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist albums from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getPlaylistSongs(
        playlistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting playlist songs from plugin: $pluginId, playlistId: $playlistId, cursor: $cursor, limit: $limit")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getPlaylistSongs(playlistId, cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Playlist songs received from plugin: $pluginId, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain(pluginId) }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playlist songs from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun getStream(mediaId: String): com.viperplayer.plugin.v1.StreamSource {
        Timber.d("Getting stream from plugin: $pluginId, mediaId: $mediaId")
        return suspendCancellableCoroutine { cont ->
            try {
                service.getStream(mediaId, object : IStreamSourceCallback.Stub() {
                    override fun onSuccess(source: com.viperplayer.plugin.v1.StreamSource) {
                        Timber.d("Stream source received from plugin: $pluginId, type: ${source.type}")
                        cont.resume(source)
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stream from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun disconnect() {
        try {
            service.onDisconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error calling disconnect() on plugin: $pluginId")
            // Continue even if disconnect fails
        }
    }
}

