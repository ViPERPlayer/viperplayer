package com.viperplayer.data.player

import androidx.media3.common.C
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A sleep timer that pauses playback either after a chosen number of minutes ([schedule]) or when the
 * current track finishes ([scheduleEndOfCurrentTrack]). App-scoped ([Singleton]) so it survives the
 * player UI being closed.
 *
 * [mode] is the armed [SleepTimerMode] (or [SleepTimerMode.Off]); [activeMinutes] is a convenience
 * projection for the minute presets in the picker (null unless a [SleepTimerMode.Minutes] is armed).
 *
 * When the persisted [SettingsRepository.sleepTimerFadeOut] toggle is on, the volume is ramped down to
 * silence over the final [FADE_MS] before the pause, then restored to full for the next playback. The
 * fade writes [androidx.media3.session.MediaController.volume] the same way the alarm fade-in does (see
 * [com.viperplayer.alarm.scheduling.AlarmPlaybackStarter]); if the user has crossfade enabled the two
 * volume writers may briefly contend near a track boundary, but the fade re-asserts every step so it
 * still converges. Crossfade is off by default.
 */
@Singleton
class SleepTimerManager @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val mediaControllerManager: MediaControllerManager,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _mode = MutableStateFlow<SleepTimerMode>(SleepTimerMode.Off)
    val mode: StateFlow<SleepTimerMode> = _mode.asStateFlow()

    /** The armed minute preset, or null when off / in end-of-track mode. For the minute-preset picker. */
    val activeMinutes: StateFlow<Int?> =
        _mode.map { (it as? SleepTimerMode.Minutes)?.count }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Arm the timer for [minutes]; a non-positive value cancels it. */
    fun schedule(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        _mode.value = SleepTimerMode.Minutes(minutes)
        val totalMs = minutes * 60_000L
        job = scope.launch {
            runCountdown(totalMs)
            finishTimer()
        }
    }

    /**
     * Arm the timer to pause when the CURRENT track finishes. Captures the media item that is current at
     * arm time; playback pauses when that item transitions away (the track ends or the user skips) or,
     * as a fallback, when the polled position reaches the item's duration. Re-evaluated on every poll so
     * a seek/track-change simply retargets the boundary. No-op with nothing playing.
     */
    fun scheduleEndOfCurrentTrack() {
        cancel()
        _mode.value = SleepTimerMode.EndOfTrack
        job = scope.launch {
            val armedId = playerRepository.currentSong.value?.id
            if (armedId == null) {
                _mode.value = SleepTimerMode.Off
                return@launch
            }
            runEndOfTrack(armedId)
            finishTimer()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _mode.value = SleepTimerMode.Off
    }

    /**
     * Wait out a fixed [totalMs] delay, fading the last [FADE_MS] toward silence when the persisted
     * fade-out toggle is on. Polls in short steps so the fade tracks real time; a plain delay is used
     * when fade-out is off.
     */
    private suspend fun runCountdown(totalMs: Long) {
        val fadeMs = fadeMsIfEnabled()
        if (fadeMs <= 0L) {
            delay(totalMs)
            return
        }
        val startedAt = System.currentTimeMillis()
        while (true) {
            val remaining = SleepTimerPlan.remainingMs(System.currentTimeMillis() - startedAt, totalMs)
            if (remaining <= 0L) break
            setVolume(SleepTimerPlan.fadeVolumeAt(remaining, fadeMs))
            delay(minOf(POLL_MS, remaining))
        }
    }

    /**
     * Poll until the [armedId] track is no longer current (it ended or was skipped) or its position
     * reaches its duration, fading the last [FADE_MS] toward silence when fade-out is on.
     */
    private suspend fun runEndOfTrack(armedId: MediaId) {
        val fadeMs = fadeMsIfEnabled()
        while (true) {
            // Track changed (finished or skipped) -> the "current track" ended.
            if (playerRepository.currentSong.value?.id != armedId) break

            val duration = playerRepository.duration.value
            val position = playerRepository.getCurrentPosition()
            if (duration > 0L && duration != C.TIME_UNSET) {
                val remaining = SleepTimerPlan.remainingMs(position, duration)
                if (remaining <= 0L) break
                if (fadeMs > 0L && SleepTimerPlan.isInFadeWindow(remaining, fadeMs)) {
                    setVolume(SleepTimerPlan.fadeVolumeAt(remaining, fadeMs))
                }
                delay(minOf(POLL_MS, remaining))
            } else {
                // Duration unknown yet (still buffering) — wait for it without firing.
                delay(POLL_MS)
            }
        }
    }

    /** Pause, restore full volume for the next playback, and clear the armed mode. */
    private suspend fun finishTimer() {
        playerRepository.pause()
        setVolume(1f)
        _mode.value = SleepTimerMode.Off
    }

    /** [FADE_MS] when the persisted fade-out toggle is on, else 0 (no fade). */
    private suspend fun fadeMsIfEnabled(): Long =
        if (settingsRepository.sleepTimerFadeOut.first()) FADE_MS else 0L

    private suspend fun setVolume(volume: Float) = withContext(Dispatchers.Main) {
        val controller = mediaControllerManager.controllerFlow.first()
        runCatching { controller.volume = volume.coerceIn(0f, 1f) }
    }

    private companion object {
        /** Length of the volume fade-out before the pause fires, in ms. */
        const val FADE_MS = 8_000L

        /** Poll cadence for the fade ramp and end-of-track boundary check, in ms. */
        const val POLL_MS = 200L
    }
}
