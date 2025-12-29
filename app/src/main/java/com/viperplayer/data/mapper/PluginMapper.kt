package com.viperplayer.data.mapper

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CategoryContentType
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.sdk.v1.Album as AidlAlbum
import com.viperplayer.plugin.sdk.v1.AlbumType as AidlAlbumType
import com.viperplayer.plugin.sdk.v1.Artist as AidlArtist
import com.viperplayer.plugin.sdk.v1.BrowseCategory as AidlBrowseCategory
import com.viperplayer.plugin.sdk.v1.CategoryContentType as AidlCategoryContentType
import com.viperplayer.plugin.sdk.v1.MediaId as AidlMediaId
import com.viperplayer.plugin.sdk.v1.Playlist as AidlPlaylist
import com.viperplayer.plugin.sdk.v1.PluginCapabilities as AidlPluginCapabilities
import com.viperplayer.plugin.sdk.v1.PluginInfo as AidlPluginInfo
import com.viperplayer.plugin.sdk.v1.SearchResult as AidlSearchResult
import com.viperplayer.plugin.sdk.v1.Song as AidlSong

/**
 * Mappers to convert between AIDL models and domain models.
 */
object PluginMapper {
    
    fun AidlMediaId.toDomain(): MediaId = MediaId(pluginId, sourceId)
    
    fun MediaId.toAidl(): AidlMediaId = AidlMediaId(pluginId, sourceId)
    
    fun AidlArtist.toDomain(): Artist = Artist(
        id = id.toDomain(),
        name = name,
        imageUrl = imageUrl,
        genres = genres,
        followerCount = followerCount,
        bio = bio
    )
    
    fun AidlAlbumType.toDomain(): AlbumType = when (this) {
        AidlAlbumType.ALBUM -> AlbumType.ALBUM
        AidlAlbumType.SINGLE -> AlbumType.SINGLE
        AidlAlbumType.EP -> AlbumType.EP
        AidlAlbumType.COMPILATION -> AlbumType.COMPILATION
    }
    
    fun AidlAlbum.toDomain(): Album = Album(
        id = id.toDomain(),
        name = name,
        artists = artists.map { it.toDomain() },
        artworkUrl = artworkUrl,
        releaseYear = releaseYear,
        trackCount = trackCount,
        type = type.toDomain(),
        songs = songs?.map { it.toDomain() }
    )
    
    fun AidlSong.toDomain(): Song = Song(
        id = id.toDomain(),
        title = title,
        artists = artists.map { it.toDomain() },
        album = album?.toDomain(),
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        trackNumber = trackNumber,
        discNumber = discNumber,
        isExplicit = isExplicit,
        isPlayable = isPlayable
    )
    
    fun AidlPlaylist.toDomain(): Playlist = Playlist(
        id = id.toDomain(),
        name = name,
        description = description,
        artworkUrl = artworkUrl,
        ownerName = ownerName,
        songCount = songCount,
        isPublic = isPublic,
        isEditable = isEditable,
        songs = songs?.map { it.toDomain() }
    )
    
    fun AidlCategoryContentType.toDomain(): CategoryContentType = when (this) {
        AidlCategoryContentType.CATEGORIES -> CategoryContentType.CATEGORIES
        AidlCategoryContentType.PLAYLISTS -> CategoryContentType.PLAYLISTS
        AidlCategoryContentType.ALBUMS -> CategoryContentType.ALBUMS
        AidlCategoryContentType.ARTISTS -> CategoryContentType.ARTISTS
        AidlCategoryContentType.SONGS -> CategoryContentType.SONGS
        AidlCategoryContentType.MIXED -> CategoryContentType.MIXED
    }
    
    fun AidlBrowseCategory.toDomain(): BrowseCategory = BrowseCategory(
        id = id,
        pluginId = pluginId,
        name = name,
        description = description,
        imageUrl = imageUrl,
        contentType = contentType.toDomain()
    )
    
    fun AidlSearchResult.toDomain(): SearchResult = SearchResult(
        songs = songs.map { it.toDomain() },
        albums = albums.map { it.toDomain() },
        artists = artists.map { it.toDomain() },
        playlists = playlists.map { it.toDomain() },
        nextCursor = nextCursor
    )
    
    fun AidlPluginInfo.toDomain(): PluginInfo = PluginInfo(
        id = id,
        name = name,
        version = version,
        apiVersion = apiVersion,
        description = description,
        author = author,
        iconUrl = iconUrl,
    )
    
    fun AidlPluginCapabilities.toDomain(): PluginCapabilities = PluginCapabilities(
        canSearch = canSearch,
        canBrowse = canBrowse,
        hasLibrary = hasLibrary,
        hasPlaylists = hasPlaylists,
        canSeek = canSeek,
        hasLyrics = hasLyrics,
        hasHighQuality = hasHighQuality,
        supportsOffline = supportsOffline,
        hasSettings = hasSettings
    )
}

