package com.viperplayer.data.mapper

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CategoryContentType
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.model.Artwork
import com.viperplayer.plugin.model.Capability
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.PluginManifest
import com.viperplayer.plugin.model.Page
import com.viperplayer.plugin.model.Album as SdkAlbum
import com.viperplayer.plugin.model.AlbumType as SdkAlbumType
import com.viperplayer.plugin.model.Artist as SdkArtist
import com.viperplayer.plugin.model.BrowseCategory as SdkBrowseCategory
import com.viperplayer.plugin.model.CategoryContentType as SdkCategoryContentType
import com.viperplayer.plugin.model.HomeContent as SdkHomeContent
import com.viperplayer.plugin.model.HomeSection as SdkHomeSection
import com.viperplayer.plugin.model.MediaItem as SdkMediaItem
import com.viperplayer.plugin.model.UnknownMediaItem
import com.viperplayer.plugin.model.Playlist as SdkPlaylist
import com.viperplayer.plugin.model.SearchResult as SdkSearchResult
import com.viperplayer.plugin.model.SearchSuggestions as SdkSearchSuggestions
import com.viperplayer.plugin.model.SectionLayout as SdkSectionLayout
import com.viperplayer.plugin.model.Song as SdkSong

/**
 * Converts plugin-SDK models (opaque per-plugin ids, real nullables) into host domain models
 * (globally-unique [MediaId], host-specific fields). All ids are namespaced with the producing
 * plugin's id here.
 */
object PluginMapper {

    private fun Artwork?.bestUrl(): String? = this?.fullUrl ?: this?.thumbnailUrl

    fun SearchFilter.toMediaType(): MediaType = when (this) {
        SearchFilter.SONG -> MediaType.SONG
        SearchFilter.ALBUM -> MediaType.ALBUM
        SearchFilter.ARTIST -> MediaType.ARTIST
        SearchFilter.PLAYLIST -> MediaType.PLAYLIST
    }

    fun SdkSong.toDomain(pluginId: String): Song = Song(
        id = MediaId(pluginId, id),
        title = title,
        artists = artists.map { it.toDomain(pluginId) },
        album = album?.toDomain(pluginId),
        durationMs = durationMs,
        artworkUrl = artwork.bestUrl(),
        trackNumber = trackNumber,
        discNumber = discNumber,
        isExplicit = isExplicit,
        isPlayable = isPlayable,
        requiresInternet = requiresInternet,
        replayGainDb = replayGainDb,
        peakAmplitude = peakAmplitude,
    )

    fun SdkAlbum.toDomain(pluginId: String): Album = Album(
        id = MediaId(pluginId, id),
        name = name,
        artists = artists.map { it.toDomain(pluginId) },
        artworkUrl = artwork.bestUrl(),
        releaseYear = releaseYear,
        trackCount = trackCount ?: 0,
        type = type.toDomain(),
        songs = songs.takeIf { it.isNotEmpty() }?.map { it.toDomain(pluginId) },
    )

    fun SdkArtist.toDomain(pluginId: String): Artist = Artist(
        id = MediaId(pluginId, id),
        name = name,
        imageUrl = artwork.bestUrl(),
        topSongs = topSongs.map { it.toDomain(pluginId) },
        albums = (albums + singles).map { it.toDomain(pluginId) },
        playlists = playlists.map { it.toDomain(pluginId) },
        featuring = emptyList(),
        appearsOn = emptyList(),
        similarArtists = similarArtists.map { it.toDomain(pluginId) },
    )

    fun SdkPlaylist.toDomain(pluginId: String): Playlist = Playlist(
        id = MediaId(pluginId, id),
        name = name,
        description = description,
        artworkUrl = artwork.bestUrl(),
        ownerName = ownerName,
        songCount = trackCount ?: 0,
        isPublic = true,
        isEditable = isEditable,
        songs = songs.takeIf { it.isNotEmpty() }?.map { it.toDomain(pluginId) },
    )

    fun SdkMediaItem.toDomain(pluginId: String): MediaItem? = when (this) {
        is SdkSong -> toDomain(pluginId)
        is SdkAlbum -> toDomain(pluginId)
        is SdkArtist -> toDomain(pluginId)
        is SdkPlaylist -> toDomain(pluginId)
        is UnknownMediaItem -> null // a media kind this host doesn't understand; skip it
    }

    fun SdkSearchResult.toDomain(pluginId: String): SearchResult = SearchResult(
        items = items.mapNotNull { it.toDomain(pluginId) },
        nextCursor = nextCursor,
    )

    fun SdkSearchSuggestions.toDomain(pluginId: String): SearchSuggestions = SearchSuggestions(
        pluginId = pluginId,
        suggestions = suggestions,
        items = items.mapNotNull { it.toDomain(pluginId) },
    )

    fun SdkBrowseCategory.toDomain(pluginId: String): BrowseCategory = BrowseCategory(
        id = id,
        pluginId = pluginId,
        name = name,
        description = description,
        imageUrl = imageUrl,
        contentType = contentType.toDomain(),
    )

    fun SdkHomeContent.toDomain(pluginId: String): HomeContent = HomeContent(
        quickPicks = quickPicks.takeIf { it.isNotEmpty() }?.mapNotNull { it.toDomain(pluginId) },
        sections = sections.map { it.toDomain(pluginId) },
    )

    fun SdkHomeSection.toDomain(pluginId: String): HomeSection = HomeSection(
        id = id,
        title = title,
        items = items.mapNotNull { it.toDomain(pluginId) },
        layout = when (layout) {
            SdkSectionLayout.LIST -> HomeSection.Layout.LIST
            SdkSectionLayout.GRID -> HomeSection.Layout.GRID
        },
    )

    fun <T, R> Page<T>.toDomain(transform: (T) -> R): PagedResult<R> =
        PagedResult(items.map(transform), nextCursor, totalCount)

    fun PluginManifest.toDomainInfo(): PluginInfo = PluginInfo(
        id = id,
        name = name,
        version = version,
        apiVersion = protocolVersion,
        description = description,
        author = author,
        settingsActivity = settingsActivity,
    )

    fun PluginManifest.toDomainCapabilities(): PluginCapabilities = PluginCapabilities(
        canSearch = source?.search ?: false,
        canBrowse = source?.browse ?: false,
        hasLibrary = source?.library ?: false,
        hasPlaylists = source?.playlists ?: false,
        canSeek = source?.canSeek ?: false,
        hasLyrics = Capability.LYRICS in capabilities,
        hasHighQuality = false,
        supportsOffline = source?.supportsOffline ?: false,
        hasSettings = settingsActivity != null,
    )

    private fun SdkAlbumType.toDomain(): AlbumType = when (this) {
        SdkAlbumType.ALBUM -> AlbumType.ALBUM
        SdkAlbumType.SINGLE -> AlbumType.SINGLE
        SdkAlbumType.EP -> AlbumType.EP
        SdkAlbumType.COMPILATION -> AlbumType.COMPILATION
    }

    private fun SdkCategoryContentType.toDomain(): CategoryContentType = when (this) {
        SdkCategoryContentType.CATEGORIES -> CategoryContentType.CATEGORIES
        SdkCategoryContentType.PLAYLISTS -> CategoryContentType.PLAYLISTS
        SdkCategoryContentType.ALBUMS -> CategoryContentType.ALBUMS
        SdkCategoryContentType.ARTISTS -> CategoryContentType.ARTISTS
        SdkCategoryContentType.SONGS -> CategoryContentType.SONGS
        SdkCategoryContentType.MIXED -> CategoryContentType.MIXED
    }
}
