package com.viperplayer.presentation.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.data.repository.NetworkConnectivityChecker
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Genre
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.model.resolveVisibleTabIds
import com.viperplayer.domain.repository.AutoPlaylistRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.sort.MediaSorter
import com.viperplayer.follows.data.FollowedArtistsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Library tabs. This is the canonical full set of tabs the app ships. Each carries the string resource
 * for its label, so both the Library TabRow and the "Customize tabs" screen render the same localized
 * name. The enum [name] is the stable id persisted in [com.viperplayer.domain.model.LibraryTabsConfig];
 * the reconciliation between a stored config and this set is pure — see [resolveVisibleTabIds].
 */
enum class LibraryTab(@param:StringRes val labelRes: Int) {
    SONGS(R.string.library_tab_songs),
    ALBUMS(R.string.library_tab_albums),
    ARTISTS(R.string.library_tab_artists),
    GENRES(R.string.library_tab_genres),
    PLAYLISTS(R.string.library_tab_playlists);

    companion object {
        /** The full set of tab ids (enum names) in default order, for [resolveVisibleTabIds]. */
        val ALL_IDS: List<String> = entries.map { it.name }

        /** Resolve a persisted id back to a [LibraryTab], or `null` if it names a removed/unknown tab. */
        fun fromId(id: String): LibraryTab? = entries.firstOrNull { it.name == id }
    }
}

/**
 * Resolve a [LibraryTabsConfig] into the ordered list of visible [LibraryTab]s. Thin presentation-layer
 * adapter over the pure [resolveVisibleTabIds]: maps ids back to enum values (dropping any that no longer
 * resolve) and guarantees a non-empty result (falls back to [LibraryTab.SONGS]).
 */
fun resolveVisibleTabs(config: LibraryTabsConfig): List<LibraryTab> {
    val tabs = resolveVisibleTabIds(LibraryTab.ALL_IDS, config).mapNotNull { LibraryTab.fromId(it) }
    return tabs.ifEmpty { listOf(LibraryTab.SONGS) }
}

/**
 * UI State for Library screen.
 *
 * The `songs`/`albums`/`artists`/`playlists` lists are already sorted for display per the matching
 * `sortOrder`; the ViewModel keeps the raw (default-ordered) lists privately so re-sorting on a menu
 * change never needs a reload.
 */
data class LibraryUiState(
    /**
     * The selected filter chip, or `null` for the default **unified feed** (no chip selected): all
     * library items — songs, albums, artists and playlists — interleaved and ordered by
     * most-recently-played. Tapping a chip selects that type; tapping the selected chip again returns
     * here (`null`). Chips are toggleable and start unselected.
     */
    val selectedTab: LibraryTab? = null,
    /**
     * The tabs to show, in the user's configured order, already reconciled against the tabs the app
     * ships (hidden tabs omitted, new tabs appended, all-hidden guarded). Defaults to every tab in the
     * natural order so an un-customized library is unchanged. Never empty.
     */
    val visibleTabs: List<LibraryTab> = LibraryTab.entries.toList(),
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    /** Local library genres (name + song count), for the Genres tab. Ordered by name from the DAO. */
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /**
     * The unified all-types feed shown when [selectedTab] is `null`: songs/albums/artists/playlists
     * interleaved, sorted by most-recently-played (newest first). Built by [buildUnifiedRecencyFeed]
     * from the four raw lists plus the listening history — never re-sorted by a per-type sort menu.
     */
    val unified: List<LibraryFeedItem> = emptyList(),
    /**
     * Dynamic auto-playlists (Recently Added / Most Played / …), computed live from the library and
     * play-history. Shown as their own section above the regular playlists and never re-sorted by the
     * playlist sort menu, so they keep their stable, meaningful order.
     */
    val autoPlaylists: List<Playlist> = emptyList(),
    val songsSort: SortOrder = SortOrder.DEFAULT,
    val albumsSort: SortOrder = SortOrder.DEFAULT,
    val artistsSort: SortOrder = SortOrder.DEFAULT,
    val playlistsSort: SortOrder = SortOrder.DEFAULT,
    val error: String? = null
)

/**
 * Live counts for the pinned Library shortcut tiles (Liked / Downloads / Following). Derived from the
 * library + follows repositories and exposed separately from [LibraryUiState] so the tiles update
 * reactively without coupling to the tab content load.
 */
data class LibraryShortcutCounts(
    val liked: Int = 0,
    val downloaded: Int = 0,
    val following: Int = 0,
)

/** One-shot result of an M3U import, surfaced as a Toast by the screen. */
sealed interface ImportEvent {
    data class Success(val imported: Int, val skipped: Int) : ImportEvent
    data object Failure : ImportEvent
}

