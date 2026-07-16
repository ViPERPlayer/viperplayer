package com.viperplayer.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.alarm.data.AlarmRepository
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.scheduling.AlarmScheduler
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmsUiState(
    val alarms: List<Alarm> = emptyList(),
    /** Playlists selectable as alarm content. */
    val playlists: List<Playlist> = emptyList(),
    /** False on Android 12+ when the user hasn't granted exact-alarm permission. */
    val canScheduleExact: Boolean = true,
)

/**
 * Holds all alarm-list state and mutation logic. The screen only renders this and forwards events —
 * every schedule/DB write goes through here (and the injected [AlarmScheduler]/[AlarmRepository]), so
 * no business logic leaks into the composables.
 */
@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    mediaLibraryRepository: MediaLibraryRepository,
) : ViewModel() {

    private val canScheduleExact = MutableStateFlow(scheduler.canScheduleExact())

    val uiState: StateFlow<AlarmsUiState> =
        combine(
            alarmRepository.alarms,
            mediaLibraryRepository.getLocalPlaylists(),
            canScheduleExact,
        ) { alarms, playlists, canExact ->
            AlarmsUiState(
                alarms = alarms,
                playlists = playlists,
                canScheduleExact = canExact,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlarmsUiState(canScheduleExact = canScheduleExact.value),
        )

    /** Re-check exact-alarm permission (e.g. after returning from the system settings screen). */
    fun refreshExactPermission() {
        canScheduleExact.value = scheduler.canScheduleExact()
    }

    /** Persist a new or edited alarm, then (re)arm it. */
    fun saveAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val id = alarmRepository.upsert(alarm)
            val saved = alarm.copy(id = id)
            if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(id)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            alarmRepository.delete(alarm)
        }
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepository.setEnabled(alarm.id, enabled)
            val updated = alarm.copy(enabled = enabled)
            if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
        }
    }
}
