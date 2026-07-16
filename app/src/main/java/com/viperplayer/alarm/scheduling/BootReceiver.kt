package com.viperplayer.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.viperplayer.alarm.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Re-arms all enabled alarms after a reboot (or app upgrade / time change) — AlarmManager schedules
 * do not survive a reboot, so without this the alarms would silently stop firing.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> reschedule()
        }
    }

    private fun reschedule() {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val alarms = alarmRepository.getEnabled()
                scheduler.rescheduleAll(alarms)
                Timber.d("Re-armed ${alarms.size} alarm(s) after boot/time change")
            } catch (e: Exception) {
                Timber.e(e, "Failed to re-arm alarms after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
