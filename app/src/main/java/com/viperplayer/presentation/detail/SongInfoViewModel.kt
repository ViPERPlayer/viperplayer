package com.viperplayer.presentation.detail

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.audio.BluetoothCodecReader
import com.viperplayer.domain.audio.BluetoothCodecInfo
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.navigableAlbum
import com.viperplayer.domain.model.navigableArtist
import com.viperplayer.domain.model.toEntity
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.SongInfo as SongInfoRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    /** First artist with a real navigable target, or null when none is linked (tile hidden). */
    val artist: Artist? get() = song?.navigableArtist()?.toEntity()
    /** The album only when it points at a real navigable target, else null (tile hidden). */
    val album: Album? get() = song?.navigableAlbum()
}

@HiltViewModel(assistedFactory = SongInfoViewModel.Factory::class)
class SongInfoViewModel @AssistedInject constructor(
    @Assisted private val route: SongInfoRoute,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val pluginRepository: PluginRepository,
    private val bluetoothCodecReader: BluetoothCodecReader,
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
            listOf(ArtistRef(name = route.initialArtist, id = mediaId))
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

    /**
     * Bumped after the runtime BLUETOOTH_CONNECT permission is granted so the reactive codec/permission
     * flows re-evaluate immediately instead of waiting for the next route/codec broadcast.
     */
    private val permissionRefresh = MutableStateFlow(0)

    /** True only while this track is the one playing. Drives every runtime-format flow below. */
    private val isCurrentSong: StateFlow<Boolean> = playerRepository.currentSong
        .map { it?.id == mediaId }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * The active Bluetooth A2DP codec (e.g. "LDAC 990kbps", "aptX HD"), only when this track is the
     * one playing and audio is actually routing to a BT device. Re-reads reactively when the route,
     * active device, or LDAC quality changes while the screen is open. Null on wired/speaker routes,
     * when the platform can't report it, or when [Manifest.permission.BLUETOOTH_CONNECT] isn't
     * granted — the reader degrades gracefully.
     */
    val bluetoothCodec: StateFlow<BluetoothCodecInfo?> =
        combine(isCurrentSong, permissionRefresh) { isCurrent, _ -> isCurrent }
            .flatMapLatest { isCurrent ->
                if (isCurrent) bluetoothCodecReader.codecFlow() else flowOf(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /**
     * True when the BLUETOOTH_CONNECT permission is missing AND we're actually on a BT A2DP route for
     * the playing track — i.e. requesting it would let us show the codec. The screen only prompts when
     * this is true, so users aren't asked for a permission they don't currently need.
     */
    val bluetoothPermissionNeeded: StateFlow<Boolean> =
        combine(isCurrentSong, permissionRefresh) { isCurrent, _ -> isCurrent }
            .mapLatest { isCurrent ->
                BluetoothCodecInfo.shouldPromptForBluetoothPermission(
                    isCurrentSong = isCurrent,
                    permissionMissing = bluetoothCodecReader.needsBluetoothConnectPermission(),
                    onBluetoothRoute = bluetoothCodecReader.isBluetoothA2dpRoute(),
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /** Called by the screen once the BLUETOOTH_CONNECT permission is granted, to refresh the codec. */
    fun onBluetoothPermissionGranted() {
        permissionRefresh.update { it + 1 }
    }
}
