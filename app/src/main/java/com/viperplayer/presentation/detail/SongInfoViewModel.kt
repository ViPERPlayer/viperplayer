package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.SongInfo as SongInfoRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * State for the Song-info detail screen. Every field mirrors what a plugin can provide; the screen
 * hides sections whose data is absent (see [SongInfoScreen]).
 */
data class SongInfoUiState(
    val song: Song?,
    /** The source plugin's display name (e.g. "Local Files"), or null if the plugin isn't connected. */
    val pluginName: String?,
    /** The plugin's package name — used both as the id and to load its launcher icon. */
    val pluginPackage: String,
    val isCurrentSong: Boolean,
) {
    val artist: Artist? get() = song?.artists?.firstOrNull()
    val album: Album? get() = song?.album
}

@HiltViewModel(assistedFactory = SongInfoViewModel.Factory::class)
class SongInfoViewModel @AssistedInject constructor(
    @Assisted private val route: SongInfoRoute,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val pluginRepository: PluginRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: SongInfoRoute): SongInfoViewModel
    }

    private val mediaId: MediaId = route.mediaId

    /** Minimal placeholder from the nav args so the header shows instantly while the real song loads. */
    private val placeholder = Song(
        id = mediaId,
        title = route.initialTitle,
        artists = if (route.initialArtist.isNotBlank()) {
            listOf(Artist(id = mediaId, name = route.initialArtist))
        } else {
            emptyList()
        },
        artworkUrl = route.initialArtworkUrl,
    )

    val uiState: StateFlow<SongInfoUiState> = combine(
        playerRepository.currentSong,
        mediaLibraryRepository.getSong(mediaId),
        pluginRepository.connectedPlugins,
    ) { current, dbSong, plugins ->
        val isCurrent = current?.id == mediaId
        // Prefer the live playing song (fullest metadata), then the DB copy, then the nav placeholder.
        val song = current?.takeIf { it.id == mediaId } ?: dbSong ?: placeholder
        val pluginName = plugins.firstOrNull { it.info.id == mediaId.pluginId }?.info?.name
        SongInfoUiState(
            song = song,
            pluginName = pluginName,
            pluginPackage = mediaId.pluginId,
            isCurrentSong = isCurrent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongInfoUiState(
            song = placeholder,
            pluginName = null,
            pluginPackage = mediaId.pluginId,
            isCurrentSong = false,
        ),
    )

    /**
     * Runtime audio format from ExoPlayer — only available while this track is the one playing (the
     * player is the only source of the decoded stream's real format). Null otherwise.
     */
    val audioFormat: StateFlow<AudioFormat?> = playerRepository.currentSong
        .map { it?.id == mediaId }
        .distinctUntilChanged()
        .mapLatest { isCurrent -> if (isCurrent) playerRepository.getAudioFormat() else null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}
