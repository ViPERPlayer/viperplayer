package com.viperplayer.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.viperplayer.alarm.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives alarm fires (and Dismiss actions). On [ACTION_FIRE] it:
 *  1. holds a short wakelock so the device stays awake long enough to start playback,
 *  2. starts playback of the alarm's content with a fade-in (via [AlarmPlaybackStarter]),
 *  3. posts the ringing notification (via [AlarmNotifier]),
 *  4. reschedules the NEXT occurrence for a repeating alarm, or disables a one-shot.
 *
 * The receiver is a thin dispatcher; all business logic lives in the injected managers/repository.
 * Uses [goAsync] to keep the process alive across the (suspending) work.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var playbackStarter: AlarmPlaybackStarter
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var notifier: AlarmNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return

        when (intent.action) {
            ACTION_FIRE -> handleFire(context, alarmId)
            ACTION_DISMISS -> handleDismiss(context, alarmId)
        }
    }

    private fun handleFire(context: Context, alarmId: Long) {
        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            try {
                val alarm = alarmRepository.getById(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                // Re-arm / disable the NEXT occurrence FIRST — before the (fade-length) playback work —
                // so a slow start or a timeout can never leave a repeating alarm un-rearmed or a
                // spent one-shot still enabled.
                if (alarm.isOneShot) {
                    alarmRepository.setEnabled(alarm.id, enabled = false)
                    scheduler.cancel(alarm.id)
                } else {
                    scheduler.schedule(alarm)
                }

                notifier.notify(alarm)

                // start() suspends only until playback is issued; the fade continues in the
                // app-scoped AlarmPlaybackStarter. Bound just the play-issue step by the wakelock.
                withTimeoutOrNull(START_TIMEOUT_MS) {
                    playbackStarter.start(alarm)
                }
            } catch (e: Exception) {
                Timber.e(e, "Alarm #$alarmId fire failed")
            } finally {
                if (wakeLock.isHeld) runCatching { wakeLock.release() }
                pendingResult.finish()
            }
        }
    }

    private fun handleDismiss(context: Context, alarmId: Long) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                notifier.cancel(alarmId)
                playbackStarter.stop()
            } catch (e: Exception) {
                Timber.w(e, "Alarm #$alarmId dismiss failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(START_TIMEOUT_MS)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.viperplayer.alarm.ACTION_FIRE"
        const val ACTION_DISMISS = "com.viperplayer.alarm.ACTION_DISMISS"
        const val EXTRA_ALARM_ID = "com.viperplayer.alarm.EXTRA_ALARM_ID"

        private const val WAKE_LOCK_TAG = "ViPERPlayer:AlarmWakeLock"
        // Enough to connect the MediaController and ISSUE playback. The fade-in runs afterwards in the
        // app-scoped AlarmPlaybackStarter (kept alive by the media foreground service), not here, so
        // this bound is independent of the (up to 60s) fade length.
        private const val START_TIMEOUT_MS = 30_000L
    }
}
