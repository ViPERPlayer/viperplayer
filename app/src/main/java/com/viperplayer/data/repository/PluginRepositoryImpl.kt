package com.viperplayer.data.repository

import com.viperplayer.data.mapper.PluginMapper.toDomain
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
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.plugin.v1.SearchFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PluginRepository.
 */
@Singleton
class PluginRepositoryImpl @Inject constructor(
    private val dataSource: PluginDataSource
) : PluginRepository {
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
            plugins.values.mapNotNull { connected ->
                try {
                    Plugin(
                        info = connected.info,
                        capabilities = connected.handler.getCapabilities().getOrThrow().toDomain(),
                        isConnected = true
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to map plugin to domain model")
                    null
                }
            }
        }
    
    override suspend fun refreshPlugins() {
        dataSource.refreshPlugins()
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

    override suspend fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> {
        return dataSource.getSearchSuggestions(query)
    }
    
    override suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): Result<SearchResult> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            if (plugins.isEmpty()) {
                return@coroutineScope Result.success(SearchResult())
            }

            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.search(pluginId, query, filter, cursor, limit).map { it.toDomain(pluginId) }
                }
            }.awaitAll()
            
            // Merge sections from all plugins, mapping them to domain models
            val successfulResults = results.mapNotNull { it.getOrNull() }
            val merged = successfulResults.flatMap { it.items }

            Result.success(SearchResult(
                items = merged,
                nextCursor = null
            ))
        } catch (e: Exception) {
            Timber.e(e, "Error in search")
            Result.failure(e)
        }
    }

    override suspend fun getHomeContent(): Result<List<Pair<String, com.viperplayer.domain.model.HomeContent>>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            val results = plugins.keys.map { pluginId ->
                async {
                    val result = dataSource.getHomeContent(pluginId)
                    result.map { pluginId to it.toDomain(pluginId) }
                }
            }.awaitAll()
            
            val successfulResults = results.mapNotNull { it.getOrNull() }
            Result.success(successfulResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<BrowseCategory>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.getBrowseCategories(pluginId, cursor, limit)
                }
            }.awaitAll()
            
            val successfulResults = results.mapNotNull { it.getOrNull() }
            val merged = PagedResult(
                items = successfulResults.flatMap { it.items }
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
            val result = dataSource.getCategoryContents(pluginId, categoryId, cursor, limit)
            result.map { it.toDomain(pluginId) }
        } catch (e: Exception) {
            Timber.e(e, "Error in getCategoryContents")
            Result.failure(e)
        }
    }
    
    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> = coroutineScope {
        try {
            val plugins = dataSource.connectedPlugins.value
            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.getLibrarySongs(pluginId, cursor, limit)
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.mapNotNull { it.getOrNull() }.flatMap { it.items }
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
            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.getLibraryAlbums(pluginId, cursor, limit)
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.mapNotNull { it.getOrNull() }.flatMap { it.items }
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
            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.getLibraryArtists(pluginId, cursor, limit)
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.mapNotNull { it.getOrNull() }.flatMap { it.items }
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
            val results = plugins.keys.map { pluginId ->
                async {
                    dataSource.getLibraryPlaylists(pluginId, cursor, limit)
                }
            }.awaitAll()
            
            val merged = PagedResult(
                items = results.mapNotNull { it.getOrNull() }.flatMap { it.items }
            )
            
            Timber.d("getLibraryPlaylists() completed: ${merged.items.size} playlists from ${plugins.size} plugins")
            Result.success(merged)
        } catch (e: Exception) {
            Timber.e(e, "Error in getLibraryPlaylists()")
            Result.failure(e)
        }
    }
    
    override suspend fun getSong(mediaId: MediaId): Result<Song> {
        return dataSource.getSong(mediaId)
    }
    
    override suspend fun getAlbum(mediaId: MediaId): Result<Album> {
        return dataSource.getAlbum(mediaId)
    }
    
    override suspend fun getArtist(mediaId: MediaId): Result<Artist> {
        return dataSource.getArtist(mediaId)
    }
    
    override suspend fun getPlaylist(mediaId: MediaId): Result<Playlist> {
        return dataSource.getPlaylist(mediaId)
    }

    override suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return dataSource.getArtistSongs(artistId, cursor, limit)
    }

    override suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> {
        return dataSource.getArtistAlbums(artistId, cursor, limit)
    }

    override suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return dataSource.getPlaylistSongs(playlistId, cursor, limit)
    }
}

