package com.viperplayer.data.mapper

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CategoryContentType
import com.viperplayer.domain.model.BannerSection
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.GridSection
import com.viperplayer.domain.model.HeroSection
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.ItemShape
import com.viperplayer.domain.model.ListSection
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SectionAction
import com.viperplayer.domain.model.SectionFilter
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
import com.viperplayer.plugin.model.Lyrics as SdkLyrics
import com.viperplayer.plugin.model.MediaItem as SdkMediaItem
import com.viperplayer.plugin.model.PlayableItem as SdkPlayableItem
import com.viperplayer.plugin.model.UnknownMediaItem
import com.viperplayer.plugin.model.UnknownPlayableItem
import com.viperplayer.plugin.model.Playlist as SdkPlaylist
import com.viperplayer.plugin.model.SearchResult as SdkSearchResult
import com.viperplayer.plugin.model.SearchSuggestions as SdkSearchSuggestions
import com.viperplayer.plugin.model.BannerSection as SdkBannerSection
import com.viperplayer.plugin.model.CarouselSection as SdkCarouselSection
import com.viperplayer.plugin.model.GridSection as SdkGridSection
import com.viperplayer.plugin.model.HeroSection as SdkHeroSection
import com.viperplayer.plugin.model.ItemShape as SdkItemShape
import com.viperplayer.plugin.model.ListSection as SdkListSection
import com.viperplayer.plugin.model.SectionAction as SdkSectionAction
import com.viperplayer.plugin.model.SectionFilter as SdkSectionFilter
import com.viperplayer.plugin.model.UnknownSection as SdkUnknownSection
import com.viperplayer.plugin.model.Song as SdkSong
import com.viperplayer.plugin.model.Video as SdkVideo

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
        artists = artists.map { it.toArtistRef(pluginId) },
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
        isVideo = false,
    )

    fun SdkVideo.toDomain(pluginId: String): Song = Song(
        id = MediaId(pluginId, id),
        title = title,
        artists = artists.map { it.toArtistRef(pluginId) },
        album = album?.toDomain(pluginId),
        durationMs = durationMs,
        artworkUrl = artwork.bestUrl(),
        trackNumber = trackNumber,
        discNumber = discNumber,
        isExplicit = isExplicit,
        isPlayable = isPlayable,
        requiresInternet = true,
        replayGainDb = replayGainDb,
        peakAmplitude = peakAmplitude,
        isVideo = true,
    )

    /** A playable (song or video) inside a mixed list -> domain Song; unknown kinds are skipped. */
    fun SdkPlayableItem.toDomainSong(pluginId: String): Song? = when (this) {
        is SdkSong -> toDomain(pluginId)
        is SdkVideo -> toDomain(pluginId)
        is UnknownPlayableItem -> null
    }

    fun SdkAlbum.toDomain(pluginId: String): Album = Album(
        id = MediaId(pluginId, id),
        name = name,
        artists = artists.map { it.toArtistRef(pluginId) },
        artworkUrl = artwork.bestUrl(),
        releaseYear = releaseYear,
        trackCount = trackCount ?: 0,
        type = type.toDomain(),
        songs = songs.takeIf { it.isNotEmpty() }?.mapNotNull { it.toDomainSong(pluginId) },
    )

    /** A byline credit (inside a Song/Album). Unlinked artists carry a null [ArtistRef.id]. */
    fun SdkArtist.toArtistRef(pluginId: String): ArtistRef = ArtistRef(
        name = name,
        id = MediaId.of(pluginId, id),
    )

    /**
     * A top-level entity artist (search result, similar artist, library artist) — always navigable,
     * so it must carry a real id. A blank id here is an unlinked artist that should not be surfaced
     * as an entity; callers use [toArtistRef] instead. Returns null so such an artist is skipped.
     */
    fun SdkArtist.toDomainRef(pluginId: String): Artist? = MediaId.of(pluginId, id)?.let { mediaId ->
        Artist(
            id = mediaId,
            name = name,
            imageUrl = artwork.bestUrl(),
        )
    }

    fun SdkArtist.toDomainDetail(pluginId: String): ArtistDetail = ArtistDetail(
        id = MediaId(pluginId, id),
        name = name,
        imageUrl = artwork.bestUrl(),
        topSongs = topSongs.mapNotNull { it.toDomainSong(pluginId) },
        albums = (albums + singles).map { it.toDomain(pluginId) },
        playlists = playlists.map { it.toDomain(pluginId) },
        featuring = emptyList(),
        appearsOn = emptyList(),
        similarArtists = similarArtists.mapNotNull { it.toDomainRef(pluginId) },
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
        songs = songs.takeIf { it.isNotEmpty() }?.mapNotNull { it.toDomainSong(pluginId) },
    )

    fun SdkLyrics.toDomain(): Lyrics = Lyrics(
        synced = synced,
        lines = lines.map { LyricsLine(startMs = it.startMs, text = it.text) },
        plainText = plainText,
    )

    fun SdkMediaItem.toDomain(pluginId: String): MediaItem? = when (this) {
        is SdkSong -> toDomain(pluginId)
        is SdkVideo -> toDomain(pluginId)
        is SdkAlbum -> toDomain(pluginId)
        is SdkArtist -> toDomainRef(pluginId)  // entity artist; unlinked (blank id) -> null, skipped
        is SdkPlaylist -> toDomain(pluginId)
        is UnknownMediaItem -> null // a media kind this host doesn't understand; skip it
        is UnknownPlayableItem -> null // an unknown playable kind; skip it
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
        sections = sections.mapNotNull { it.toDomain(pluginId) },
    )

    /** Maps a section design 1:1; an unknown design (or a hero whose single item can't map) is dropped. */
    fun SdkHomeSection.toDomain(pluginId: String): HomeSection? = when (this) {
        is SdkCarouselSection -> CarouselSection(
            id = id, title = title, subtitle = subtitle, action = action?.toDomain(),
            pluginId = pluginId,
            items = items.mapNotNull { it.toDomain(pluginId) }, itemShape = itemShape.toDomain(),
            rows = rows, filters = filters.map { it.toDomain() },
        )

        is SdkGridSection -> GridSection(
            id = id, title = title, subtitle = subtitle, action = action?.toDomain(),
            pluginId = pluginId,
            items = items.mapNotNull { it.toDomain(pluginId) }, columns = columns,
            itemShape = itemShape.toDomain(),
        )

        is SdkListSection -> ListSection(
            id = id, title = title, subtitle = subtitle, action = action?.toDomain(),
            pluginId = pluginId,
            items = items.mapNotNull { it.toDomain(pluginId) },
        )

        is SdkHeroSection -> item.toDomain(pluginId)?.let { domainItem ->
            HeroSection(
                id = id, title = title, subtitle = subtitle, action = action?.toDomain(),
                pluginId = pluginId,
                item = domainItem, backgroundImageUrl = backgroundImageUrl, description = description,
            )
        }

        is SdkBannerSection -> BannerSection(
            id = id, title = title, subtitle = subtitle, action = action?.toDomain(),
            pluginId = pluginId,
            text = text, imageUrl = imageUrl,
        )

        is SdkUnknownSection -> null
    }

    private fun SdkSectionAction.toDomain(): SectionAction = SectionAction(label = label, targetId = targetId)

    private fun SdkSectionFilter.toDomain(): SectionFilter = SectionFilter(label = label, key = key, selected = selected)

    private fun SdkItemShape.toDomain(): ItemShape = when (this) {
        SdkItemShape.SQUARE -> ItemShape.SQUARE
        SdkItemShape.CIRCLE -> ItemShape.CIRCLE
        SdkItemShape.WIDE -> ItemShape.WIDE
    }

    fun <T, R> Page<T>.toDomain(transform: (T) -> R): PagedResult<R> =
        PagedResult(items.map(transform), nextCursor, totalCount)

    /** Like [toDomain] but drops items the transform can't map (returns null). */
    fun <T, R : Any> Page<T>.toDomainNotNull(transform: (T) -> R?): PagedResult<R> =
        PagedResult(items.mapNotNull(transform), nextCursor, totalCount)

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
