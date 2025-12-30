package com.viperplayer.domain.model

/**
 * Domain models - These are the core business entities used throughout the app.
 * They are independent of any framework or external data source.
 */

/**
 * Unique identifier for media items across plugins.
 */
data class MediaId(
    val pluginId: String,
    val sourceId: String
) {
    override fun toString(): String = "$pluginId:$sourceId"
    
    companion object {
        fun fromString(value: String): MediaId {
            val parts = value.split(":", limit = 2)
            require(parts.size == 2) { "Invalid MediaId format: $value" }
            return MediaId(parts[0], parts[1])
        }
    }
}

/**
 * Represents an artist.
 */
data class Artist(
    val id: MediaId,
    val name: String,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val followerCount: Long? = null,
    val bio: String? = null
)

/**
 * Album type.
 */
enum class AlbumType {
    ALBUM, SINGLE, EP, COMPILATION
}

/**
 * Represents an album.
 */
data class Album(
    val id: MediaId,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val trackCount: Int = 0,
    val type: AlbumType = AlbumType.ALBUM,
    val songs: List<Song>? = null
) {
    val artistName: String
        get() = artists.firstOrNull()?.name ?: "Unknown Artist"
}

/**
 * Represents a song/track.
 */
data class Song(
    val id: MediaId,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long? = 0,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isExplicit: Boolean = false,
    val isPlayable: Boolean = true
) {
    val artistName: String
        get() = artists.firstOrNull()?.name ?: "Unknown Artist"
    
    val albumName: String
        get() = album?.name ?: ""
    
    val effectiveArtworkUrl: String?
        get() = artworkUrl ?: album?.artworkUrl
}

/**
 * Represents a playlist.
 */
data class Playlist(
    val id: MediaId,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val ownerName: String? = null,
    val songCount: Int = 0,
    val isPublic: Boolean = true,
    val isEditable: Boolean = false,
    val songs: List<Song>? = null
)

/**
 * Category content type.
 */
enum class CategoryContentType {
    CATEGORIES, PLAYLISTS, ALBUMS, ARTISTS, SONGS, MIXED
}

/**
 * Represents a browsable category.
 */
data class BrowseCategory(
    val id: String,
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val contentType: CategoryContentType = CategoryContentType.MIXED
)

/**
 * Search results containing multiple types.
 */
data class SearchResult(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val nextCursor: String? = null
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
    
    val hasMore: Boolean
        get() = nextCursor != null
}

/**
 * Paginated result.
 */
data class PagedResult<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalCount: Int? = null
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = items.isEmpty()
    
    companion object {
        fun <T> empty(): PagedResult<T> = PagedResult(emptyList())
    }
}

/**
 * Plugin information.
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int?,
    val description: String? = null,
    val author: String? = null,
    val iconUrl: String? = null
)

/**
 * Plugin capabilities.
 */
data class PluginCapabilities(
    val canSearch: Boolean = true,
    val canBrowse: Boolean = true,
    val hasLibrary: Boolean = true,
    val hasPlaylists: Boolean = true,
    val canSeek: Boolean = true,
    val hasLyrics: Boolean = false,
    val hasHighQuality: Boolean = false,
    val supportsOffline: Boolean = false,
    val hasSettings: Boolean = false
)

/**
 * Connected plugin with its info and capabilities.
 */
data class Plugin(
    val info: PluginInfo,
    val capabilities: PluginCapabilities,
    val isConnected: Boolean = true
)

/**
 * Playback state.
 */
enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, STOPPED, ERROR
}

/**
 * Repeat mode.
 */
enum class RepeatMode {
    OFF, ONE, ALL
}

/**
 * Current player state.
 */
data class PlayerState(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentSong: Song? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val queueSize: Int = 0,
    val queuePosition: Int = 0
) {
    val isPlaying: Boolean get() = state == PlaybackState.PLAYING
    val isPaused: Boolean get() = state == PlaybackState.PAUSED
    val hasContent: Boolean get() = currentSong != null
}

