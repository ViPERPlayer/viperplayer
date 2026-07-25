package com.viperplayer.data.player.cast

/**
 * The custom session-command channel the [com.viperplayer.data.player.PlaybackService] uses to tell
 * connected [androidx.media3.session.MediaController]s (i.e. the app UI layer) whether playback is
 * currently going to a Cast device. The standard [androidx.media3.common.Player] interface has no
 * "am I casting?" signal, so we broadcast this custom command on every cast connect/disconnect and
 * the app receives it in `MediaController.Listener.onCustomCommand`.
 */
object CastSessionCommands {
    /** Custom command action: casting state changed. Payload carries [EXTRA_IS_CASTING]. */
    const val ACTION_CASTING_CHANGED = "com.viperplayer.cast.CASTING_CHANGED"

    /** Boolean extra on [ACTION_CASTING_CHANGED]: whether a cast session is currently active. */
    const val EXTRA_IS_CASTING = "isCasting"
}
