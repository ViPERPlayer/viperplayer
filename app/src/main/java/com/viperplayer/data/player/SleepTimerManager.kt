package com.viperplayer.data.player

import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A simple sleep timer: pauses playback after a chosen number of minutes. App-scoped ([Singleton]) so
 * it survives the player UI being closed. [activeMinutes] is the originally-selected duration while a
 * timer is armed (used to tick the selected option in the picker), or null when off.
 */
@Singleton
class SleepTimerManager @Inject constructor(
    private val playerRepository: PlayerRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _activeMinutes = MutableStateFlow<Int?>(null)
    val activeMinutes: StateFlow<Int?> = _activeMinutes.asStateFlow()

    /** Arm the timer for [minutes]; a non-positive value cancels it. */
    fun schedule(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        _activeMinutes.value = minutes
        job = scope.launch {
            delay(minutes * 60_000L)
            playerRepository.pause()
            _activeMinutes.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _activeMinutes.value = null
    }
}
