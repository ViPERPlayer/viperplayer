package com.viperplayer.presentation.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.data.lyrics.IcuTransliterator
import com.viperplayer.data.lyrics.LyricsRomanizer
import com.viperplayer.data.lyrics.LyricsTranslator
import com.viperplayer.data.player.SleepTimerManager
import com.viperplayer.domain.player.SleepTimerMode
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsCandidate
import com.viperplayer.domain.model.LyricsOffset
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.radio.RadioPlaylist
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.data.social.SessionPlaybackCoordinator
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.LyricsPreferencesRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.SwipeSensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * [Stable] because every public member is either a read-only [StateFlow] `val` (an observable whose
 * *identity* never changes for the lifetime of the ViewModel) or a method — the Compose compiler can
 * therefore treat a `PlayerViewModel` parameter as stable and skip a composable whose only changed
 * input is an unrelated sibling's state. The visible transport leaves don't take the ViewModel at all
 * (they take narrow immutable values + stable callbacks); this annotation only covers the few wrappers
 * that still hold a reference (top bar overflow, sheets, dialogs), keeping them skippable too.
 */
@Stable
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val pluginRepository: PluginRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val lyricsTranslator: LyricsTranslator,
    icuTransliterator: IcuTransliterator,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    private val listenTogetherRepository: ListenTogetherRepository,
    private val sessionPlaybackCoordinator: SessionPlaybackCoordinator,
    private val lyricsPreferencesRepository: LyricsPreferencesRepository,
) : ViewModel() {

    /** User-configured lyrics appearance + behavior, applied by the lyrics renderer. */
    val lyricsSettings: StateFlow<LyricsSettings> = settingsRepository.lyricsSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsSettings()
        )

    // ICU engine for romanization (thread-safe singleton). A fresh [LyricsRomanizer] (with its own
    // per-line cache) is built per song inside the romanization pipeline — see [romanizedLines] — so
    // the cache is only ever touched from that single Default coroutine (no cross-coroutine race) and
    // resets naturally on a track change without a separate clear() call.
    private val romanizerEngine = icuTransliterator
    // Separate flows for optimal performance
    val playbackState: StateFlow<PlaybackInfo> = playerRepository.playbackState

    // --- Listen-together (synced playback, layer 2) session state ---

    /**
     * The player's view of the current Jam session: whether we're in one, whether we're a follower
     * (in a session but can't control — the follower loop owns playback), and the sync state. Kept
     * entirely in the ViewModel; the screen only renders these flags (MVVM).
     */
    val sessionState: StateFlow<SessionUiState> =
        combine(
            listenTogetherRepository.currentSession,
            listenTogetherRepository.synced,
            sessionPlaybackCoordinator.followerTrackUnavailable,
        ) { session, synced, trackUnavailable ->
            val isFollower = session != null && !session.canControl
            SessionUiState(
                inSession = session != null,
                isFollower = isFollower,
                syncState = if (session == null || synced) SyncState.Synced else SyncState.Syncing,
                trackUnavailable = isFollower && trackUnavailable,
            )
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SessionUiState(),
            )

    /**
     * Play/pause the player should reflect: for a follower it's the shared timeline's rate (the local
     * player is driven by the follower loop and briefly lags), otherwise the local player's own state.
     * While a follower is still syncing (no timeline yet, `pb == null`), fall back to the local state so
     * the play icon doesn't flash "paused" in the gap before the first delta arrives.
     */
    val isPlaying: StateFlow<Boolean> =
        combine(
            playbackState.map { it.isPlaying }.distinctUntilChanged(),
            sessionState,
            listenTogetherRepository.playback,
        ) { localPlaying, session, pb ->
            if (session.isFollower && pb != null) pb.rate > 0f else localPlaying
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val duration: StateFlow<Long> = playerRepository.duration

    // Narrow projections of [playbackState] so each transport control observes ONLY the field it
    // renders. Toggling shuffle (or repeat, or the playback source) then recomposes just its own
    // isolated leaf instead of every composable that would read the bundled PlaybackInfo. Each is
    // distinctUntilChanged so an unrelated field change (e.g. repeat) never wakes the shuffle flow.
    val shuffleEnabled: StateFlow<Boolean> = playbackState
        .map { it.shuffleEnabled }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = playbackState.value.shuffleEnabled
        )

    val repeatMode: StateFlow<RepeatMode> = playbackState
        .map { it.repeatMode }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = playbackState.value.repeatMode
        )

    val playbackContext: StateFlow<PlaybackContext?> = playbackState
        .map { it.playbackContext }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = playbackState.value.playbackContext
        )

    // Last successfully-resolved lyrics keyed by song id. Lets a re-subscription (the sheet reopening
    // after WhileSubscribed's timeout, on the *same* song) reuse the resolved result instead of
    // re-flashing the shimmer and re-fetching from the plugin.
    private var lastResolvedSongId: MediaId? = null
    private var lastResolvedLyrics: Lyrics? = null

    // Loading vs. resolved lyrics as one atomic result so the renderer can distinguish "still
    // fetching" (shimmer) from "fetched, none found" (empty state) instead of both looking like null.
    // flatMapLatest re-emits Loading only when the track actually changes (then Loaded), so the shimmer
    // shows for a genuinely new track but not when reopening the sheet on the same, already-fetched one.
    private val lyricsResult: StateFlow<LyricsResult> = currentSong
        .flatMapLatest { song ->
            when {
                song == null -> flowOf<LyricsResult>(LyricsResult.Loaded(null))
                song.id == lastResolvedSongId ->
                    // Same song we already resolved: skip the shimmer and the refetch.
                    flowOf<LyricsResult>(LyricsResult.Loaded(lastResolvedLyrics))
                else -> flow<LyricsResult> {
                    emit(LyricsResult.Loading)
                    val fetched = pluginRepository.getLyrics(song).getOrNull()
                        ?.takeUnless { lyrics -> lyrics.isEmpty }
                    lastResolvedSongId = song.id
                    lastResolvedLyrics = fetched
                    emit(LyricsResult.Loaded(fetched))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsResult.Loading
        )

    // A manually-picked lyric-match override for the current song (feature-parity gap B), keyed by
    // MediaId and persisted. When present it replaces the auto-fetched lyrics; null means "use auto".
    private val lyricsOverride: StateFlow<Lyrics?> = currentSong
        .flatMapLatest { song ->
            if (song == null) flowOf(null) else lyricsPreferencesRepository.override(song.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Lyrics for the current track: the user's manual override when one is set for this song,
     * otherwise the auto-fetched (plugin/LRCLIB) result, or null when none are available.
     */
    val lyrics: StateFlow<Lyrics?> = combine(
        lyricsResult.map { (it as? LyricsResult.Loaded)?.lyrics },
        lyricsOverride,
    ) { fetched, override -> override ?: fetched }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** True when the shown lyrics come from a manual override rather than the auto-fetched result. */
    val lyricsOverridden: StateFlow<Boolean> = lyricsOverride
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // --- Per-song lyrics timing offset (feature-parity gap A) ---

    /**
     * The persisted per-song timing offset (ms, may be negative) applied to synced-lyric line
     * selection. Keyed by [MediaId] so it survives re-opening; `0` when the song was never nudged.
     */
    val lyricsOffsetMs: StateFlow<Long> = currentSong
        .flatMapLatest { song ->
            if (song == null) flowOf(0L) else lyricsPreferencesRepository.offset(song.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /** Nudge the current song's lyrics offset by [steps] +/- steps of [LyricsOffset.STEP_MS]. */
    fun adjustLyricsOffset(steps: Int) {
        val song = currentSong.value ?: return
        val next = LyricsOffset.nudged(lyricsOffsetMs.value, steps)
        viewModelScope.launch { lyricsPreferencesRepository.setOffset(song.id, next) }
    }

    /** Reset the current song's lyrics offset back to 0. */
    fun resetLyricsOffset() {
        val song = currentSong.value ?: return
        viewModelScope.launch { lyricsPreferencesRepository.setOffset(song.id, LyricsOffset.NONE_MS) }
    }

    // --- Manual lyric-match picker (feature-parity gap B) ---

    private val _lyricsSearch = MutableStateFlow(LyricsSearchState())

    /** State of the manual lyric-match search sheet (editable query + phase + results). */
    val lyricsSearch: StateFlow<LyricsSearchState> = _lyricsSearch.asStateFlow()

    /** Open the search sheet, prefilling the query from the current song. */
    fun openLyricsSearch() {
        val song = currentSong.value
        _lyricsSearch.value = LyricsSearchState(
            isOpen = true,
            title = song?.title.orEmpty(),
            artist = song?.artistNames.orEmpty(),
        )
    }

    /** Close the search sheet, discarding its transient state. */
    fun dismissLyricsSearch() {
        _lyricsSearch.value = LyricsSearchState()
    }

    /** Update the editable search title. */
    fun updateLyricsSearchTitle(title: String) {
        _lyricsSearch.value = _lyricsSearch.value.copy(title = title)
    }

    /** Update the editable search artist. */
    fun updateLyricsSearchArtist(artist: String) {
        _lyricsSearch.value = _lyricsSearch.value.copy(artist = artist)
    }

    /** Run the lyric-match search for the current query; no-op for a blank title. */
    fun searchLyrics() {
        val state = _lyricsSearch.value
        val title = state.title.trim()
        if (title.isEmpty()) return
        val artist = state.artist.trim().takeIf { it.isNotEmpty() }
        _lyricsSearch.value = state.copy(phase = LyricsSearchState.Phase.Searching)
        viewModelScope.launch {
            val results = pluginRepository.searchLyrics(title, artist)
            _lyricsSearch.value = _lyricsSearch.value.copy(
                phase = LyricsSearchState.Phase.Results(results)
            )
        }
    }

    /**
     * Persist [candidate]'s lyrics as the manual override for the current song, so subsequent opens
     * (and this one) show the picked lyrics instead of the auto-fetched result, then close the sheet.
     */
    fun pickLyricsCandidate(candidate: LyricsCandidate) {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            lyricsPreferencesRepository.setOverride(song.id, candidate.lyrics)
        }
        _lyricsSearch.value = LyricsSearchState()
    }

    /** Drop any manual override for the current song, reverting to the auto-fetched lyrics. */
    fun revertToAutoLyrics() {
        val song = currentSong.value ?: return
        viewModelScope.launch { lyricsPreferencesRepository.clearOverride(song.id) }
    }

    /** True while the current track's lyrics are still being fetched (drives the loading shimmer). */
    val lyricsLoading: StateFlow<Boolean> = lyricsResult
        .map { it is LyricsResult.Loading }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
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

    // A fresh [LyricsRomanizer] per set of lyrics: pairing each distinct [Lyrics] with its own
    // romanizer means the per-line cache is bounded to the current song (no stale cross-song entries)
    // and — crucially — is only ever created and mutated from the single Default coroutine in
    // [romanizedLines] below, eliminating the previous race with a Main-dispatcher clear(). Keyed by
    // lyrics identity so toggling romanization on/off within a song reuses the same warm cache.
    private val songRomanizer: Flow<Pair<Lyrics?, LyricsRomanizer>> = lyrics
        .distinctUntilChanged { a, b -> a === b }
        .map { it to LyricsRomanizer(romanizerEngine) }

    /**
     * Per-line romanizations aligned to [lyrics] `.lines` order, or null when romanization is off.
     * Each entry is the romanized (Latin-script) form of the line, or null for lines that are already
     * Latin / blank and need no romanization. Transliteration runs off the main thread on
     * [Dispatchers.Default] and is cached per line by a [LyricsRomanizer] built fresh for the current
     * song; recomputed when the song or the toggle changes (in-flight work cancelled by [mapLatest]).
     */
    val romanizedLines: StateFlow<List<String?>?> = combine(songRomanizer, romanizationEnabled) { (l, romanizer), enabled ->
        (l to romanizer).takeIf { enabled }
    }.mapLatest { current ->
        val lines = current?.first?.lines?.map { it.text }
        if (lines.isNullOrEmpty()) return@mapLatest null
        val romanizer = current.second
        withContext(Dispatchers.Default) { romanizer.romanize(lines) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        // Seed the per-session translation/romanization toggles from the persisted defaults whenever
        // a new track's lyrics arrive, so each track starts in the user's preferred state.
        viewModelScope.launch {
            var lastSeededSong: MediaId? = null
            combine(currentSong, settingsRepository.lyricsSettings) { song, settings -> song to settings }
                .collect { (song, settings) ->
                    val songId = song?.id
                    if (songId != null && songId != lastSeededSong) {
                        lastSeededSong = songId
                        _translationEnabled.value = settings.showTranslationByDefault
                        _romanizationEnabled.value = settings.showRomanizationByDefault
                    }
                }
        }
    }

    /**
     * Gets the current playback position in milliseconds.
     * Use this for polling-based position updates where the UI controls the polling frequency.
     */
    suspend fun getCurrentPosition(): Long = playerRepository.getCurrentPosition()

    /**
     * The playback position (ms) the lyrics renderer should use to pick/scroll the active line: the
     * real position shifted by the current song's persisted timing offset (feature-parity gap A). The
     * offset is applied here in the state layer, not in the composable, so the position→line mapping
     * stays testable and the UI just renders. Tap-to-seek still uses the untouched line timestamps.
     */
    suspend fun getEffectiveLyricsPosition(): Long =
        LyricsOffset.effectivePosition(playerRepository.getCurrentPosition(), lyricsOffsetMs.value)

    /** Buffered position in ms (how far ahead of the playhead is loaded) for the seek bar. */
    suspend fun getBufferedPosition(): Long = playerRepository.getBufferedPosition()

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

    /**
     * True when the local transport must be ignored: this device is a session FOLLOWER, so the follower
     * loop owns playback. The host and non-session devices control the player normally (the host's
     * player is mirrored into the session by the coordinator, not routed through commands).
     */
    private fun isFollower(): Boolean = sessionState.value.isFollower

    fun togglePlayPause() {
        if (isFollower()) return
        viewModelScope.launch {
            playerRepository.togglePlayPause()
        }
    }

    fun skipToNext() {
        if (isFollower()) return
        viewModelScope.launch {
            playerRepository.skipToNext()
        }
    }

    fun skipToPrevious() {
        if (isFollower()) return
        viewModelScope.launch {
            playerRepository.skipToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        if (isFollower()) return
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
        if (isFollower()) return
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
                    id = MediaId.Local("queue_${System.currentTimeMillis()}"),
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

    /** Currently-armed sleep timer duration in minutes, or null when off / in end-of-track mode. */
    val sleepTimerMinutes: StateFlow<Int?> = sleepTimerManager.activeMinutes

    /** The armed sleep-timer mode (off / minutes / end-of-track), so the picker can tick the choice. */
    val sleepTimerMode: StateFlow<SleepTimerMode> = sleepTimerManager.mode

    /** Whether the sleep timer fades the volume out before pausing. Persisted via SettingsRepository. */
    val sleepTimerFadeOut: StateFlow<Boolean> = settingsRepository.sleepTimerFadeOut
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Swipe-to-change-song preferences for the artwork pager: whether a swipe commits a track change,
     * and how easily it does so (sensitivity → pager snap threshold). Combined into one snapshot so the
     * screen reads a single flow.
     */
    val swipeSettings: StateFlow<SwipeSettings> = combine(
        settingsRepository.swipeToChangeSong,
        settingsRepository.swipeSensitivity
    ) { enabled, sensitivity ->
        SwipeSettings(enabled = enabled, sensitivity = sensitivity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SwipeSettings())

    /** Playback speed (tempo) and pitch, adjustable independently. */
    val playbackSpeed: StateFlow<Float> = playerRepository.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerRepository.playbackPitch

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { playerRepository.setPlaybackSpeed(speed) }
    }

    fun setPlaybackPitch(pitch: Float) {
        viewModelScope.launch { playerRepository.setPlaybackPitch(pitch) }
    }

    // --- Song radio (issue #7) ---

    /**
     * The synthetic radio [MediaId] for the current song's "Song radio", or null when nothing is
     * playing. The screen navigates to `PlaylistDetail(this)` so the generated radio queue renders in
     * the standard playlist detail screen (no auto-play — the user plays from the list there). The
     * seed's own MediaId is packed into the id so the detail screen can regenerate the radio.
     */
    fun currentSongRadioMediaId(): MediaId? =
        currentSong.value?.let { RadioPlaylist.buildMediaId(it.id) }

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

    /** Arm (or, with a non-positive value, cancel) the sleep timer for a fixed number of minutes. */
    fun setSleepTimer(minutes: Int) = sleepTimerManager.schedule(minutes)

    /** Arm the sleep timer to pause when the current track finishes. */
    fun setSleepTimerEndOfTrack() = sleepTimerManager.scheduleEndOfCurrentTrack()

    /** Cancel any armed sleep timer. */
    fun cancelSleepTimer() = sleepTimerManager.cancel()

    /** Persist whether the sleep timer should fade the volume out before it pauses. */
    fun setSleepTimerFadeOut(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSleepTimerFadeOut(enabled) }
    }
}

/**
 * The player's view of the current Listen-together (Jam) session, for the now-playing UI.
 *
 * @property inSession whether this device is in a session at all.
 * @property isFollower in a session AND can't control — the follower loop drives playback, so the
 *   local transport is disabled and reflects the shared timeline instead.
 * @property syncState whether the session clock is still syncing or has synced.
 */
data class SessionUiState(
    val inSession: Boolean = false,
    val isFollower: Boolean = false,
    val syncState: SyncState = SyncState.Synced,
    /** A follower whose device can't resolve the host's current track — surfaced as "can't play this". */
    val trackUnavailable: Boolean = false,
)

/** Whether the shared session clock has synced (so the playhead can be placed) or is still syncing. */
enum class SyncState { Syncing, Synced }

/**
 * Swipe-to-change-song settings for the now-playing artwork pager.
 *
 * @property enabled when false, a swipe never commits a track change (the pager still shows the queue).
 * @property sensitivity how easily a swipe commits, mapped to the pager's snap threshold.
 */
data class SwipeSettings(
    val enabled: Boolean = true,
    val sensitivity: SwipeSensitivity = SwipeSensitivity.MEDIUM,
)

