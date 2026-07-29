package com.viperplayer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.viperplayer.R
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.BannerSection
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.MoodGridSection
import com.viperplayer.domain.model.FilterState
import com.viperplayer.domain.model.HomeChip
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.HomeSectionOrder
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.FriendsRepository
import com.viperplayer.domain.social.SharedPlaylistsRepository
import com.viperplayer.domain.social.SocialFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

/**
 * UI State for Home screen.
 */
sealed interface HomeUiState {
    val greetingType: GreetingType
    val userName: String?

    data class Loading(
        override val greetingType: GreetingType = GreetingType.MORNING,
        override val userName: String? = null
    ) : HomeUiState

    data class Content(
        override val greetingType: GreetingType,
        override val userName: String?,
        val categories: List<BrowseCategory>,
        val quickPicks: List<MediaItem>? = null,
        /** The sections in the order to render (already shuffled when the setting is on). */
        val sections: List<HomeSection> = emptyList(),
        val connectedPlugins: List<Plugin>,
        val isRefreshing: Boolean = false,
        /** Top-level filter chips (merged across plugins); empty = no chip row. */
        val chips: List<HomeChip> = emptyList(),
        /** The currently-selected chip id, or null for the base feed. */
        val selectedChipId: String? = null,
        /** More sections can be fetched (infinite scroll) — true when any plugin has a continuation. */
        val canLoadMore: Boolean = false,
        /** A "load more" page fetch is in flight (drives an appended spinner / prevents re-trigger). */
        val isLoadingMore: Boolean = false,
    ) : HomeUiState

    data class Error(
        override val greetingType: GreetingType,
        override val userName: String?,
        val message: String
    ) : HomeUiState
}

/**
 * State for the Home top bar's avatar + notification bell. Signed-in shows initials + a live ring when
 * friends are listening; signed-out shows the generic person glyph. [notificationCount] drives the bell
 * badge (social unread when the backend is configured, else 0).
 */
data class HomeTopBarState(
    val signedIn: Boolean = false,
    val initials: String = "",
    val friendsLive: Boolean = false,
    val notificationCount: Int = 0,
)

