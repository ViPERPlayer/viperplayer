package com.viperplayer.data.mapper

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CategoryContentType
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.v1.MediaItemV1
import com.viperplayer.plugin.v1.Album as AidlAlbum
import com.viperplayer.plugin.v1.Album.AlbumType as AidlAlbumType
import com.viperplayer.plugin.v1.Artist as AidlArtist
import com.viperplayer.plugin.v1.BrowseCategory as AidlBrowseCategory
import com.viperplayer.plugin.v1.BrowseCategory.CategoryContentType as AidlCategoryContentType
import com.viperplayer.plugin.v1.Playlist as AidlPlaylist
import com.viperplayer.plugin.v1.PluginCapabilities as AidlPluginCapabilities
import com.viperplayer.plugin.v1.PluginInfo as AidlPluginInfo
import com.viperplayer.plugin.v1.SearchResult as AidlSearchResult
import com.viperplayer.plugin.v1.Song as AidlSong

/**
 * Mappers to convert between AIDL models and domain models.
 */
object PluginMapper {

    fun MediaItemV1.toDomain(pluginId: String): MediaItem {
        return when (this.type) {
            MediaItemV1.Type.SONG -> this.song!!.toDomain(pluginId)
            MediaItemV1.Type.ALBUM -> this.album!!.toDomain(pluginId)
            MediaItemV1.Type.ARTIST -> this.artist!!.toDomain(pluginId)
            MediaItemV1.Type.PLAYLIST -> this.playlist!!.toDomain(pluginId)
            else -> throw IllegalArgumentException("Unknown MediaItem type: ${this.type}")
        }
    }

    fun String.toDomain(pluginId: String): MediaId = MediaId(pluginId, this)

    fun AidlArtist.toDomain(pluginId: String): Artist = Artist(
        id = id.toDomain(pluginId),
        name = name,
        imageUrl = artwork?.full,
        topSongs = topSongs?.map { it.toDomain(pluginId) }.orEmpty(),
        albums = albums?.map { it.toDomain(pluginId) }.orEmpty(),
        playlists = playlists?.map { it.toDomain(pluginId) }.orEmpty(),
        featuring = featuring?.map { it.toDomain(pluginId) }.orEmpty(),
        appearsOn = appearsOn?.map { it.toDomain(pluginId) }.orEmpty(),
        similarArtists = similarArtists?.map { it.toDomain(pluginId) }.orEmpty()
    )

    fun Byte.toDomainAlbumType(): AlbumType = when (this) {
        AidlAlbumType.ALBUM -> AlbumType.ALBUM
        AidlAlbumType.SINGLE -> AlbumType.SINGLE
        AidlAlbumType.EP -> AlbumType.EP
        AidlAlbumType.COMPILATION -> AlbumType.COMPILATION
        else -> AlbumType.ALBUM // Default fallback
    }

    fun AidlAlbum.toDomain(pluginId: String): Album = Album(
        id = id.toDomain(pluginId),
        name = name,
        artists = artists.map { it.toDomain(pluginId) },
        artworkUrl = artwork?.full,
        releaseYear = if (hasReleaseYear) releaseYear else null,
        trackCount = trackCount,
        type = type.toDomainAlbumType(),
        songs = songs?.mapIndexed { index, song -> song.toDomain(pluginId, index + 1) }
    )

    fun AidlSong.toDomain(
        pluginId: String,
        trackNumber: Int? = null,
    ): Song {
        return Song(
            id = this.id.toDomain(pluginId),
            title = this.title,
            artists = this.artists.map { it.toDomain(pluginId) },
            album = this.album?.toDomain(pluginId),
            durationMs = if (hasDurationMs) this.durationMs else null,
            artworkUrl = this.artwork?.full,
            trackNumber = if (hasTrackNumber) this.trackNumber else trackNumber,
            discNumber = if (hasDiscNumber) this.discNumber else null,
            isExplicit = this.isExplicit,
            isPlayable = this.isPlayable,
            requiresInternet = this.requiresInternet,
            replayGainDb = if (hasReplayGainDb) this.replayGainDb else null,
            peakAmplitude = if (hasPeakAmplitude) this.peakAmplitude else null
        )
    }

    fun AidlPlaylist.toDomain(pluginId: String): Playlist = Playlist(
        id = id.toDomain(pluginId),
        name = name,
        description = description,
        artworkUrl = artwork?.full,
        ownerName = ownerName,
        songCount = songCount,
        isPublic = true,
        isEditable = false,
        songs = songs?.map { it.toDomain(pluginId) }
    )

    fun Byte.toDomainCategoryContentType(): CategoryContentType = when (this) {
        AidlCategoryContentType.CATEGORIES -> CategoryContentType.CATEGORIES
        AidlCategoryContentType.PLAYLISTS -> CategoryContentType.PLAYLISTS
        AidlCategoryContentType.ALBUMS -> CategoryContentType.ALBUMS
        AidlCategoryContentType.ARTISTS -> CategoryContentType.ARTISTS
        AidlCategoryContentType.SONGS -> CategoryContentType.SONGS
        AidlCategoryContentType.MIXED -> CategoryContentType.MIXED
        else -> CategoryContentType.MIXED // Default fallback
    }

    fun AidlBrowseCategory.toDomain(): BrowseCategory = BrowseCategory(
        id = id,
        pluginId = pluginId,
        name = name,
        description = description,
        imageUrl = imageUrl,
        contentType = contentType.toDomainCategoryContentType()
    )

    fun AidlSearchResult.toDomain(pluginId: String): SearchResult {
        return SearchResult(
            items = items.map { it.toDomain(pluginId) },
            nextCursor = nextCursor
        )
    }

    fun AidlPluginInfo.toDomain(): PluginInfo = PluginInfo(
        id = id,
        name = name,
        version = version,
        apiVersion = apiVersion,
        description = description,
        author = author,
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

    fun com.viperplayer.plugin.v1.HomeContent.toDomain(pluginId: String): com.viperplayer.domain.model.HomeContent {
        return com.viperplayer.domain.model.HomeContent(
            quickPicks = quickPicks?.map { it.toDomain(pluginId) },
            sections = sections.map { it.toDomain(pluginId) }
        )
    }

    fun com.viperplayer.plugin.v1.HomeSection.toDomain(pluginId: String): com.viperplayer.domain.model.HomeSection {
        return com.viperplayer.domain.model.HomeSection(
            id = id,
            title = title,
            items = items.map { it.toDomain(pluginId) },
            layout = when (layout) {
                com.viperplayer.plugin.v1.HomeSection.Layout.LIST -> com.viperplayer.domain.model.HomeSection.Layout.LIST
                com.viperplayer.plugin.v1.HomeSection.Layout.GRID -> com.viperplayer.domain.model.HomeSection.Layout.GRID
                else -> throw IllegalArgumentException("Unknown HomeSection layout: $layout")
            }
        )
    }
}

