package com.viperplayer.data.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class PlaybackService : MediaLibraryService(), MediaLibraryService.MediaLibrarySession.Callback {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    
    override fun onCreate() {
        super.onCreate()
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this).build()
        
        // Create MediaSession
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, this)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }
    
    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}