/**
 * ViewModel for Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    accountRepository: AccountRepository,
    friendsRepository: FriendsRepository,
    friendActivityRepository: FriendActivityRepository,
    sharedPlaylistsRepository: SharedPlaylistsRepository,
    socialFeatures: SocialFeatures,
    private val settingsRepository: SettingsRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Session seed for the randomized section order. Stable within a session so the order doesn't
     * jump between reloads; re-rolled only on an explicit [refresh].
     */
    private var randomSeed: Long = System.nanoTime()

    /**
     * The merged, UN-ordered sections from the last successful load (base + any appended pages),
     * kept so the display order can be recomputed (on a setting change) without re-fetching.
     */
    private var baseSections: List<HomeSection> = emptyList()

    /** Per-plugin infinite-scroll continuation token from the latest page; absent = that plugin is done. */
    private var continuations: Map<String, String> = emptyMap()

    /** Latest value of the randomize-home-order setting, applied when (re)ordering sections. */
    private var randomizeHomeOrder: Boolean = true

    /** Whether offline mode is on, so Home can surface an unobtrusive "Offline" banner. */
    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // The currently-playing track + play state, so home item cards can show the now-playing indicator.
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val socialEnabled = socialFeatures.enabled

    /** Avatar + bell state for the top bar, folded from account + (backend-gated) social flows. */
    val topBarState: StateFlow<HomeTopBarState> = combine(
        accountRepository.state,
        friendsRepository.liveJams,
        friendActivityRepository.activity,
        sharedPlaylistsRepository.unreadCount,
    ) { account, liveJams, activity, sharedUnread ->
        val name = account.user?.displayName?.takeIf { it.isNotBlank() }
            ?: account.user?.email.orEmpty()
        val friendsLive = socialEnabled &&
            (liveJams.isNotEmpty() || activity.any { it is FriendActivityItem.ListeningNow })
        // The bell = notifications feed; count the unread social signals when the backend is on.
        val notifications = if (socialEnabled) {
            sharedUnread + activity.count { it is FriendActivityItem.ListeningNow }
        } else {
            0
        }
        HomeTopBarState(
            signedIn = account.isSignedIn,
            initials = name.firstOrNull()?.uppercase().orEmpty(),
            friendsLive = friendsLive,
            notificationCount = notifications,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeTopBarState())

    private var lastConnectedPlugins: List<Plugin> = emptyList()

    // The content-load coroutine; cancel the previous one so overlapping loads (auto-update on every
    // connectedPlugins emission + refresh) can't let an older/slower response overwrite a newer one.
    private var loadJob: Job? = null

    init {
        onTimeChanged()
        observeConnectedPlugins()
        observeRandomizeSetting()
    }

    /**
     * Track the randomize-home-order setting. When it flips, re-order the already-loaded sections in
     * place (no re-fetch) so the toggle takes effect immediately.
     */
    private fun observeRandomizeSetting() {
        viewModelScope.launch {
            settingsRepository.randomizeHomeOrder.collect { enabled ->
                randomizeHomeOrder = enabled
                _uiState.update { state ->
                    if (state is HomeUiState.Content) {
                        state.copy(sections = HomeSectionOrder.order(baseSections, enabled, randomSeed))
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun observeConnectedPlugins() {
        viewModelScope.launch {
            pluginRepository.connectedPlugins.collect { plugins ->
                lastConnectedPlugins = plugins
                // Automatically reload content when plugins change
                loadContent(fromAutoUpdate = true)
            }
        }
    }

    fun loadContent(
        isRefreshing: Boolean = false,
        fromAutoUpdate: Boolean = false,
        chipId: String? = (_uiState.value as? HomeUiState.Content)?.selectedChipId,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { state ->
                when (state) {
                    is HomeUiState.Loading -> state // Keep loading
                    is HomeUiState.Content -> {
                        if (isRefreshing) {
                            state.copy(isRefreshing = true, connectedPlugins = lastConnectedPlugins)
                        } else if (fromAutoUpdate) {
                            state.copy(
                                isRefreshing = false,
                                connectedPlugins = lastConnectedPlugins
                            )
                        } else {
                            // If just loading (neither refresh nor auto-update), show loading? 
                            // Usually loadContent is called explicitly.
                            // If we already have content, maybe just keep it.
                            state.copy(connectedPlugins = lastConnectedPlugins)
                        }
                    }

                    is HomeUiState.Error -> {
                        if (isRefreshing) {
                            // Switch to loading or keep error with indicator?
                            // Better to switch to loading if we were in error
                            HomeUiState.Loading(state.greetingType, state.userName)
                        } else {
                            state
                        }
                    }
                }
            }

            // If we are not refreshing and not in content state, set loading (unless auto-update should be silent)
            if (!isRefreshing && !fromAutoUpdate && _uiState.value !is HomeUiState.Content) {
                _uiState.update { state ->
                    HomeUiState.Loading(state.greetingType, state.userName)
                }
            }

            try {
                // Load categories
                val categoriesResult = pluginRepository.getBrowseCategories(limit = 10)
                val categories = categoriesResult.getOrNull()?.items.orEmpty()

                // Load home content (Quick Picks & Custom Sections), optionally filtered by a chip.
                val homeContentResult = pluginRepository.getHomeContent(chipId)
                val homeContentList = homeContentResult.getOrNull().orEmpty()

                // Merge all quickPicks and sections from all plugins
                val allQuickPicks = homeContentList.flatMap { (_, content) ->
                    content.quickPicks.orEmpty()
                }
                // De-dup by (pluginId, id): two plugins (or one plugin repeating an id) must never yield
                // two sections that map to the same LazyColumn key — that throws at composition and crashes
                // the whole Home feed. Also drop empty sections so an item-less section can't render a
                // dangling header.
                val seenSectionKeys = HashSet<Pair<String, String>>()
                val allSections = homeContentList
                    .flatMap { (_, content) -> content.sections }
                    .filter { seenSectionKeys.add(it.pluginId to it.id) }
                    .filter { it.hasRenderableContent() }
                // Merge top-level chips across plugins (de-duped by id, first title wins).
                val allChips = homeContentList
                    .flatMap { (_, content) -> content.chips }
                    .distinctBy { it.id }
                // Track each plugin's continuation token for infinite scroll (drop plugins with none).
                continuations = homeContentList.mapNotNull { (pluginId, content) ->
                    content.continuation?.let { pluginId to it }
                }.toMap()

                baseSections = allSections
                _uiState.update { state ->
                    HomeUiState.Content(
                        greetingType = state.greetingType,
                        userName = state.userName,
                        categories = categories,
                        quickPicks = allQuickPicks.takeIf { it.isNotEmpty() },
                        sections = HomeSectionOrder.order(allSections, randomizeHomeOrder, randomSeed),
                        connectedPlugins = lastConnectedPlugins,
                        isRefreshing = false,
                        chips = allChips,
                        selectedChipId = chipId,
                        canLoadMore = continuations.isNotEmpty(),
                        isLoadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    HomeUiState.Error(
                        greetingType = state.greetingType,
                        userName = state.userName,
                        message = e.message ?: stringProvider.getString(R.string.home_error_load_content)
                    )
                }
            }
        }
    }

    /**
     * Pull-to-refresh: re-roll the shuffle seed (so the section order changes) and re-fetch the feed,
     * keeping the currently-selected chip.
     */
    fun refresh() {
        randomSeed = System.nanoTime()
        loadContent(isRefreshing = true)
    }

    /**
     * A top-level chip was tapped (or cleared with a null [chipId]). Re-request the feed filtered by
     * it; plugins that don't support chip filtering gracefully return their base feed. Ignored if the
     * same chip is already selected.
     */
    fun onChipSelected(chipId: String?) {
        val state = _uiState.value
        if (state is HomeUiState.Content && state.selectedChipId == chipId) return
        loadContent(chipId = chipId)
    }

    /**
     * Infinite scroll: fetch the next page of sections from every plugin that has a continuation
     * token, append them (de-duped by section id), and advance each plugin's token. No-op when
     * nothing more is available or a page fetch is already in flight.
     */
    fun loadMore() {
        val current = _uiState.value
        if (current !is HomeUiState.Content) return
        if (current.isLoadingMore || continuations.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { s -> (s as? HomeUiState.Content)?.copy(isLoadingMore = true) ?: s }

            val pages: List<Pair<String, HomeContent>> = continuations.entries.mapNotNull { (pluginId, token) ->
                pluginRepository.getHomeContinuation(pluginId, token).getOrNull()?.let { pluginId to it }
            }

            // Advance tokens: a plugin whose page had a null continuation is done and drops out.
            continuations = pages.mapNotNull { (pluginId, content) ->
                content.continuation?.let { pluginId to it }
            }.toMap()

            val newSections = pages.flatMap { (_, content) -> content.sections }
            // Append, de-duping by (pluginId, id) so a repeated section can't stack up or collide on its
            // LazyColumn key, and dropping empty sections (no dangling headers).
            val existingKeys = baseSections.mapTo(HashSet()) { it.pluginId to it.id }
            baseSections = baseSections +
                newSections.filter { existingKeys.add(it.pluginId to it.id) && it.hasRenderableContent() }

            _uiState.update { s ->
                (s as? HomeUiState.Content)?.copy(
                    sections = HomeSectionOrder.order(baseSections, randomizeHomeOrder, randomSeed),
                    canLoadMore = continuations.isNotEmpty(),
                    isLoadingMore = false,
                ) ?: s
            }
        }
    }

    /**
     * Whether a section has content worth rendering. An item-bearing section with no items (or a mood
     * grid with no chips) would otherwise emit a section header with nothing under it. Banners are
     * text/image promos with no media items by design, so they always render.
     */
    private fun HomeSection.hasRenderableContent(): Boolean = when (this) {
        is MoodGridSection -> chips.isNotEmpty()
        is BannerSection -> true
        else -> items.isNotEmpty()
    }

    fun onTimeChanged() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val greetingType = when (hour) {
            in 5..11 -> GreetingType.MORNING
            in 12..16 -> GreetingType.AFTERNOON
            in 17..20 -> GreetingType.EVENING
            else -> GreetingType.NIGHT
        }

        _uiState.update { state ->
            when (state) {
                is HomeUiState.Loading -> state.copy(greetingType = greetingType)
                is HomeUiState.Content -> state.copy(greetingType = greetingType)
                is HomeUiState.Error -> state.copy(greetingType = greetingType)
            }
        }
    }


    fun playSongFromQuickPicks(song: Song) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is HomeUiState.Content) return@launch

                // Get all songs from quickPicks
                val songs = state.quickPicks?.filterIsInstance<Song>().orEmpty()

                // Quick picks are recommendations, so play them under the Suggestions context.
                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    if (index != -1) {
                        playerRepository.playAll(songs, index, PlaybackContext.Suggestions)
                    } else {
                        playerRepository.play(song, PlaybackContext.Suggestions)
                    }
                } else {
                    playerRepository.play(song, PlaybackContext.Suggestions)
                }
            } catch (e: Exception) {
                Timber.w(e, "HomeViewModel background operation failed")
            }
        }
    }

    /**
     * A filter chip on [section] was tapped: ask the owning plugin for fresh items and, on success,
     * swap them into that section (keeping its title/rows/chips) and mark the tapped chip selected.
     * Only [CarouselSection]s carry filters. Failures are ignored so a bad tap can't break the feed.
     */
    fun onSectionFilterSelected(section: HomeSection, filterKey: String) {
        viewModelScope.launch {
            // 1. Optimistic: select the tapped chip and show a spinner in this section, immediately.
            updateCarousel(section.id) {
                it.copy(
                    filters = it.filters.map { f -> f.copy(selected = f.key == filterKey) },
                    filterState = FilterState.Loading,
                )
            }
            // 2. Fetch this section's items for the chosen filter from its owning plugin.
            // 3. Apply to this section alone — new items on success, an in-section error otherwise.
            pluginRepository.filterSection(section.pluginId, section.id, filterKey).fold(
                onSuccess = { res ->
                    updateCarousel(section.id) { it.copy(items = res.items, filterState = FilterState.Idle) }
                },
                onFailure = { e ->
                    Timber.w(e, "filterSection failed for ${section.id} ($filterKey)")
                    updateCarousel(section.id) { it.copy(filterState = FilterState.Error) }
                },
            )
        }
    }

    /** Replace one carousel section (by id) in the current Content state via [transform]. */
    private fun updateCarousel(sectionId: String, transform: (CarouselSection) -> CarouselSection) {
        val patch: (HomeSection) -> HomeSection = { s ->
            if (s.id == sectionId && s is CarouselSection) transform(s) else s
        }
        // Keep the un-ordered base list in sync so a later re-order (setting toggle) preserves the edit.
        baseSections = baseSections.map(patch)
        _uiState.update { state ->
            if (state !is HomeUiState.Content) return@update state
            state.copy(sections = state.sections.map(patch))
        }
    }

    fun playSongFromSection(song: Song, sectionId: String) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is HomeUiState.Content) return@launch

                // Find the specific section and get only its songs
                val section = state.sections.find { it.id == sectionId }
                val songs = section?.items?.filterIsInstance<Song>().orEmpty()

                // Home sections have no specific origin entity; leave the context generic.
                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    if (index != -1) {
                        playerRepository.playAll(songs, index)
                    } else {
                        playerRepository.play(song)
                    }
                } else {
                    playerRepository.play(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "HomeViewModel background operation failed")
            }
        }
    }
}

enum class GreetingType {
    NIGHT, MORNING, AFTERNOON, EVENING
}
