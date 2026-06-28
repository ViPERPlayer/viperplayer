package com.viperplayer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.FilterState
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val sections: List<HomeSection> = emptyList(),
        val connectedPlugins: List<Plugin>,
        val isRefreshing: Boolean = false
    ) : HomeUiState

    data class Error(
        override val greetingType: GreetingType,
        override val userName: String?,
        val message: String
    ) : HomeUiState
}

/**
 * ViewModel for Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastConnectedPlugins: List<Plugin> = emptyList()

    // The content-load coroutine; cancel the previous one so overlapping loads (auto-update on every
    // connectedPlugins emission + refresh) can't let an older/slower response overwrite a newer one.
    private var loadJob: Job? = null

    init {
        onTimeChanged()
        observeConnectedPlugins()
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

    fun loadContent(isRefreshing: Boolean = false, fromAutoUpdate: Boolean = false) {
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

                // Load home content (Quick Picks & Custom Sections)
                val homeContentResult = pluginRepository.getHomeContent()
                val homeContentList = homeContentResult.getOrNull().orEmpty()

                // Merge all quickPicks and sections from all plugins
                val allQuickPicks = homeContentList.flatMap { (_, content) ->
                    content.quickPicks.orEmpty()
                }
                val allSections = homeContentList.flatMap { (_, content) ->
                    content.sections
                }

                _uiState.update { state ->
                    HomeUiState.Content(
                        greetingType = state.greetingType,
                        userName = state.userName,
                        categories = categories,
                        quickPicks = allQuickPicks.takeIf { it.isNotEmpty() },
                        sections = allSections,
                        connectedPlugins = lastConnectedPlugins,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    HomeUiState.Error(
                        greetingType = state.greetingType,
                        userName = state.userName,
                        message = e.message ?: "Failed to load content"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadContent(isRefreshing = true)
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

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    val context = PlaybackContext.Search
                    if (index != -1) {
                        playerRepository.playAll(songs, index, context)
                    } else {
                        playerRepository.play(song, context)
                    }
                } else {
                    val context = PlaybackContext.Search
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                // Handle error silently
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
        _uiState.update { state ->
            if (state !is HomeUiState.Content) return@update state
            state.copy(
                sections = state.sections.map { s ->
                    if (s.id == sectionId && s is CarouselSection) transform(s) else s
                },
            )
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

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    val context = PlaybackContext.Search
                    if (index != -1) {
                        playerRepository.playAll(songs, index, context)
                    } else {
                        playerRepository.play(song, context)
                    }
                } else {
                    val context = PlaybackContext.Search
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
}

enum class GreetingType {
    NIGHT, MORNING, AFTERNOON, EVENING
}
