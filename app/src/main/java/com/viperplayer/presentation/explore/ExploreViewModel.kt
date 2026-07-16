package com.viperplayer.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for the Explore (discovery) screen.
 */
sealed interface ExploreUiState {
    data object Loading : ExploreUiState

    data class Content(
        val content: ExploreContent,
        val hasPlugins: Boolean,
        val isRefreshing: Boolean = false,
    ) : ExploreUiState

    data class Error(val message: String) : ExploreUiState
}

/**
 * Drives the Explore screen: pulls each connected plugin's discovery data (home feed sections +
 * browse categories) via [PluginRepository], merges it with the Android-free [ExploreAggregator],
 * and exposes it as [ExploreUiState]. All plugin/network work lives here; the screen only renders.
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Currently-playing track + play state, so discovery cards can show the now-playing indicator.
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var lastConnectedPlugins: List<Plugin> = emptyList()

    // The load coroutine; cancel the previous one so overlapping loads (auto-update on every
    // connectedPlugins emission + a manual refresh) can't let an older response overwrite a newer.
    private var loadJob: Job? = null

    init {
        observeConnectedPlugins()
    }

    private fun observeConnectedPlugins() {
        viewModelScope.launch {
            pluginRepository.connectedPlugins.collect { plugins ->
                lastConnectedPlugins = plugins
                loadContent(fromAutoUpdate = true)
            }
        }
    }

    fun loadContent(isRefreshing: Boolean = false, fromAutoUpdate: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Keep existing content on-screen while refreshing / auto-updating; only show the
            // full-screen spinner when we have nothing yet.
            _uiState.update { state ->
                when (state) {
                    is ExploreUiState.Content ->
                        state.copy(isRefreshing = isRefreshing)
                    is ExploreUiState.Error ->
                        if (isRefreshing) ExploreUiState.Loading else state
                    ExploreUiState.Loading -> state
                }
            }

            try {
                val homeContent = pluginRepository.getHomeContent().getOrNull().orEmpty()
                val categories = pluginRepository.getBrowseCategories(limit = 20)
                    .getOrNull()?.items.orEmpty()

                val pluginNames = lastConnectedPlugins.associate { it.info.id to it.info.name }
                val content = ExploreAggregator.aggregate(homeContent, categories, pluginNames)

                _uiState.value = ExploreUiState.Content(
                    content = content,
                    hasPlugins = lastConnectedPlugins.isNotEmpty(),
                    isRefreshing = false,
                )
            } catch (e: CancellationException) {
                // A newer load (or scope teardown) cancelled this one — honour structured
                // concurrency and don't surface it as a user-facing error.
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load Explore content")
                // Don't blow away good content we're already showing on a failed background refresh.
                _uiState.update { state ->
                    if (state is ExploreUiState.Content) state.copy(isRefreshing = false)
                    else ExploreUiState.Error(e.message ?: "Failed to load content")
                }
            }
        }
    }

    fun refresh() = loadContent(isRefreshing = true)

    /**
     * A mood / genre chip was tapped: ask the owning plugin to re-filter that section by the chip's
     * key and swap the fresh items into the section in place, honouring the plugin-SDK
     * `filterSection` contract (the host keeps the section, swaps its items) — the same behaviour as
     * the Home feed's filter chips. The tapped chip is marked selected. Failures are logged and
     * ignored so a bad tap can't break the surface.
     */
    fun onMoodSelected(mood: ExploreMood) {
        viewModelScope.launch {
            val globalSectionId = "${mood.pluginId}:${mood.sectionId}"
            pluginRepository.filterSection(mood.pluginId, mood.sectionId, mood.key).fold(
                onSuccess = { result ->
                    if (result.items.isEmpty()) return@fold
                    _uiState.update { state ->
                        if (state !is ExploreUiState.Content) return@update state
                        state.copy(
                            content = state.content.copy(
                                // Only re-select within the tapped chip's OWN section: deselect its
                                // siblings (same plugin + section) and select the tapped one; leave
                                // every other section's chips untouched so a tap here can't clear an
                                // active chip that belongs to a different section.
                                moods = state.content.moods.map {
                                    if (it.pluginId == mood.pluginId && it.sectionId == mood.sectionId) {
                                        it.copy(selected = it.key == mood.key)
                                    } else {
                                        it
                                    }
                                },
                                sections = state.content.sections.map { section ->
                                    if (section.id == globalSectionId) {
                                        section.copy(items = result.items)
                                    } else section
                                },
                            ),
                        )
                    }
                },
                onFailure = { e -> Timber.w(e, "Explore mood filter failed for ${mood.key}") },
            )
        }
    }

    /** Play a song tapped inside a discovery section, in the context of that section's songs. */
    fun playSongFromSection(song: Song, sectionId: String) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is ExploreUiState.Content) return@launch
                val songs = state.content.sections
                    .firstOrNull { it.id == sectionId }
                    ?.items
                    ?.filterIsInstance<Song>()
                    .orEmpty()
                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    playerRepository.playAll(songs, index)
                } else {
                    playerRepository.play(song)
                }
            } catch (e: Exception) {
                Timber.w(e, "Explore playSongFromSection failed")
            }
        }
    }
}
