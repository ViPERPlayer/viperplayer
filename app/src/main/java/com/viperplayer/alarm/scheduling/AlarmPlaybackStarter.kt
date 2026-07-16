package com.viperplayer.alarm.scheduling

import androidx.media3.session.MediaController
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.domain.AlarmContentSpec
import com.viperplayer.alarm.domain.AlarmFade
import com.viperplayer.data.player.MediaControllerManager
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an alarm's [AlarmContentSpec] to songs and starts playback through the existing
 * [PlayerRepository] path (the same one the UI uses), then ramps the player volume in via the pure
 * [AlarmFade] curve. All the "what to play + how to fade" logic lives here so [AlarmReceiver] stays a
 * thin dispatcher.
 *
 * App-scoped ([Singleton]) so the fade coroutine outlives the short-lived BroadcastReceiver (the
 * media foreground service keeps the process alive) and so a later Dismiss can [cancel][stop] it —
 * the fade [Job] is held here, not in the receiver.
 *
 * NOTE on volume: if the user has crossfade enabled, [PlayerRepository]'s own volume loop also writes
 * `controller.volume` near track boundaries; during that overlap the two may briefly contend. The
 * alarm fade re-asserts every [FADE_STEP_MS], so it still converges to the target. Crossfade is off
 * by default, so this only affects users who opted into it.
 */
@Singleton
class AlarmPlaybackStarter @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val mediaControllerManager: MediaControllerManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fadeJob: Job? = null

    /**
     * Start playback for [alarm]. Suspends only until playback has been *issued* (songs resolved +
     * play command sent); the volume fade-in then continues asynchronously in this manager's own
     * scope so the caller's wakelock window isn't tied to the full fade length. Returns true if songs
     * were found and playback started, false if there was nothing to play.
     */
    suspend fun start(alarm: Alarm): Boolean {
        // A new alarm supersedes any in-flight fade.
        fadeJob?.cancel()

        val songs = resolveSongs(alarm.content)
        if (songs.isEmpty()) {
            Timber.w("Alarm #${alarm.id} had no songs to play for ${alarm.content}")
            return false
        }

        val target = alarm.volume.coerceIn(0f, 1f)
        val fadeMs = alarm.fadeInDurationMs

        // Start attenuated so the fade begins from silence; skip if there's no fade.
        if (fadeMs > 0L) setVolume(0f)

        playerRepository.playAll(songs, startIndex = 0, context = null)

        if (fadeMs <= 0L) {
            setVolume(target)
        } else {
            // Run the ramp in our own scope so it survives the receiver finishing.
            fadeJob = scope.launch { runFadeIn(fadeMs, target) }
        }
        return true
    }

    /** Stop the alarm's playback (invoked by the notification's Dismiss action). */
    suspend fun stop() {
        fadeJob?.cancel()
        fadeJob = null
        playerRepository.pause()
        // Restore full volume so a later normal playback isn't left attenuated by a partial fade.
        setVolume(1f)
    }

    private suspend fun resolveSongs(spec: AlarmContentSpec): List<Song> = when (spec) {
        AlarmContentSpec.ShuffleAll -> {
            mediaLibraryRepository.getAllSavedSongs().first()
                .filter { it.isPlayable }
                .shuffled()
        }

        is AlarmContentSpec.PlaylistContent -> {
            val playlist = mediaLibraryRepository.getPlaylist(spec.playlistId).first()
            val songs = playlist?.songs.orEmpty().filter { it.isPlayable }
            if (spec.shuffle) songs.shuffled() else songs
        }
    }

    /** Ramp [MediaController.volume] from 0 to [target] over [fadeMs], polling until the fade ends. */
    private suspend fun runFadeIn(fadeMs: Long, target: Float) {
        val startedAt = System.currentTimeMillis()
        while (currentIsActive()) {
            val elapsed = System.currentTimeMillis() - startedAt
            setVolume(AlarmFade.volumeAt(elapsed, fadeMs, target))
            if (elapsed >= fadeMs) break
            delay(FADE_STEP_MS)
        }
        // Only pin the final target if we weren't cancelled (a Dismiss cancels + restores volume).
        if (currentIsActive()) setVolume(target)
    }

    private fun currentIsActive(): Boolean = fadeJob?.isActive ?: true

    private suspend fun setVolume(volume: Float) = withContext(Dispatchers.Main) {
        val controller = mediaControllerManager.controllerFlow.first()
        runCatching { controller.volume = volume.coerceIn(0f, 1f) }
    }

    private companion object {
        const val FADE_STEP_MS = 200L
    }
}
