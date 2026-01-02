package com.viperplayer.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain models - These are the core business entities used throughout the app.
 * They are independent of any framework or external data source.
 */

/**
 * Unique identifier for media items across plugins.
 */
@Parcelize
data class MediaId(
    val pluginId: String,
    val sourceId: String
) : Parcelable {
    override fun toString(): String {
        val encodedPluginId = Uri.encode(pluginId)
        val encodedSourceId = Uri.encode(sourceId)
        return "pluginId=$encodedPluginId&sourceId=$encodedSourceId"
    }

    companion object {
        fun fromString(string: String): MediaId {
            val params = string.split("&").associate {
                val (key, value) = it.split("=")
                key to Uri.decode(value)
            }
            val pluginId = params["pluginId"] ?: error("Missing pluginId in MediaId")
            val sourceId = params["sourceId"] ?: error("Missing sourceId in MediaId")
            return MediaId(pluginId, sourceId)
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
 * Playback information (state, settings, queue info).
 * Does NOT include position, currentSong, or duration - these are separate.
 * Use this for UI that only needs playback state and settings.
 */
data class PlaybackInfo(
    val state: PlaybackState = PlaybackState.IDLE,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val queueSize: Int = 0,
    val queuePosition: Int = 0
) {
    val isPlaying: Boolean get() = state == PlaybackState.BUFFERING || state == PlaybackState.PLAYING
    val isPaused: Boolean get() = state == PlaybackState.PAUSED
}

/**
 * Current player state (legacy - combines all fields).
 * For better performance, use separate flows: playbackState, currentSong, position, duration.
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

