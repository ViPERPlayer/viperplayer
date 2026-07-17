package com.viperplayer.data.social

import com.viperplayer.domain.model.PlaybackState
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Layer 2, part B: mirrors the LOCAL player into the shared session when this device is the controller
 * ([com.viperplayer.domain.model.ListenSession.canControl] == true).
 *
 * The host keeps playing normally — this coordinator never drives the host's own player. It OBSERVES
 * [PlayerRepository] and forwards state changes to [ListenTogetherRepository]'s `control*` actions so
 * followers mirror the host:
 * - **Seed** on activation: publish the current track + position, then play/pause per state.
 * - **Track change** ([PlayerRepository.currentSong]): `controlSetTrack(new, currentPos)` + play/pause.
 * - **Play/pause** ([PlaybackInfo.isPlaying]): `controlPlay()` / `controlPause()`.
 * - **Seek**: a ~1 Hz sampler compares the actual position against the expected `last + elapsed*speed`;
 *   a large deviation ([HostSeekDetector]) means a seek → `controlSeek(currentPos)`.
 *
 * The host ignores its own broadcasts (only followers apply deltas), so there is no feedback loop.
 * Owned by [SessionPlaybackCoordinator]; [start]/[stop] bracket the controller role.
 */
class SessionPlaybackHost(
    private val listenTogether: ListenTogetherRepository,
    private val playerRepository: PlayerRepository,
    // Test-only: cap the seek-detection sampler so a virtual-time test's advanceUntilIdle terminates
    // (the sampler is otherwise infinite). null (prod) runs until [stop].
    private val maxSamples: Int? = null,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // The two state observers START by replaying the host's CURRENT value (StateFlow semantics),
            // which IS the session seed — publishing the current track + position + play state. Because
            // they begin collecting immediately (no seed()/drop() window), a change that lands right as
            // the session opens can't slip through unpublished. distinctUntilChanged dedups redundant
            // re-emissions (e.g. a like/download that re-emits the same song).

            // Track (+ its position + play state) — current value, then every change.
            launch { observeTrackChanges() }

            // Play/pause — current value, then every transition.
            launch { observePlayPause() }

            // Seek detection via ~1 Hz sampling.
            launch { detectSeeks() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Publish the current track and every subsequent change: the track at the host's current position,
     * then a play/pause to match. The first emission is the current song (StateFlow replay) — that seeds
     * a fresh session. `distinctUntilChanged` on the media id keeps a like/download re-emission of the
     * same song from re-publishing.
     */
    private suspend fun observeTrackChanges() {
        playerRepository.currentSong
            .map { it?.id }
            .distinctUntilChanged()
            .collect { id ->
                if (id == null) return@collect // nothing loaded yet — don't publish a blank track.
                val song = playerRepository.currentSong.value ?: return@collect
                val positionUs = runCatching { playerRepository.getCurrentPosition() }.getOrDefault(0L) * 1000
                runCatching {
                    listenTogether.controlSetTrack(song.toSessionTrack(), positionUs)
                    if (playerRepository.playbackState.value.isPlaying) {
                        listenTogether.controlPlay()
                    } else {
                        listenTogether.controlPause()
                    }
                }.onFailure { Timber.w(it, "Host: failed to publish track change") }
            }
    }

    /** Publish the current play state, then every play/pause transition. */
    private suspend fun observePlayPause() {
        playerRepository.playbackState
            .map { it.isPlaying }
            .distinctUntilChanged()
            .collect { playing ->
                // Only meaningful once a track is loaded; a blank session has nothing to play/pause.
                if (playerRepository.currentSong.value == null) return@collect
                runCatching {
                    if (playing) listenTogether.controlPlay() else listenTogether.controlPause()
                }.onFailure { Timber.w(it, "Host: failed to publish play/pause") }
            }
    }

    /**
     * Sample the position ~1 Hz. When it deviates from `last + elapsedWall*speed` by more than
     * [HostSeekDetector.SEEK_THRESHOLD_MS], a seek happened → publish it. Skips the tick after a track
     * change (the position legitimately jumps to 0) by tracking the current media id.
     */
    private suspend fun detectSeeks() {
        var lastPos = runCatching { playerRepository.getCurrentPosition() }.getOrDefault(0L)
        var lastWall = System.currentTimeMillis()
        var lastTrackId = playerRepository.currentSong.value?.id
        var samples = 0
        while (currentCoroutineContext().isActive && (maxSamples == null || samples < maxSamples)) {
            delay(SAMPLE_MS)
            samples++
            val now = System.currentTimeMillis()
            val pos = runCatching { playerRepository.getCurrentPosition() }.getOrDefault(lastPos)
            val trackId = playerRepository.currentSong.value?.id
            val info = playerRepository.playbackState.value
            val speed = playerRepository.playbackSpeed.value

            // A track change moves the playhead to 0 legitimately — don't mistake it for a seek. The
            // change itself is published by observeTrackChanges.
            if (trackId != lastTrackId) {
                lastTrackId = trackId
                lastPos = pos
                lastWall = now
                continue
            }

            // Buffering stalls the playhead while wall time keeps advancing — re-baseline and skip so a
            // mid-track rebuffer isn't mistaken for a seek (which would spam controlSeek to every
            // follower). Only PLAYING↔PLAYING intervals are checked; isPlaying alone is true for BUFFERING.
            if (info.state != PlaybackState.PLAYING) {
                lastPos = pos
                lastWall = now
                continue
            }

            val elapsed = (now - lastWall).coerceAtLeast(0L)
            val seek = HostSeekDetector.seekHappened(
                lastPositionMs = lastPos,
                currentPositionMs = pos,
                elapsedWallMs = elapsed,
                speed = speed,
                isPlaying = info.isPlaying,
            )
            if (seek) {
                runCatching { listenTogether.controlSeek(pos * 1000) }
                    .onFailure { Timber.w(it, "Host: failed to publish seek") }
            }
            lastPos = pos
            lastWall = now
        }
    }

    private companion object {
        /** Seek-detection sampling cadence (~1 Hz). */
        const val SAMPLE_MS = 1000L
    }
}
