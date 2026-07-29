package com.viperplayer.data.repository

import android.util.Base64
import com.viperplayer.data.lyrics.LrcLibLyricsProvider
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.BannerSection
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.FeaturedCardsSection
import com.viperplayer.domain.model.GridSection
import com.viperplayer.domain.model.HeroSection
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.ListSection
import com.viperplayer.domain.model.MoodGridSection
import com.viperplayer.domain.model.MultiBrowseCarouselSection
import com.viperplayer.domain.model.QuickPicksSection
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsCandidate
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginActionType
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.viperplayer.plugin.model.ActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val dataSource: PluginDataSource,
    private val settingsRepository: SettingsRepository,
    private val lrcLib: LrcLibLyricsProvider,
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

    override val pendingActions: Flow<List<PluginPendingAction>>
        get() = combine(dataSource.pluginActions, dataSource.connectedPlugins) { actions, connected ->
            actions.flatMap { (pluginId, pluginActions) ->
                val plugin = connected[pluginId] ?: return@flatMap emptyList()
                pluginActions.map { action ->
                    PluginPendingAction(
                        pluginId = pluginId,
                        pluginName = plugin.info.name,
                        actionId = action.id,
                        type = action.type.toDomain(),
                        title = action.title,
                        description = action.description,
                        activity = action.activity,
                        permission = action.permission,
                        settingsActivity = plugin.info.settingsActivity,
                    )
                }
            }
        }

    override suspend fun refreshPluginStatuses() {
        dataSource.refreshAllPluginStatuses()
    }

    private fun ActionType.toDomain(): PluginActionType = when (this) {
        ActionType.PERMISSION -> PluginActionType.PERMISSION
        ActionType.LOGIN -> PluginActionType.LOGIN
        ActionType.VERIFICATION -> PluginActionType.VERIFICATION
        ActionType.SETUP -> PluginActionType.SETUP
        ActionType.UNKNOWN -> PluginActionType.UNKNOWN
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

            Result.success(
                SearchResult(
                    items = merged.withoutExplicit(hideExplicit()),
                    nextCursor = encodeCursors(nextCursors),
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error in search")
            Result.failure(e)
        }
    }

    override suspend fun getHomeContent(): Result<List<Pair<String, HomeContent>>> =
        coroutineScope {
            try {
                val plugins = dataSource.connectedPlugins.value
                val results = plugins.keys.map { pluginId ->
                    async {
                        val result = dataSource.getHomeContent(pluginId)
                        result.map { pluginId to it }
                    }
                }.awaitAll()

                val hide = hideExplicit()
                val successfulResults = results.mapNotNull { it.getOrNull() }
                    .map { (pluginId, content) ->
                        pluginId to content.copy(
                            sections = content.sections.map { section -> section.withoutExplicit(hide) }
                        )
                    }
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

    override suspend fun getLibrarySongs(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> = dataSource.getLibrarySongs(pluginId, cursor, limit)

    override suspend fun getLibraryAlbums(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> = dataSource.getLibraryAlbums(pluginId, cursor, limit)

    override suspend fun getLibraryArtists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Artist>> = dataSource.getLibraryArtists(pluginId, cursor, limit)

    override suspend fun getLibraryPlaylists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Playlist>> = dataSource.getLibraryPlaylists(pluginId, cursor, limit)

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
        val hide = hideExplicit()
        return dataSource.getAlbum(mediaId).map { it.copy(songs = it.songs?.songsWithoutExplicit(hide)) }
    }

    override suspend fun getArtist(mediaId: MediaId): Result<ArtistDetail> {
        val hide = hideExplicit()
        return dataSource.getArtist(mediaId).map { it.copy(topSongs = it.topSongs.songsWithoutExplicit(hide)) }
    }

    override suspend fun getPlaylist(mediaId: MediaId): Result<Playlist> {
        val hide = hideExplicit()
        return dataSource.getPlaylist(mediaId).map { it.copy(songs = it.songs?.songsWithoutExplicit(hide)) }
    }

    override suspend fun getLyrics(song: Song): Result<Lyrics?> {
        val fromPlugin = dataSource.getLyrics(
            id = song.id,
            title = song.title,
            artist = song.artistNames,
            album = song.album?.name,
            durationMs = song.durationMs
        ).getOrNull()?.takeUnless { it.isEmpty }

        // A synced result from the source plugin (possibly word-level) is best. Otherwise fall back
        // to LRCLIB for line-synced lyrics, keeping any plugin plain text as a last resort.
        if (fromPlugin != null && fromPlugin.synced) return Result.success(fromPlugin)
        val fallback = runCatching { lrcLib.getLyrics(song) }
            .onFailure { Timber.e(it, "LRCLIB fallback threw for '${song.title}'") }
            .getOrNull()?.takeUnless { it.isEmpty }
        return Result.success(fallback?.takeIf { it.synced } ?: fromPlugin ?: fallback)
    }

    override suspend fun searchLyrics(title: String, artist: String?): List<LyricsCandidate> =
        runCatching { lrcLib.searchLyrics(title, artist) }
            .onFailure { Timber.e(it, "LRCLIB lyric-match search threw for '$title'") }
            .getOrDefault(emptyList())

    override suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        val hide = hideExplicit()
        return dataSource.getArtistSongs(artistId, cursor, limit)
            .map { it.copy(items = it.items.songsWithoutExplicit(hide)) }
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
        val hide = hideExplicit()
        return dataSource.getPlaylistSongs(playlistId, cursor, limit)
            .map { it.copy(items = it.items.songsWithoutExplicit(hide)) }
    }

    override suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> {
        return dataSource.getRelatedSongs(songId)
    }

    // --- Explicit-content filtering (Settings > Content > Show Explicit Content) ----------------
    // Browse/discovery surfaces only (search, home, detail tracklists). The play queue, the
    // related-songs feed, and the local library are intentionally NOT filtered here.
    /** True when explicit tracks should be hidden. */
    private suspend fun hideExplicit(): Boolean = !settingsRepository.showExplicitContent.first()

    private fun List<MediaItem>.withoutExplicit(hide: Boolean): List<MediaItem> =
        if (hide) filterNot { it is Song && it.isExplicit } else this

    private fun List<Song>.songsWithoutExplicit(hide: Boolean): List<Song> =
        if (hide) filterNot { it.isExplicit } else this

    private fun HomeSection.withoutExplicit(hide: Boolean): HomeSection = when (this) {
        is CarouselSection -> copy(items = items.withoutExplicit(hide))
        is GridSection -> copy(items = items.withoutExplicit(hide))
        is ListSection -> copy(items = items.withoutExplicit(hide))
        is MultiBrowseCarouselSection -> copy(items = items.withoutExplicit(hide))
        is QuickPicksSection -> copy(items = items.withoutExplicit(hide))
        is FeaturedCardsSection -> copy(items = items.withoutExplicit(hide))
        // MoodGridSection carries chips (no media items), so nothing to filter.
        is HeroSection, is BannerSection, is MoodGridSection -> this
    }
}

