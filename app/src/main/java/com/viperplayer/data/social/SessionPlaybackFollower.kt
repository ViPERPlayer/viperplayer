package com.viperplayer.data.social

import com.viperplayer.domain.model.PlaybackState
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Layer 2, part A: drives the LOCAL player from the shared session timeline when this device is a
 * follower ([com.viperplayer.domain.model.ListenSession.canControl] == false).
 *
 * A ~4 Hz loop reads the shared [ListenTogetherRepository.playback] + [ListenTogetherRepository.serverNowUs],
 * asks the pure [FollowerCorrection.decide] what to do, and applies it to the [PlayerRepository]:
 * loading the host's track, holding at the anchor before the delta is effective, matching play/pause,
 * and correcting drift (hard seek for large drift, gentle Sonic time-stretch for small — pitch-preserving).
 *
 * Owned by [SessionPlaybackCoordinator]; [start]/[stop] bracket the follower role. The loop runs on the
 * scope the coordinator passes (Main-dispatched) since the player controller is main-thread only.
 *
 * ## Guards
 * - **One load attempt per epoch**: on a resolve failure the player errors; reloading every tick would
 *   storm. We remember the last epoch we attempted and never re-load it, surfacing [trackUnavailable]
 *   instead while staying in the session.
 * - **Follower mode**: while active, [PlayerRepository.setFollowerMode] disables autoplay/radio queue
 *   growth so the follower's queue never diverges from the host's single shared track.
 */
class SessionPlaybackFollower(
    private val listenTogether: ListenTogetherRepository,
    private val playerRepository: PlayerRepository,
    // Test-only: cap the tick loop so a virtual-time test's advanceUntilIdle terminates (the loop is
    // otherwise infinite). null (prod) runs until [stop].
    private val maxTicks: Int? = null,
) {
    private var loopJob: Job? = null

    /** The (epoch, mediaId) currently loaded in the local player by us, or null when none loaded. */
    private var loaded: FollowerCorrection.LoadedTrack? = null

    /** The epoch we last attempted to load — guards against reload-storming a track that won't resolve. */
    private var lastLoadAttemptEpoch: Long? = null

    private val _trackUnavailable = MutableStateFlow(false)

    /** True while the current track failed to resolve on this device (surfaced to the UI). */
    val trackUnavailable: StateFlow<Boolean> = _trackUnavailable.asStateFlow()

    /** Start the follower loop on [scope] (must be Main-dispatched — the controller is main-thread only). */
    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loaded = null
        lastLoadAttemptEpoch = null
        _trackUnavailable.value = false
        playerRepository.setFollowerMode(true)
        loopJob = scope.launch {
            // Reset any residual correction speed when we take over.
            runCatching { playerRepository.setPlaybackSpeed(1f) }
            var ticks = 0
            while (isActive && (maxTicks == null || ticks < maxTicks)) {
                tick()
                ticks++
                delay(TICK_MS)
            }
        }
    }

    /**
     * Stop following: leave the last track loaded (the user regains manual control), reset the speed to
     * 1.0, and re-enable autoplay. [scope] is used to run the one-shot speed reset off the caller.
     * Idempotent.
     */
    fun stop(scope: CoroutineScope) {
        loopJob?.cancel()
        loopJob = null
        playerRepository.setFollowerMode(false)
        // Reset the drift time-stretch so the user's own playback isn't left running fast/slow.
        scope.launch { runCatching { playerRepository.setPlaybackSpeed(1f) } }
        loaded = null
        lastLoadAttemptEpoch = null
        _trackUnavailable.value = false
    }

    /** One iteration of the follower loop. Kept small; the decision lives in [FollowerCorrection]. */
    private suspend fun tick() {
        val pb = listenTogether.playback.value
        val serverNow = listenTogether.serverNowUs()
        val synced = listenTogether.synced.value

        // Not synced yet: don't drive position; the UI shows "Syncing…".
        if (!synced || serverNow == null) return

        val state = playerRepository.playbackState.value
        // "Ready" = a track is loaded and settled (not idle/buffering/error), so localPos + play are trustable.
        val ready = state.state == PlaybackState.PLAYING || state.state == PlaybackState.PAUSED
        // A track we already loaded (epoch matches) that later errored means the stream couldn't resolve
        // on this device — surface "can't play" but keep following (no reload; the guard below still holds).
        if (state.state == PlaybackState.ERROR && loaded?.epoch == pb?.epoch && pb?.track != null) {
            _trackUnavailable.value = true
        }
        val localPosMs = runCatching { playerRepository.getCurrentPosition() }.getOrDefault(0L)

        val decision = FollowerCorrection.decide(
            pb = pb,
            serverNowUs = serverNow,
            localPosMs = localPosMs,
            loaded = loaded,
            ready = ready,
        )

        if (decision.idle || decision.syncing) return

        // Track load — guarded to once per epoch.
        val load = decision.loadTrack
        if (load != null) {
            if (lastLoadAttemptEpoch != load.epoch) {
                lastLoadAttemptEpoch = load.epoch
                _trackUnavailable.value = false
                runCatching {
                    playerRepository.playRemote(
                        mediaId = load.mediaId,
                        title = load.title,
                        artist = load.artist,
                        artworkUrl = load.artworkUrl,
                        playWhenReady = decision.play,
                    )
                    loaded = FollowerCorrection.LoadedTrack(epoch = load.epoch, mediaId = load.mediaId)
                }.onFailure {
                    Timber.w(it, "Follower: failed to load remote track ${load.mediaId}")
                    _trackUnavailable.value = true
                }
            }
            // We just (re)loaded / attempted this epoch: don't seek/speed this tick — the player is settling.
            return
        }

        // Apply seek (if the decision asks), speed, then play/pause.
        decision.seekMs?.let { seekMs -> runCatching { playerRepository.seekTo(seekMs) } }
        runCatching { playerRepository.setPlaybackSpeed(decision.speed) }
        if (decision.play) {
            if (!state.isPlaying) runCatching { playerRepository.resume() }
        } else {
            if (state.isPlaying) runCatching { playerRepository.pause() }
        }
    }

    private companion object {
        /** Loop cadence: ~4 Hz. */
        const val TICK_MS = 250L
    }
}
