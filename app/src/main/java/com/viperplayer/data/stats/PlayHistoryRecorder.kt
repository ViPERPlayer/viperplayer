package com.viperplayer.data.stats

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.viperplayer.domain.stats.ListeningStatsAggregator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records counted plays into the stats database. Hooked from [com.viperplayer.data.player.PlaybackService]'s
 * `onPlaybackStatsReady` — media3 fires that once per playback session with the ACTUAL play time
 * (excludes pauses / buffering), which gives us a natural debounce: one continuous listen ⇒ one
 * event, and scrubbing / brief skips produce a tiny `listenedMs` that fails the threshold and is
 * dropped. The play/no-play decision is the pure [ListeningStatsAggregator.countsAsPlay].
 *
 * Writes happen on [Dispatchers.IO] off the caller's (main) thread. Track metadata is read straight
 * from the [MediaItem]'s `mediaMetadata.extras` (populated by
 * [com.viperplayer.data.player.MediaItemMapper]) so no DB lookup is needed and streaming-only tracks
 * are captured too.
 */
@Singleton
class PlayHistoryRecorder @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Considers recording a play for [mediaItem] that was listened to for [listenedMs].
     *
     * @param listenedMs actual play time in ms as reported by media3's PlaybackStats.
     * @param nowMs epoch millis to stamp the event with (defaults to the system clock).
     */
    @UnstableApi
    fun onSessionEnded(
        mediaItem: MediaItem,
        listenedMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val extras = mediaItem.mediaMetadata.extras
        val pluginId = extras?.getString("pluginId")
        val sourceId = extras?.getString("sourceId")
        // Without a real id we can't attribute the play; skip rather than store junk.
        if (pluginId.isNullOrBlank() || sourceId.isNullOrBlank()) {
            Timber.d("PlayHistoryRecorder: skipping event with no plugin/source id")
            return
        }

        val durationMs = extras.getLong("durationMs", 0L).coerceAtLeast(0L)
        if (!ListeningStatsAggregator.countsAsPlay(listenedMs, durationMs)) {
            Timber.d("PlayHistoryRecorder: below threshold (listened=$listenedMs, duration=$durationMs) — not counted")
            return
        }

        val entity = PlayHistoryEntity(
            timestamp = nowMs,
            mediaId = mediaItem.mediaId,
            title = extras.getString("title")
                ?: mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = extras.getString("artistName")
                ?: mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            album = extras.getString("albumName")
                ?: mediaItem.mediaMetadata.albumTitle?.toString(),
            pluginId = pluginId,
            durationMs = durationMs,
            listenedMs = listenedMs,
            artworkUrl = extras.getString("artworkUrl"),
        )

        scope.launch {
            runCatching { playHistoryDao.insert(entity) }
                .onFailure { Timber.e(it, "PlayHistoryRecorder: failed to insert play event") }
        }
    }
}
