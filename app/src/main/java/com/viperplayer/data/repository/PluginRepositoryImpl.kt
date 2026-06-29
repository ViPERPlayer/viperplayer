package com.viperplayer.data.repository

import android.util.Base64
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
                        capabilities = connected.capabilities,
                        isConnected = true
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to map plugin to domain model")
                    null
                }
            }
        }

    override val disabledPlugins: Flow<Set<String>>
        get() = dataSource.disabledPlugins

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

            val perPlugin = decodeCursors(cursor)
            // First page: query every connected plugin. Load-more: only those that returned a cursor.
            val targets = if (cursor == null) plugins.keys else perPlugin.keys.filter { it in plugins.keys }
            val results = targets.map { pluginId ->
                async {
                    pluginId to dataSource.search(pluginId, query, filter, perPlugin[pluginId], limit).getOrNull()
                }
            }.awaitAll()

            val merged = mutableListOf<MediaItem>()
            val nextCursors = mutableMapOf<String, String>()
            results.forEach { (pluginId, searchResult) ->
                searchResult?.let {
                    merged += it.items
                    it.nextCursor?.let { c -> nextCursors[pluginId] = c }
                }
            }

            Result.success(SearchResult(items = merged, nextCursor = encodeCursors(nextCursors)))
        } catch (e: Exception) {
            Timber.e(e, "Error in search")
            Result.failure(e)
        }
    }

    override suspend fun getHomeContent(): Result<List<Pair<String, com.viperplayer.domain.model.HomeContent>>> =
        coroutineScope {
            try {
                val plugins = dataSource.connectedPlugins.value
                val results = plugins.keys.map { pluginId ->
                    async {
                        val result = dataSource.getHomeContent(pluginId)
                        result.map { pluginId to it }
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
    ): Result<PagedResult<BrowseCategory>> = runCatching {
        aggregatePaged(cursor) { pluginId, pluginCursor ->
            dataSource.getBrowseCategories(pluginId, pluginCursor, limit).getOrNull()
        }
    }

    override suspend fun getCategoryContents(
        pluginId: String,
        categoryId: String,
        cursor: String?,
        limit: Int
    ): Result<SearchResult> {
        return try {
            dataSource.getCategoryContents(pluginId, categoryId, cursor, limit)
        } catch (e: Exception) {
            Timber.e(e, "Error in getCategoryContents")
            Result.failure(e)
        }
    }

    override suspend fun filterSection(
        pluginId: String,
        sectionId: String,
        filterKey: String
    ): Result<SearchResult> {
        return try {
            dataSource.filterSection(pluginId, sectionId, filterKey)
        } catch (e: Exception) {
            Timber.e(e, "Error in filterSection")
            Result.failure(e)
        }
    }

    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> = runCatching {
        aggregatePaged(cursor) { pluginId, pluginCursor ->
            dataSource.getLibrarySongs(pluginId, pluginCursor, limit).getOrNull()
        }
    }

    override suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> = runCatching {
        aggregatePaged(cursor) { pluginId, pluginCursor ->
            dataSource.getLibraryAlbums(pluginId, pluginCursor, limit).getOrNull()
        }
    }

    override suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Artist>> = runCatching {
        aggregatePaged(cursor) { pluginId, pluginCursor ->
            dataSource.getLibraryArtists(pluginId, pluginCursor, limit).getOrNull()
        }
    }

    override suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Playlist>> = runCatching {
        aggregatePaged(cursor) { pluginId, pluginCursor ->
            dataSource.getLibraryPlaylists(pluginId, pluginCursor, limit).getOrNull()
        }
    }

    // ---- multi-plugin pagination ----
    // An aggregate cursor packs each plugin's own opaque cursor as base64(JSON); on "load more"
    // only the plugins that still have a cursor are re-queried, each with its own.
    private val cursorJson = Json { ignoreUnknownKeys = true }

    private fun decodeCursors(cursor: String?): Map<String, String> = cursor?.let {
        runCatching {
            cursorJson.decodeFromString<Map<String, String>>(
                String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8)
            )
        }.getOrNull()
    } ?: emptyMap()

    private fun encodeCursors(cursors: Map<String, String>): String? =
        cursors.takeIf { it.isNotEmpty() }?.let {
            Base64.encodeToString(cursorJson.encodeToString(it).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

    private suspend fun <T> aggregatePaged(
        cursor: String?,
        fetch: suspend (pluginId: String, pluginCursor: String?) -> PagedResult<T>?,
    ): PagedResult<T> = coroutineScope {
        val connected = dataSource.connectedPlugins.value.keys
        val perPlugin = decodeCursors(cursor)
        // First page: query every connected plugin. Load-more: only those that returned a cursor.
        val targets = if (cursor == null) connected else perPlugin.keys.filter { it in connected }
        val results = targets.map { pluginId ->
            async { pluginId to runCatching { fetch(pluginId, perPlugin[pluginId]) }.getOrNull() }
        }.awaitAll()
        val merged = mutableListOf<T>()
        val nextCursors = mutableMapOf<String, String>()
        results.forEach { (pluginId, page) ->
            page?.let {
                merged += it.items
                it.nextCursor?.let { c -> nextCursors[pluginId] = c }
            }
        }
        PagedResult(merged, encodeCursors(nextCursors))
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

    override suspend fun getLyrics(song: Song): Result<Lyrics?> {
        return dataSource.getLyrics(
            id = song.id,
            title = song.title,
            artist = song.artistNames,
            album = song.album?.name,
            durationMs = song.durationMs
        )
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

    override suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> {
        return dataSource.getRelatedSongs(songId)
    }
}

