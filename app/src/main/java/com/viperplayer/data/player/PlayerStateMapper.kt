package com.viperplayer.data.player

import androidx.media3.common.C
import androidx.media3.common.Player
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.PlaybackState
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song

/**
 * Mapper between Media3 Player state and domain PlayerState.
 */
object PlayerStateMapper {

    /**
     * Creates PlaybackInfo from a Player instance.
     * Does NOT include position, currentSong, or duration - these are separate.
     */
    fun createPlaybackInfo(controller: Player): PlaybackInfo {
        val playbackState = when {
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            controller.playbackState == Player.STATE_READY && controller.playWhenReady -> PlaybackState.PLAYING
            controller.playbackState == Player.STATE_READY && !controller.playWhenReady -> PlaybackState.PAUSED
            controller.playbackState == Player.STATE_ENDED -> PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }

        return PlaybackInfo(
            state = playbackState,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode.toRepeatMode(),
            volume = controller.volume,
            queueSize = controller.mediaItemCount,
            queuePosition = controller.currentMediaItemIndex.takeIf { it >= 0 } ?: 0
        )
    }

//    /**
//     * Converts Media3 Player state to domain PlaybackState.
//     */
//    fun Player.State.toPlaybackState(): PlaybackState = when (this) {
//        Player.STATE_IDLE -> PlaybackState.IDLE
//        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
//        Player.STATE_READY -> {
//            // If ready and playWhenReady, it's playing; otherwise paused
//            PlaybackState.PAUSED // This will be corrected by checking playWhenReady separately
//        }
//        Player.STATE_ENDED -> PlaybackState.STOPPED
//        else -> PlaybackState.IDLE
//    }

    /**
     * Converts Media3 repeat mode to domain RepeatMode.
     */
    fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_OFF -> RepeatMode.OFF
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    /**
     * Converts domain RepeatMode to Media3 repeat mode.
     */
    fun RepeatMode.toMedia3RepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    /**
     * Creates a domain PlayerState from MediaController.
     */
    fun createPlayerState(
        controller: Player,
        currentSong: Song?,
        queue: List<Song>
    ): PlayerState {
        val playbackState = when {
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            controller.playbackState == Player.STATE_READY && controller.playWhenReady -> PlaybackState.PLAYING
            controller.playbackState == Player.STATE_READY && !controller.playWhenReady -> PlaybackState.PAUSED
            controller.playbackState == Player.STATE_ENDED -> PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }

        return PlayerState(
            state = playbackState,
            currentSong = currentSong,
            positionMs = controller.currentPosition,
            durationMs = controller.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0),
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode.toRepeatMode(),
            volume = controller.volume,
            queueSize = queue.size,
            queuePosition = controller.currentMediaItemIndex.takeIf { it >= 0 } ?: 0
        )
    }
}

