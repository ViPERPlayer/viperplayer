package com.viperplayer.data.player.cast

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service-side source of truth for whether playback is currently going to a Cast device.
 *
 * The [com.viperplayer.data.player.PlaybackService] updates this on every cast connect/disconnect
 * and broadcasts the change to already-connected controllers. A controller that connects *mid-cast*
 * (e.g. the app process is relaunched while casting) misses that broadcast, so
 * [com.viperplayer.data.player.ViperMediaLibrarySessionCallback.onPostConnect] reads the current
 * value here and sends it just to the newly-connected controller — otherwise the UI would think it
 * isn't casting and hide the "ViPER FX unavailable" notice while audio plays unprocessed.
 *
 * A plain volatile flag: both writer (service, main thread) and reader (callback, main thread) are on
 * the same thread, but volatile keeps it safe if that ever changes.
 */
@Singleton
class CastStateHolder @Inject constructor() {
    @Volatile
    var isCasting: Boolean = false
}
