package com.viperplayer.data.repository

import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.data.preferences.PluginPreferences
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PluginRepository.
 */
@Singleton
class PluginRepositoryImpl @Inject constructor(
    private val dataSource: PluginDataSource,
    private val pluginPreferences: PluginPreferences
) : PluginRepository {
    companion object {
        private const val TAG = "PluginRepository"
    }
    
    override val discoveredPlugins: Flow<List<PluginInfo>>
        get() = dataSource.discoveredPlugins.map { plugins ->
            plugins.values.map { discovered ->
                PluginInfo(
                    id = discovered.id,
                    name = discovered.name,
                    version = discovered.version,
                    apiVersion = null,
                    description = discovered.description,
                    author = null,
                )
            }
        }
    
    override val connectedPlugins: Flow<List<Plugin>>
        get() = dataSource.connectedPlugins.map { plugins ->
            plugins.values.map { connected ->
                Plugin(
                    info = connected.info,
                    capabilities = connected.service.capabilities.toDomain(),
                    isConnected = true
                )
            }
        }
    
    override suspend fun discoverPlugins() {
        dataSource.discoverAndUpdatePlugins()
    }

    override suspend fun connectPlugin(pluginId: String): Result<Plugin> {
        TODO("Not implemented yet")
    }

    override suspend fun disconnectPlugin(pluginId: String) {
        Timber.d("disconnectPlugin() called for: $pluginId")
        dataSource.disconnectPlugin(pluginId)
    }
    
    override suspend fun disconnectAll() {
        Timber.d("disconnectAll() called")
        dataSource.disconnectAll()
    }
    
    override suspend fun enablePlugin(pluginId: String): Result<Unit> {
        Timber.d("enablePlugin() called for: $pluginId")
        return try {
            dataSource.enablePlugin(pluginId)
            Timber.d("enablePlugin() succeeded for: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "enablePlugin() failed for: $pluginId")
            Result.failure(e)
        }
    }
    
    override suspend fun disablePlugin(pluginId: String): Result<Unit> {
        Timber.d("disablePlugin() called for: $pluginId")
        return try {
            dataSource.disablePlugin(pluginId)
            Timber.d("disablePlugin() succeeded for: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "disablePlugin() failed for: $pluginId")
            Result.failure(e)
        }
    }
    
    override val pluginDisabledStates: Flow<Map<String, Boolean>>
        get() = combine(
            dataSource.discoveredPlugins,
            pluginPreferences.disabledPlugins
        ) { discovered, disabledSet ->
            discovered.keys.associateWith { pluginId ->
                disabledSet.contains(pluginId)
            }
        }

    // Gets search suggestions from all plugins asynchronously and publishes merged results
    // as soon as it gets them
    override suspend fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestionsResultV1>>> {
        return dataSource.getSearchSuggestions(query)
    }
    
    override suspend fun search(
        query: String,
        types: Int,
        cursor: String?,
        limit: Int
    ): Result<com.viperplayer.plugin.v1.SearchResult> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(com.viperplayer.plugin.v1.SearchResult())
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.search(pluginId, query, types, cursor, limit)
                    } catch (e: Exception) {
                        com.viperplayer.plugin.v1.SearchResult(
                            emptyList(), null
                        ) // Return empty on error
                    }
                }
            }.awaitAll()
            
            // Merge results from all plugins
            val merged = com.viperplayer.plugin.v1.SearchResult(
                sections = results.flatMap { it.sections },
                nextCursor = null
            )
            
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchInPlugin(
        pluginId: String,
        query: String,
        types: Int,
        cursor: String?,
        limit: Int
    ): Result<SearchResult> {
        TODO()
//        return try {
//            val result = dataSource.search(pluginId, query, types, cursor, limit)
//            Result.success(result)
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
    }
    
    override suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<BrowseCategory>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(PagedResult<BrowseCategory>(emptyList()))
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.getBrowseCategories(pluginId, cursor, limit)
                    } catch (e: Exception) {
                        PagedResult<BrowseCategory>(emptyList())
                    }
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.flatMap { it.items }
            )
            
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCategoryContents(
        pluginId: String,
        categoryId: String,
        cursor: String?,
        limit: Int
    ): Result<SearchResult> {
        return try {
            val plugin = dataSource.getPlugin(pluginId)
                ?: return Result.failure(IllegalStateException("Plugin not connected"))
            
            // For now, return empty result - need to implement in data source
            Result.success(SearchResult())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(PagedResult<Song>(emptyList()))
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.getLibrarySongs(pluginId, cursor, limit)
                    } catch (e: Exception) {
                        PagedResult<Song>(emptyList())
                    }
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.flatMap { it.items }
            )
            
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(PagedResult<Album>(emptyList()))
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.getLibraryAlbums(pluginId, cursor, limit)
                    } catch (e: Exception) {
                        PagedResult<Album>(emptyList())
                    }
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.flatMap { it.items }
            )
            
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Artist>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(PagedResult<Artist>(emptyList()))
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.getLibraryArtists(pluginId, cursor, limit)
                    } catch (e: Exception) {
                        Timber.e("Error getting library artists from plugin: $pluginId", e)
                        PagedResult<Artist>(emptyList())
                    }
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.flatMap { it.items }
            )
            
            Timber.d("getLibraryArtists() completed: ${merged.items.size} artists from ${plugins.size} plugins")
            Result.success(merged)
        } catch (e: Exception) {
            Timber.e(e, "Error in getLibraryArtists()")
            Result.failure(e)
        }
    }
    
    override suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Playlist>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(PagedResult<Playlist>(emptyList()))
            }
            
            val results = plugins.keys.map { pluginId ->
                async {
                    try {
                        dataSource.getLibraryPlaylists(pluginId, cursor, limit)
                    } catch (e: Exception) {
                        Timber.e("Error getting library playlists from plugin: $pluginId", e)
                        PagedResult<Playlist>(emptyList())
                    }
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.flatMap { it.items }
            )
            
            Timber.d("getLibraryPlaylists() completed: ${merged.items.size} playlists from ${plugins.size} plugins")
            Result.success(merged)
        } catch (e: Exception) {
            Timber.e(e, "Error in getLibraryPlaylists()")
            Result.failure(e)
        }
    }
    
    override suspend fun getSong(mediaId: MediaId): Result<Song> {
        return try {
            val plugin = dataSource.getPlugin(mediaId.pluginId)
                ?: return Result.failure(IllegalStateException("Plugin not connected"))
            // Need to implement in data source
            Result.failure(NotImplementedError())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAlbum(mediaId: MediaId): Result<Album> {
        return try {
            Timber.d("getAlbum() called for: ${mediaId.pluginId}:${mediaId.sourceId}")
            val album = dataSource.getAlbum(mediaId)
            Timber.d("getAlbum() completed: ${album.name}")
            Result.success(album)
        } catch (e: Exception) {
            Timber.e(e, "Error in getAlbum()")
            Result.failure(e)
        }
    }
    
    override suspend fun getArtist(mediaId: MediaId): Result<Artist> {
        return try {
            Timber.d("getArtist() called for: ${mediaId.pluginId}:${mediaId.sourceId}")
            val artist = dataSource.getArtist(mediaId)
            Timber.d("getArtist() completed: ${artist.name}")
            Result.success(artist)
        } catch (e: Exception) {
            Timber.e(e, "Error in getArtist()")
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylist(mediaId: MediaId): Result<Playlist> {
        return try {
            Timber.d("getPlaylist() called for: ${mediaId.pluginId}:${mediaId.sourceId}")
            val playlist = dataSource.getPlaylist(mediaId)
            Timber.d("getPlaylist() completed: ${playlist.name}")
            Result.success(playlist)
        } catch (e: Exception) {
            Timber.e(e, "Error in getPlaylist()")
            Result.failure(e)
        }
    }

    override suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return try {
            Timber.d("getArtistSongs() called for: ${artistId.pluginId}:${artistId.sourceId}")
            val result = dataSource.getArtistSongs(artistId, cursor, limit)
            Timber.d("getArtistSongs() completed: ${result.items.size} songs")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error in getArtistSongs()")
            Result.failure(e)
        }
    }

    override suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> {
        return try {
            Timber.d("getArtistAlbums() called for: ${artistId.pluginId}:${artistId.sourceId}")
            val result = dataSource.getArtistAlbums(artistId, cursor, limit)
            Timber.d("getArtistAlbums() completed: ${result.items.size} albums")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error in getArtistAlbums()")
            Result.failure(e)
        }
    }

    override suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return try {
            Timber.d("getPlaylistSongs() called for: ${playlistId.pluginId}:${playlistId.sourceId}")
            val result = dataSource.getPlaylistSongs(playlistId, cursor, limit)
            Timber.d("getPlaylistSongs() completed: ${result.items.size} songs")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error in getPlaylistSongs()")
            Result.failure(e)
        }
    }
}
