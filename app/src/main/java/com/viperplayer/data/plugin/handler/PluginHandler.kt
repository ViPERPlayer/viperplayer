package com.viperplayer.data.plugin.handler

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.v1.HomeContent
import com.viperplayer.plugin.v1.PluginCapabilities
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import com.viperplayer.plugin.v1.StreamSource
import com.viperplayer.plugin.v1.SearchResult as AidlSearchResult

/**
 * Abstract interface for plugin operations.
 * Each API version will have its own implementation.
 * This allows us to support multiple plugin API versions simultaneously.
 */
interface PluginHandler {
    /**
     * The API version this handler supports.
     */
    val apiVersion: Int
    
    /**
     * Get plugin capabilities (what features it supports).
     */
    fun getCapabilities(): Result<PluginCapabilities>
    
    /**
     * Get search suggestions from the plugin.
     */
    suspend fun getSearchSuggestions(query: String): Result<SearchSuggestionsResultV1>
    
    /**
     * Search in the plugin.
     */
    suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): AidlSearchResult
    
    /**
     * Get browse categories from the plugin.
     */
    suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int
    ): PagedResult<BrowseCategory>
    
    /**
     * Get category contents.
     */
    suspend fun getCategoryContents(
        categoryId: String,
        cursor: String?,
        limit: Int
    ): AidlSearchResult
    
    /**
     * Get library songs from the plugin.
     */
    suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): PagedResult<Song>
    
    /**
     * Get library albums from the plugin.
     */
    suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int
    ): PagedResult<Album>
    
    /**
     * Get library artists from the plugin.
     */
    suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int
    ): PagedResult<Artist>
    
    /**
     * Get library playlists from the plugin.
     */
    suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int
    ): PagedResult<Playlist>
    
    /**
     * Get song details.
     */
    suspend fun getSong(id: String): Song
    
    /**
     * Get album details with tracks.
     */
    suspend fun getAlbum(id: String): Album
    
    /**
     * Get artist details.
     */
    suspend fun getArtist(id: String): Artist
    
    /**
     * Get playlist details with tracks.
     */
    suspend fun getPlaylist(id: String): Playlist
    
    /**
     * Get artist songs.
     */
    suspend fun getArtistSongs(
        artistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Song>
    
    /**
     * Get artist albums.
     */
    suspend fun getArtistAlbums(
        artistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Album>
    
    /**
     * Get playlist songs.
     */
    suspend fun getPlaylistSongs(
        playlistId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Song>
    
    /**
     * Get a stream source for playback.
     * Can return a URL, DASH XML, or AudioStream based on what the plugin supports.
     */
    suspend fun getStream(mediaId: String): StreamSource
    
    /**
     * Get content for the home screen.
     */
    suspend fun getHomeSections(): HomeContent
    
    /**
     * Get the settings activity class name from the plugin.
     */
    fun getSettingsActivityClass(): String?
    
    /**
     * Disconnect from the plugin.
     * Called when the host disconnects from the plugin.
     */
    suspend fun disconnect()
}