/**
 * ViewModel for Library screen.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val followedArtistsRepository: FollowedArtistsRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
    private val networkConnectivityChecker: NetworkConnectivityChecker,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Raw (default-ordered) lists as loaded, kept so a sort-menu change re-sorts without a reload and
    // so the unified feed can be rebuilt from all four types without a per-type reload.
    private var rawSongs: List<Song> = emptyList()
    private var rawAlbums: List<Album> = emptyList()
    private var rawArtists: List<Artist> = emptyList()
    private var rawPlaylists: List<Playlist> = emptyList()

    // Newest-first listening history (the recency signal for the unified feed). Kept up to date by a
    // perpetual observer so the unified order tracks new plays without a reload.
    private var rawHistory: List<HistoryEntry> = emptyList()

    // The latest persisted sort order per tab, updated on every collector emission (even before the
    // first content load completes). Reading these — rather than the possibly-still-DEFAULT UI state —
    // when a load finishes prevents a cold-start one-frame flip from DEFAULT to the persisted order.
    private var currentSongsSort: SortOrder = SortOrder.DEFAULT
    private var currentAlbumsSort: SortOrder = SortOrder.DEFAULT
    private var currentArtistsSort: SortOrder = SortOrder.DEFAULT
    private var currentPlaylistsSort: SortOrder = SortOrder.DEFAULT

    private val _importEvents = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 1)
    val importEvents: SharedFlow<ImportEvent> = _importEvents.asSharedFlow()

    /**
     * Live counts for the pinned shortcut tiles (Liked / Downloads / Following), combined from the
     * library + follows repositories. Kept separate from [uiState] so the tiles react to library
     * changes without a tab reload. [SharingStarted.WhileSubscribed] tears the underlying DB queries
     * down when the screen is off-screen.
     */
    val shortcutCounts: StateFlow<LibraryShortcutCounts> = combine(
        mediaLibraryRepository.likedSongCount(),
        mediaLibraryRepository.downloadedSongCount(),
        followedArtistsRepository.count(),
    ) { liked, downloaded, following ->
        LibraryShortcutCounts(liked = liked, downloaded = downloaded, following = following)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryShortcutCounts(),
    )

    // Expose current song and playing state from player repository
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // The current tab's load coroutine — some tabs collect perpetual flows, so cancel the previous
    // one before each reload, otherwise re-selecting a tab / refreshing leaks a collector per call.
    private var loadJob: Job? = null

    init {
        // Seed the persisted sort orders BEFORE the first content load so the first Success state is
        // already correctly ordered (no DEFAULT-then-flip), then let the tabs-config observer drive the
        // FIRST content load for the resolved initial tab. Running the seed and starting the observer in
        // one coroutine guarantees the prefetch completes before that first load; the per-tab observers
        // below keep re-sorting on later changes. The config observer — not a hardcoded loadContent(SONGS)
        // in init — owns the cold-start load, so the right tab loads exactly once (no redundant SONGS load
        // that would otherwise be immediately cancelled when the initial tab isn't SONGS).
        viewModelScope.launch {
            seedSortOrders()
            observeTabsConfig()
        }
        observeSortOrders()
        observeAutoPlaylists()
        observeHistory()
    }

    /**
     * Perpetual collector of the listening history — the recency signal behind the unified feed. When
     * the history changes (a new play is recorded) it re-derives the recency order in place, so the
     * default all-types feed stays current without a reload. No-op for the ordering while a specific
     * tab is selected (the feed isn't shown), but the raw history is still kept fresh for when the user
     * deselects back to it.
     */
    private fun observeHistory() {
        viewModelScope.launch {
            mediaLibraryRepository.getHistory().collect { history ->
                rawHistory = history
                if (_uiState.value.selectedTab == null) rebuildUnifiedFeed()
            }
        }
    }

    /** Recompute the unified recency feed from the current raw lists + history and publish it. */
    private fun rebuildUnifiedFeed() {
        _uiState.update {
            it.copy(
                isLoading = false,
                unified = buildUnifiedRecencyFeed(
                    songs = rawSongs,
                    albums = rawAlbums,
                    artists = rawArtists,
                    playlists = rawPlaylists,
                    history = rawHistory,
                )
            )
        }
    }

    /**
     * Collect the persisted library-tabs config, reconcile it into the ordered visible [LibraryTab]s,
     * keep the [LibraryUiState.selectedTab] valid, and drive content loading. On the FIRST emission this
     * loads the default **unified feed** (no chip selected — `selectedTab == null`) exactly once (the
     * cold-start load). On later emissions, if a currently-selected non-null tab was just hidden (or
     * removed), it deselects back to the unified feed. The unified default is always valid regardless of
     * which chips are visible, so `null` is never reconciled away. The perpetual collector keeps the
     * chip row in sync as the user edits the config on the Customize-tabs screen.
     */
    private suspend fun observeTabsConfig() {
        var firstEmission = true
        settingsRepository.libraryTabsConfig.collect { config ->
            val visible = resolveVisibleTabs(config)
            // Reconcile against the CURRENT selection atomically inside update(): a selected tab that
            // was just hidden/removed deselects back to the unified feed (null). Reading selectedTab
            // from the update's own `it` (not a pre-read snapshot) avoids clobbering a concurrent
            // selectTab(). `deselected` records when we fell back so the cold path can reload the feed.
            var deselected = false
            _uiState.update {
                val current = it.selectedTab
                val next = if (current == null || current in visible) current else null
                deselected = next == null && current != null
                it.copy(visibleTabs = visible, selectedTab = next)
            }
            // Cold start: load the default (unified) content exactly once. Afterwards, only reload when
            // the selection was just deselected because its tab is no longer visible.
            if (firstEmission) {
                firstEmission = false
                loadContent(_uiState.value.selectedTab)
            } else if (deselected) {
                loadContent(null)
            }
        }
    }

    /**
     * Collect the dynamic auto-playlists (computed live from library + play-history) into the UI
     * state. Perpetual collector started once — the flow re-emits whenever the library or history
     * changes, keeping the Playlists tab's auto section current without a reload.
     */
    private fun observeAutoPlaylists() {
        viewModelScope.launch {
            autoPlaylistRepository.getAutoPlaylists().collect { autoPlaylists ->
                _uiState.update { it.copy(autoPlaylists = autoPlaylists) }
            }
        }
    }

    /**
     * Prefetch each tab's persisted [SortOrder] once and record it in the `currentXSort` fields (and in
     * the UI state, so a menu already reflects the persisted order on first frame). Uses `first()` so
     * the initial value is available synchronously to the subsequent [loadContent], avoiding a cold-start
     * flip where the list renders DEFAULT then jumps to the persisted order.
     */
    private suspend fun seedSortOrders() {
        val seed = seedLibrarySortOrders(settingsRepository)
        currentSongsSort = seed.songs
        currentAlbumsSort = seed.albums
        currentArtistsSort = seed.artists
        currentPlaylistsSort = seed.playlists
        _uiState.update {
            it.copy(
                songsSort = seed.songs,
                albumsSort = seed.albums,
                artistsSort = seed.artists,
                playlistsSort = seed.playlists,
            )
        }
    }

    /**
     * Observe each tab's persisted [SortOrder] and re-sort its already-loaded raw list whenever the
     * user picks a new order (or on first load). Sorting the raw list here — rather than reloading —
     * keeps a menu change instant and avoids a network round-trip. Each collector also updates the
     * matching `currentXSort` field so a load finishing later applies the freshest order.
     */
    private fun observeSortOrders() {
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.LIBRARY_SONGS).collect { order ->
                currentSongsSort = order
                _uiState.update { it.copy(songsSort = order, songs = MediaSorter.sortSongs(rawSongs, order)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.LIBRARY_ALBUMS).collect { order ->
                currentAlbumsSort = order
                _uiState.update { it.copy(albumsSort = order, albums = MediaSorter.sortAlbums(rawAlbums, order)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.LIBRARY_ARTISTS).collect { order ->
                currentArtistsSort = order
                _uiState.update { it.copy(artistsSort = order, artists = MediaSorter.sortArtists(rawArtists, order)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.LIBRARY_PLAYLISTS).collect { order ->
                currentPlaylistsSort = order
                _uiState.update { it.copy(playlistsSort = order, playlists = MediaSorter.sortPlaylists(rawPlaylists, order)) }
            }
        }
    }

    /** Persist a new [order] for [view]; the sort-order observer applies it to the visible list. */
    fun setSortOrder(view: SortView, order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(view, order)
        }
    }

    /**
     * Toggle a filter chip. Selecting an unselected chip filters to that type; tapping the currently
     * selected chip again **deselects** it, returning to the default unified all-types recency feed.
     * Chips are toggleable and there is always a valid state (a selected type, or the unified feed).
     */
    fun selectTab(tab: LibraryTab) {
        val next = if (_uiState.value.selectedTab == tab) null else tab
        _uiState.update { it.copy(selectedTab = next) }
        loadContent(next)
    }

    /**
     * Load the content for [tab], or the unified all-types recency feed when [tab] is `null` (no chip
     * selected — the default). The unified path loads all four types concurrently and rebuilds the
     * recency-ordered feed as each arrives; a single-type path loads just that type's list. Cancels any
     * in-flight load first so re-selecting / refreshing never leaks a perpetual collector.
     */
    private fun loadContent(tab: LibraryTab?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                when (tab) {
                    // Unified default: load every type concurrently, each rebuilding the recency feed as
                    // it arrives. The songs/playlists loaders collect perpetual flows, so they must run
                    // as independent children rather than sequentially.
                    null -> {
                        // Keep isLoading = true until the first loader publishes and rebuildUnifiedFeed
                        // runs; clearing it here would flash the empty state for a frame on cold start.
                        launch { loadSongs(rebuildUnified = true) }
                        launch { loadAlbums(rebuildUnified = true) }
                        launch { loadArtists(rebuildUnified = true) }
                        launch { loadPlaylists(rebuildUnified = true) }
                    }
                    LibraryTab.SONGS -> loadSongs(rebuildUnified = false)
                    LibraryTab.ALBUMS -> loadAlbums(rebuildUnified = false)
                    LibraryTab.ARTISTS -> loadArtists(rebuildUnified = false)
                    LibraryTab.GENRES -> loadGenres()
                    LibraryTab.PLAYLISTS -> loadPlaylists(rebuildUnified = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load library"
                    )
                }
            }
        }
    }

    /**
     * Load the library songs (with reactive playability) into [LibraryUiState.songs]. When
     * [rebuildUnified] is true it also feeds the unified recency feed as new emissions arrive rather
     * than updating the per-type list. Collects a perpetual flow — runs until its coroutine is cancelled.
     */
    private suspend fun loadSongs(rebuildUnified: Boolean) {
        try {
            val result = pluginRepository.getLibrarySongs(limit = 50)
            val songs = result.getOrNull()?.items.orEmpty()

            // Update playability based on plugin connection, download status, and internet availability.
            // Combine with connected plugins and internet availability flows to make it reactive.
            combine(
                flowOf(songs),
                pluginRepository.connectedPlugins,
                networkConnectivityChecker.isInternetAvailable
            ) { songsList, connectedPlugins, isInternetAvailable ->
                val connectedPluginIds = connectedPlugins.map { it.info.id }.toSet()

                songsList.map { song ->
                    val isPluginConnected = song.id.routingPluginId in connectedPluginIds
                    // Song is playable if:
                    // 1. Song is downloaded (can play offline), OR
                    // 2. Plugin is connected AND (song doesn't require internet OR internet is available)
                    val isPlayable = song.isDownloaded ||
                            (isPluginConnected && (!song.requiresInternet || isInternetAvailable))
                    song.copy(isPlayable = isPlayable)
                }
            }.collect { songsWithPlayability ->
                rawSongs = songsWithPlayability
                if (rebuildUnified) {
                    rebuildUnifiedFeed()
                } else {
                    // Sort with the seeded/current order (not the possibly-stale state value)
                    // so the first Success frame already reflects the persisted order.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            songs = MediaSorter.sortSongs(songsWithPlayability, currentSongsSort)
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!rebuildUnified) reportLoadError(e)
        }
    }

    /** Load the library albums into [LibraryUiState.albums] (or the unified feed). */
    private suspend fun loadAlbums(rebuildUnified: Boolean) {
        try {
            val result = pluginRepository.getLibraryAlbums(limit = 50)
            rawAlbums = result.getOrNull()?.items.orEmpty()
            if (rebuildUnified) {
                rebuildUnifiedFeed()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        albums = MediaSorter.sortAlbums(rawAlbums, currentAlbumsSort)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!rebuildUnified) reportLoadError(e)
        }
    }

    /** Load the library artists into [LibraryUiState.artists] (or the unified feed). */
    private suspend fun loadArtists(rebuildUnified: Boolean) {
        try {
            val result = pluginRepository.getLibraryArtists(limit = 50)
            rawArtists = result.getOrNull()?.items.orEmpty()
            if (rebuildUnified) {
                rebuildUnifiedFeed()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        artists = MediaSorter.sortArtists(rawArtists, currentArtistsSort)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!rebuildUnified) reportLoadError(e)
        }
    }

    /** Load the library playlists (plugin + reactive local) into [LibraryUiState.playlists] (or feed). */
    private suspend fun loadPlaylists(rebuildUnified: Boolean) {
        try {
            // Load plugin playlists once (they are network-backed and don't change locally).
            val result = pluginRepository.getLibraryPlaylists(limit = 50)
            val pluginPlaylists = result.getOrNull()?.items.orEmpty()

            // Observe the local (Room-backed) playlists reactively: the virtual "Liked Songs" list plus
            // the user-created local playlists. This makes newly created playlists (e.g. from "Add to
            // playlist -> New playlist") appear immediately and be openable/editable in PlaylistDetailScreen.
            combine(
                mediaLibraryRepository.getLikedSongsPlaylist(),
                mediaLibraryRepository.getLocalPlaylists()
            ) { likedSongsPlaylist, localPlaylists ->
                // Order: Liked Songs (if any) -> user local playlists -> plugin playlists.
                buildList {
                    if (likedSongsPlaylist.songCount > 0) add(likedSongsPlaylist)
                    addAll(localPlaylists)
                    addAll(pluginPlaylists)
                }
            }.collect { allPlaylists ->
                rawPlaylists = allPlaylists
                if (rebuildUnified) {
                    rebuildUnifiedFeed()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playlists = MediaSorter.sortPlaylists(allPlaylists, currentPlaylistsSort)
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!rebuildUnified) reportLoadError(e)
        }
    }

    /**
     * Load the local library genres (name + song count) into [LibraryUiState.genres]. Genres are a
     * local-DB browse index (populated from saved songs' genre tags), not a plugin list and not part of
     * the unified recency feed, so this collects the reactive DAO flow directly. Runs until cancelled.
     */
    private suspend fun loadGenres() {
        try {
            mediaLibraryRepository.getGenres().collect { genres ->
                _uiState.update { it.copy(isLoading = false, genres = genres) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportLoadError(e)
        }
    }

    /** Surface a per-type load failure to the UI (clears the loading spinner). */
    private fun reportLoadError(e: Exception) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = e.message ?: "Failed to load library"
            )
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            playerRepository.play(song)
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            playerRepository.playNext(song)
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            playerRepository.addToQueue(song)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            mediaLibraryRepository.saveSong(song)
            val currentLiked = mediaLibraryRepository.getSong(song.id).first()?.isLiked ?: false
            mediaLibraryRepository.setSongLiked(song.id, !currentLiked)
        }
    }

    fun downloadSong(song: Song) {
        downloadManager.enqueue(song)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            // Load album songs and play
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            }
        }
    }

    fun shuffleAlbum(album: Album) {
        viewModelScope.launch {
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    playerRepository.playAll(shuffled, 0)
                }
            }
        }
    }

    fun addAlbumToQueue(album: Album) {
        viewModelScope.launch {
            val albumWithSongs = mediaLibraryRepository.getAlbum(album.id).first()
            albumWithSongs?.songs?.forEach { song ->
                playerRepository.addToQueue(song)
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0)
                }
            }
        }
    }

    fun shufflePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.let { songs ->
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    playerRepository.playAll(shuffled, 0)
                }
            }
        }
    }

    fun playPlaylistNext(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.forEach { song ->
                playerRepository.playNext(song)
            }
        }
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        viewModelScope.launch {
            val playlistWithSongs = mediaLibraryRepository.getPlaylist(playlist.id).first()
            playlistWithSongs?.songs?.forEach { song ->
                playerRepository.addToQueue(song)
            }
        }
    }

    fun togglePlaylistLike(playlist: Playlist) {
        viewModelScope.launch {
            val currentSaved = mediaLibraryRepository.isPlaylistSaved(playlist.id)
            mediaLibraryRepository.setPlaylistSaved(playlist.id, !currentSaved)
        }
    }

    fun refresh() {
        loadContent(_uiState.value.selectedTab)
    }

    /**
     * Read the M3U document at the SAF [uri], resolve its entries, and persist them as a new local
     * playlist. Emits an [ImportEvent] with the result. The playlist is named after the file.
     */
    fun importPlaylist(uri: Uri) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: return@withContext null
                    val name = queryDisplayName(uri)?.substringBeforeLast('.')
                        ?: context.getString(R.string.default_imported_playlist_name)
                    mediaLibraryRepository.importPlaylistFromM3u(name, content)
                }
            } catch (e: Exception) {
                null
            }
            if (result != null) {
                _importEvents.tryEmit(ImportEvent.Success(result.imported, result.skipped))
                // Surface the new playlist immediately if the user is on the Playlists tab.
                if (_uiState.value.selectedTab == LibraryTab.PLAYLISTS) {
                    loadContent(LibraryTab.PLAYLISTS)
                }
            } else {
                _importEvents.tryEmit(ImportEvent.Failure)
            }
        }
    }

    /** Best-effort display name for a SAF document [uri] (used to name the imported playlist). */
    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}

