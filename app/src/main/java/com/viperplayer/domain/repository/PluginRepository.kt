package com.viperplayer.domain.repository

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
import com.viperplayer.plugin.v1.SearchFilter
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for plugin operations.
 * Defines the contract for interacting with music plugins.
 */
interface PluginRepository {

    /**
     * Flow of all discovered plugins (connected and disconnected).
     */
    val discoveredPlugins: Flow<List<PluginInfo>>

    /**
     * Flow of connected plugins.
     */
    val connectedPlugins: Flow<List<Plugin>>

    /**
     * Discover all installed plugins.
     */
    suspend fun refreshPlugins()

    /**
     * Enable a plugin (connects it automatically).
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit>

    /**
     * Disable a plugin (disconnects it).
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit>

    /**
     * Gets search suggestions across all connected plugins.
     */
    suspend fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>>

    /**
     * Search across all connected plugins.
     */
    suspend fun search(
        query: String,
        filter: SearchFilter? = null,
        cursor: String? = null,
        limit: Int = 20
    ): Result<SearchResult>

    /**
     * Get home content from all connected plugins.
     * Returns a list of pairs: (PluginName, HomeContent)
     */
    suspend fun getHomeContent(): Result<List<Pair<String, com.viperplayer.domain.model.HomeContent>>>

    /**
     * Get browse categories from all plugins.
     */
    suspend fun getBrowseCategories(
        cursor: String? = null,
        limit: Int = 20
    ): Result<PagedResult<BrowseCategory>>

    /**
     * Get category contents.
     */
    suspend fun getCategoryContents(
        pluginId: String,
        categoryId: String,
        cursor: String? = null,
        limit: Int = 20
    ): Result<SearchResult>

    /**
     * Get library songs from all plugins.
     */
    suspend fun getLibrarySongs(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>>

    /**
     * Get library albums from all plugins.
     */
    suspend fun getLibraryAlbums(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Album>>

    /**
     * Get library artists from all plugins.
     */
    suspend fun getLibraryArtists(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Artist>>

    /**
     * Get library playlists from all plugins.
     */
    suspend fun getLibraryPlaylists(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Playlist>>

    /**
     * Get song details.
     */
    suspend fun getSong(mediaId: MediaId): Result<Song>

    /**
     * Get album details with tracks.
     */
    suspend fun getAlbum(mediaId: MediaId): Result<Album>

    /**
     * Get artist details.
     */
    suspend fun getArtist(mediaId: MediaId): Result<Artist>

    /**
     * Get playlist details with tracks.
     */
    suspend fun getPlaylist(mediaId: MediaId): Result<Playlist>

    /**
     * Get artist songs.
     */
    suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>>

    /**
     * Get artist albums.
     */
    suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Album>>

    /**
     * Get playlist songs.
     */
    suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>>

    companion object {
        const val SEARCH_TYPE_SONG = 1
        const val SEARCH_TYPE_ALBUM = 2
        const val SEARCH_TYPE_ARTIST = 4
        const val SEARCH_TYPE_PLAYLIST = 8
        const val SEARCH_TYPE_ALL =
            SEARCH_TYPE_SONG or SEARCH_TYPE_ALBUM or SEARCH_TYPE_ARTIST or SEARCH_TYPE_PLAYLIST
    }
}

