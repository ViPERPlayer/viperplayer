package com.viperplayer.data.player

import androidx.media3.session.MediaSessionService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViperMediaSessionServiceListener @Inject constructor() : MediaSessionService.Listener {
    override fun onForegroundServiceStartNotAllowedException() {
        // TODO
    }
}