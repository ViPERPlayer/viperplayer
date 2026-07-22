package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.sort.MediaSorter
import com.viperplayer.follows.data.FollowedArtistsRepository
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.presentation.navigation.ArtistDetail as ArtistDetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for Artist Detail screen.
 */
sealed class ArtistDetailUiState {
    data class Loading(val initialArtist: ArtistDetail) : ArtistDetailUiState()

    /**
     * @param artist the loaded artist profile (its `topSongs` stay in the plugin's original order).
     * @param songsSort the chosen order for the Top Songs list; [SortOrder.DEFAULT] keeps the original.
     * @param sortedSongs [artist]'s `topSongs` after [songsSort] is applied.
     */
    data class Success(
        val artist: ArtistDetail,
        val songsSort: SortOrder = SortOrder.DEFAULT,
        val sortedSongs: List<Song> = artist.topSongs,
    ) : ArtistDetailUiState()

    data class Error(val message: String) : ArtistDetailUiState()
}

/**
 * ViewModel for Artist Detail screen.
 */
@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val artistDetail: ArtistDetailRoute,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository,
    private val followedArtistsRepository: FollowedArtistsRepository,
    private val stringProvider: StringProvider
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(artistDetail: ArtistDetailRoute): ArtistDetailViewModel
    }

    private val artistId = artistDetail.artistId

    /** The plugin backing this screen — used to match pending plugin actions on errors. */
    val pluginId: String get() = artistId.routingPluginId

    // Minimal placeholder shown while the full artist is (re)fetched by id.
    private val placeholderArtist = ArtistDetail(
        id = artistDetail.artistId,
        name = artistDetail.initialName,
        imageUrl = artistDetail.initialImageUrl,
    )

    private val _uiState =
        MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading(placeholderArtist))
    val uiState = _uiState.asStateFlow()

    // Expose current song and playing state from player repository
    val currentSong = playerRepository.currentSong
    val isPlaying = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Whether this artist is currently followed — drives the Follow/Following toggle button. */
    val isFollowing = followedArtistsRepository.isFollowing(artistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Latest persisted Top Songs order, tracked even while Loading so a fresh load applies it.
    private var currentSortOrder: SortOrder = SortOrder.DEFAULT

    init {
        loadArtistDetails()
        observeSortOrder()
    }

    /** Re-apply the persisted Top Songs order whenever it changes (or on first load). */
    private fun observeSortOrder() {
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.ARTIST_SONGS).collect { order ->
                currentSortOrder = order
                _uiState.update { state ->
                    if (state is ArtistDetailUiState.Success) {
                        state.copy(
                            songsSort = order,
                            sortedSongs = MediaSorter.sortSongs(state.artist.topSongs, order),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Persist a new Top Songs [order]; the observer re-sorts the visible list. */
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(SortView.ARTIST_SONGS, order)
        }
    }

    /**
     * Follow the artist if not followed, unfollow otherwise. Reads the current persisted state (not
     * the possibly-stale [isFollowing] snapshot) so a concurrent toggle can't flip the wrong way, and
     * uses the latest loaded name/artwork so the Following list has something to render. Follow is
     * idempotent at the repository level.
     */
    fun toggleFollow() {
        viewModelScope.launch {
            try {
                if (followedArtistsRepository.isFollowing(artistId).first()) {
                    followedArtistsRepository.unfollow(artistId)
                } else {
                    val artist = (_uiState.value as? ArtistDetailUiState.Success)?.artist
                    val name = artist?.name ?: artistDetail.initialName
                    // Never persist a nameless follow: only reachable if Follow is tapped while the
                    // artist is still Loading AND the screen was opened with a blank initialName. The
                    // name is available a moment later, so the tap simply no-ops until then. Unfollow
                    // is unaffected (handled above).
                    if (name.isBlank()) return@launch
                    followedArtistsRepository.follow(
                        FollowedArtist(
                            mediaId = artistId,
                            name = name,
                            artworkUrl = artist?.imageUrl ?: artistDetail.initialImageUrl,
                            followedAt = System.currentTimeMillis(),
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail follow toggle failed")
            }
        }
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.update {
                val initialArtist = when (it) {
                    is ArtistDetailUiState.Success -> it.artist
                    else -> placeholderArtist
                }

                ArtistDetailUiState.Loading(initialArtist)
            }

            try {
                // Load artist details - the artist object from AIDL already contains topSongs and albums
                val artistResult = pluginRepository.getArtist(artistId)
                if (artistResult.isFailure) {
                    _uiState.value = ArtistDetailUiState.Error(
                        artistResult.exceptionOrNull()?.message
                            ?: stringProvider.getString(R.string.artist_load_failed)
                    )
                    return@launch
                }

                val artist = artistResult.getOrNull()!!

                // Some plugins (e.g. a plugin) return only artist metadata from getArtist and serve
                // the catalog through the dedicated paged endpoints, leaving the inline lists empty —
                // which rendered as an empty profile. Backfill from getArtistSongs / getArtistAlbums
                // when the inline lists are empty so the screen actually shows the artist's content.
                val enriched = enrichArtistContent(artist)
                val order = currentSortOrder
                _uiState.value = ArtistDetailUiState.Success(
                    artist = enriched,
                    songsSort = order,
                    sortedSongs = MediaSorter.sortSongs(enriched.topSongs, order),
                )
            } catch (e: Exception) {
                _uiState.value = ArtistDetailUiState.Error(
                    e.message ?: stringProvider.getString(R.string.artist_load_details_failed)
                )
            }
        }
    }

    /**
     * Backfill an artist's songs/albums from the paged endpoints when [PluginRepository.getArtist]
     * didn't inline them (it's optional in the plugin API). Both are fetched concurrently and only
     * when their inline list is empty; failures or unsupported endpoints simply leave them empty.
     */
    private suspend fun enrichArtistContent(artist: ArtistDetail): ArtistDetail = coroutineScope {
        val songsDeferred = if (artist.topSongs.isEmpty()) {
            async { pluginRepository.getArtistSongs(artist.id).getOrNull()?.items.orEmpty() }
        } else null
        val albumsDeferred = if (artist.albums.isEmpty()) {
            async { pluginRepository.getArtistAlbums(artist.id).getOrNull()?.items.orEmpty() }
        } else null

        artist.copy(
            topSongs = songsDeferred?.await() ?: artist.topSongs,
            albums = albumsDeferred?.await() ?: artist.albums
        )
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state !is ArtistDetailUiState.Success) return@launch

                val songs = state.sortedSongs

                if (songs.isNotEmpty()) {
                    val index = songs.indexOfFirst { it.id == song.id }
                    val context = PlaybackContext.Artist(state.artist.id, state.artist.name)
                    if (index != -1) {
                        playerRepository.playAll(songs, index, context)
                    } else {
                        playerRepository.play(song, context)
                    }
                } else {
                    val context = PlaybackContext.Artist(state.artist.id, state.artist.name)
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun playAllSongs() {
        viewModelScope.launch {
            try {
                val state = _uiState.value as? ArtistDetailUiState.Success ?: return@launch
                val songs = state.sortedSongs
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Artist(state.artist.id, state.artist.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.playNext(song)
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.addToQueue(song)
            } catch (e: Exception) {
                Timber.w(e, "ArtistDetail background operation failed")
            }
        }
    }

    fun refresh() {
        loadArtistDetails()
    }
}

