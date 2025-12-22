package com.viperplayer.domain.usecase.player

import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing player state.
 */
class GetPlayerStateUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    operator fun invoke(): Flow<PlayerState> = repository.playerState
}

/**
 * Use case for observing queue.
 */
class GetQueueUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    operator fun invoke(): Flow<List<Song>> = repository.queue
}

/**
 * Use case for playing a song.
 */
class PlaySongUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(song: Song) {
        repository.play(song)
    }
}

/**
 * Use case for playing a list of songs.
 */
class PlayAllSongsUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(songs: List<Song>, startIndex: Int = 0) {
        repository.playAll(songs, startIndex)
    }
}

/**
 * Use case for toggling play/pause.
 */
class TogglePlayPauseUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke() {
        repository.togglePlayPause()
    }
}

/**
 * Use case for skipping to next track.
 */
class SkipToNextUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke() {
        repository.skipToNext()
    }
}

/**
 * Use case for skipping to previous track.
 */
class SkipToPreviousUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke() {
        repository.skipToPrevious()
    }
}

/**
 * Use case for seeking.
 */
class SeekToUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(positionMs: Long) {
        repository.seekTo(positionMs)
    }
}

/**
 * Use case for adding to queue.
 */
class AddToQueueUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(song: Song) {
        repository.addToQueue(song)
    }
}

/**
 * Use case for setting shuffle.
 */
class SetShuffleUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setShuffle(enabled)
    }
}

/**
 * Use case for setting repeat mode.
 */
class SetRepeatModeUseCase @Inject constructor(
    private val repository: PlayerRepository
) {
    suspend operator fun invoke(mode: RepeatMode) {
        repository.setRepeatMode(mode)
    }
}

