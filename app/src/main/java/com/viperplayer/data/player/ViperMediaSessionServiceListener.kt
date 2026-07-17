package com.viperplayer.data.player

import androidx.media3.session.MediaSessionService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listener for [MediaSessionService] framework callbacks.
 *
 * The OS raises [onForegroundServiceStartNotAllowedException] when playback tries to start a foreground
 * service while the app is in a state that forbids it (e.g. resumed from the background without a valid
 * FGS exemption on Android 12+). There is no safe recovery — we can't force-start the service — so we
 * log it for diagnosis and let playback stay paused rather than crash.
 */
@Singleton
class ViperMediaSessionServiceListener @Inject constructor() : MediaSessionService.Listener {
    override fun onForegroundServiceStartNotAllowedException() {
        Timber.w("Foreground service start not allowed; playback service left un-started")
    }
}
