package com.viperplayer.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.data.lyrics.IcuTransliterator
import com.viperplayer.data.lyrics.LyricsRomanizer
import com.viperplayer.data.lyrics.LyricsTranslator
import com.viperplayer.data.player.SleepTimerManager
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val pluginRepository: PluginRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val lyricsTranslator: LyricsTranslator,
    icuTransliterator: IcuTransliterator,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    // Pure orchestration for romanization; the ICU engine is the injected Android impl. Caches per
    // unique line across the ViewModel's lifetime (identical chorus lines transliterate once).
    private val lyricsRomanizer = LyricsRomanizer(icuTransliterator)
    // Separate flows for optimal performance
    val playbackState: StateFlow<PlaybackInfo> = playerRepository.playbackState

    val isPlaying: StateFlow<Boolean> =
        playbackState
            .map { it.isPlaying }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val duration: StateFlow<Long> = playerRepository.duration

    /** Lyrics for the current track from a lyrics-capable plugin, or null when none are available. */
    val lyrics: StateFlow<Lyrics?> = currentSong
        .mapLatest { song ->
            song?.let { pluginRepository.getLyrics(it).getOrNull()?.takeUnless { lyrics -> lyrics.isEmpty } }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // --- Lyrics translation (on-device, ML Kit) ---

    private val _translationEnabled = MutableStateFlow(false)

    /** Whether the user has toggled on translated lyrics in the lyrics sheet. */
    val translationEnabled: StateFlow<Boolean> = _translationEnabled.asStateFlow()

    /** Flip the lyrics-translation toggle. */
    fun toggleTranslation() {
        _translationEnabled.value = !_translationEnabled.value
    }

    private val _translationInProgress = MutableStateFlow(false)

    /** True while an on-device translation is being computed (model download / per-line translate). */
    val translationInProgress: StateFlow<Boolean> = _translationInProgress.asStateFlow()

    /**
     * Per-line translations aligned to [lyrics] `.lines` order, or null when translation is off,
     * unavailable, or still loading. Recomputed whenever the song or the toggle changes; in-flight
     * work is cancelled by [mapLatest] when either input changes.
     */
    val translatedLines: StateFlow<List<String>?> = combine(lyrics, translationEnabled) { l, enabled ->
        l.takeIf { enabled }
    }.mapLatest { current ->
        val lines = current?.lines?.map { it.text }
        if (lines.isNullOrEmpty()) return@mapLatest null
        _translationInProgress.value = true
        try {
            lyricsTranslator.translate(lines, Locale.getDefault().language)
        } finally {
            _translationInProgress.value = false
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // --- Lyrics romanization (on-device, ICU transliteration) ---

    private val _romanizationEnabled = MutableStateFlow(false)

    /** Whether the user has toggled on romanized lyrics in the lyrics sheet. */
    val romanizationEnabled: StateFlow<Boolean> = _romanizationEnabled.asStateFlow()

    /** Flip the lyrics-romanization toggle. */
    fun toggleRomanization() {
        _romanizationEnabled.value = !_romanizationEnabled.value
    }

    init {
        // Drop cached romanizations when the track changes so the per-line cache stays bounded to the
        // current song rather than growing for the whole listening session.
        viewModelScope.launch {
            currentSong
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { lyricsRomanizer.clear() }
        }
    }

    /**
     * Per-line romanizations aligned to [lyrics] `.lines` order, or null when romanization is off.
     * Each entry is the romanized (Latin-script) form of the line, or null for lines that are already
     * Latin / blank and need no romanization. Transliteration runs off the main thread on
     * [Dispatchers.Default] and is cached per line by [lyricsRomanizer]; recomputed when the song or
     * the toggle changes (in-flight work cancelled by [mapLatest]).
     */
    val romanizedLines: StateFlow<List<String?>?> = combine(lyrics, romanizationEnabled) { l, enabled ->
        l.takeIf { enabled }
    }.mapLatest { current ->
        val lines = current?.lines?.map { it.text }
        if (lines.isNullOrEmpty()) return@mapLatest null
        withContext(Dispatchers.Default) { lyricsRomanizer.romanize(lines) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Gets the current playback position in milliseconds.
     * Use this for polling-based position updates where the UI controls the polling frequency.
     */
    suspend fun getCurrentPosition(): Long = playerRepository.getCurrentPosition()
    val queue: StateFlow<List<Song>> = playerRepository.queue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Liked status - loaded from database and synced with current song
    val isLiked: StateFlow<Boolean> = currentSong
        .flatMapLatest { song ->
            if (song != null) {
                mediaLibraryRepository.getSong(song.id)
                    .map { it?.isLiked ?: false }
                    .distinctUntilChanged()
            } else {
                flowOf(false)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun togglePlayPause() {
        viewModelScope.launch {
            playerRepository.togglePlayPause()
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            playerRepository.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            playerRepository.skipToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(positionMs)
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            playerRepository.setShuffle(!playbackState.value.shuffleEnabled)
        }
    }

    fun cycleRepeatMode() {
        viewModelScope.launch {
            val nextMode = when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            playerRepository.setRepeatMode(nextMode)
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            playerRepository.addToQueue(song)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playerRepository.reorderQueue(fromIndex, toIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        viewModelScope.launch {
            playerRepository.removeFromQueue(index)
        }
    }

    fun duplicateInQueue(index: Int) {
        viewModelScope.launch {
            playerRepository.duplicateInQueue(index)
        }
    }

    fun playFromQueue(index: Int) {
        viewModelScope.launch {
            playerRepository.playFromQueue(index)
        }
    }

    /** Persist the current queue as a new local playlist. No-op for a blank name or empty queue. */
    fun saveQueueAsPlaylist(name: String) {
        viewModelScope.launch {
            val songs = queue.value
            val trimmed = name.trim()
            if (songs.isEmpty() || trimmed.isEmpty()) return@launch
            mediaLibraryRepository.savePlaylist(
                Playlist(
                    id = MediaId("local", "queue_${System.currentTimeMillis()}"),
                    name = trimmed,
                    songCount = songs.size,
                    isPublic = false,
                    isEditable = true,
                    songs = songs
                )
            )
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val song = currentSong.value
            if (song != null) {
                // Ensure song is saved to database first
                mediaLibraryRepository.saveSong(song)

                // Then update liked status
                val currentLiked = isLiked.value
                val newLiked = !currentLiked

                mediaLibraryRepository.setSongLiked(song.id, newLiked)

                // If liking, also add to library (saved songs)
                if (newLiked) {
                    mediaLibraryRepository.setSongSaved(song.id, true)
                }
            }
        }
    }

    /**
     * Gets the current audio format from ExoPlayer.
     * Returns null if no track is playing or format information is not available.
     */
    suspend fun getAudioFormat(): AudioFormat? {
        return playerRepository.getAudioFormat()
    }

    // --- Overflow-menu actions ---

    /** Currently-armed sleep timer duration in minutes, or null when off. */
    val sleepTimerMinutes: StateFlow<Int?> = sleepTimerManager.activeMinutes

    /** Playback speed (tempo) and pitch, adjustable independently. */
    val playbackSpeed: StateFlow<Float> = playerRepository.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerRepository.playbackPitch

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { playerRepository.setPlaybackSpeed(speed) }
    }

    fun setPlaybackPitch(pitch: Float) {
        viewModelScope.launch { playerRepository.setPlaybackPitch(pitch) }
    }

    /** "Song radio": seed a queue of related songs from the current track and play it. */
    fun startSongRadio() {
        viewModelScope.launch {
            val song = currentSong.value ?: return@launch
            val related = pluginRepository.getRelatedSongs(song.id).getOrNull()?.items.orEmpty()
            val queue = listOf(song) + related.filter { it.id != song.id }
            playerRepository.playAll(queue, startIndex = 0)
        }
    }

    /** "Add to library": persist the current song and mark it saved. */
    fun addCurrentSongToLibrary() {
        viewModelScope.launch {
            val song = currentSong.value ?: return@launch
            mediaLibraryRepository.saveSong(song)
            mediaLibraryRepository.setSongSaved(song.id, true)
        }
    }

    /**
     * Queue the current song for offline download. Returns true if a download was started, false if
     * there is no current song. Whether the source is actually downloadable is reported later through
     * [DownloadManager.downloads] (progressive URLs download; DASH/HLS/PCM are marked UNSUPPORTED).
     */
    fun downloadCurrentSong(): Boolean {
        val song = currentSong.value ?: return false
        downloadManager.enqueue(song)
        return true
    }

    /** Arm (or, with a non-positive value, cancel) the sleep timer. */
    fun setSleepTimer(minutes: Int) = sleepTimerManager.schedule(minutes)
}

