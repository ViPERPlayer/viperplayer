package com.viperplayer.data.lastfm

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.viperplayer.domain.lastfm.LastfmRepository
import com.viperplayer.domain.lastfm.ScrobbleEligibility
import com.viperplayer.domain.lastfm.ScrobbleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge from [com.viperplayer.data.player.PlaybackService] to Last.fm scrobbling. Translates a
 * media3 [MediaItem] into the pure [ScrobbleTrack] (reading the same `mediaMetadata.extras` the rest
 * of the player uses) and applies the [ScrobbleEligibility] rule before handing off to the
 * [LastfmRepository]. All work runs off the caller's (main) thread on an IO scope.
 *
 * Two hooks:
 *  - [onTrackStarted] — call from `onMediaItemTransition`: sends now-playing and records the start time.
 *  - [onSessionEnded] — call from `onPlaybackStatsReady`: decides eligibility from the REAL listened
 *    time media3 reports and submits a scrobble (with the recorded start timestamp).
 *
 * The start timestamp is captured on track start (the Last.fm scrobble timestamp is when the track
 * *started*), keyed by mediaId so it survives until the corresponding session-end signal arrives.
 */
@Singleton
class LastfmScrobbler @Inject constructor(
    private val repository: LastfmRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // UNIX-seconds start time of recently-started tracks, keyed by mediaId. media3 fires
    // onPlaybackStatsReady (session end) for a track AFTER the next track's onMediaItemTransition, so a
    // single "last" field would be clobbered before we can read it — hence a small bounded map. Access
    // is synchronized on itself; entries evict oldest-first past [MAX_TRACKED_STARTS].
    private val startTimes = object : LinkedHashMap<String, Long>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>): Boolean = size > MAX_TRACKED_STARTS
    }

    /**
     * Called when a new track starts playing. Records the start timestamp and (if enabled/authed)
     * fires `track.updateNowPlaying`.
     *
     * @param nowMs epoch millis to stamp the start with (defaults to the system clock).
     */
    @UnstableApi
    fun onTrackStarted(mediaItem: MediaItem?, nowMs: Long = System.currentTimeMillis()) {
        val item = mediaItem ?: return
        val track = item.toScrobbleTrack() ?: return
        synchronized(startTimes) { startTimes[item.mediaId] = nowMs / 1000L }
        scope.launch {
            runCatching { repository.updateNowPlaying(track) }
                .onFailure { Timber.w(it, "Last.fm now-playing failed") }
        }
    }

    /**
     * Called once per playback session (media3's `onPlaybackStatsReady`) with the ACTUAL time the
     * track was playing. Decides eligibility and, if it qualifies, submits a scrobble stamped with the
     * track's recorded start time (falling back to now − listened when the start wasn't captured).
     */
    @UnstableApi
    fun onSessionEnded(mediaItem: MediaItem, listenedMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val track = mediaItem.toScrobbleTrack() ?: return
        val durationMs = mediaItem.mediaMetadata.extras?.getLong("durationMs", 0L)?.coerceAtLeast(0L) ?: 0L
        if (!ScrobbleEligibility.isEligible(listenedMs, durationMs)) {
            Timber.d("Last.fm: below scrobble threshold (listened=$listenedMs, duration=$durationMs)")
            return
        }
        // Consume the recorded start (remove so a repeated mediaId can't reuse a stale stamp); fall back
        // to now − listened when it wasn't captured (e.g. the process was restarted mid-track).
        val recordedStart = synchronized(startTimes) { startTimes.remove(mediaItem.mediaId) }
        val start = recordedStart ?: ((nowMs - listenedMs).coerceAtLeast(0L) / 1000L)
        scope.launch {
            runCatching { repository.submitScrobble(track, start) }
                .onFailure { Timber.w(it, "Last.fm scrobble submit failed") }
        }
    }

    /**
     * Builds a [ScrobbleTrack] from a media item's metadata extras, or null when it lacks the artist +
     * title Last.fm requires. Mirrors the keys written by
     * [com.viperplayer.data.player.MediaItemMapper].
     */
    private fun MediaItem.toScrobbleTrack(): ScrobbleTrack? {
        val extras = mediaMetadata.extras
        val artist = extras?.getString("artistName")?.takeIf { it.isNotBlank() }
            ?: mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val title = extras?.getString("title")?.takeIf { it.isNotBlank() }
            ?: mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val album = extras?.getString("albumName")?.takeIf { it.isNotBlank() }
            ?: mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        val durationMs = extras?.getLong("durationMs", 0L) ?: 0L
        return ScrobbleTrack(
            artist = artist,
            track = title,
            album = album,
            durationSeconds = if (durationMs > 0L) durationMs / 1000L else null,
        )
    }

    private companion object {
        // Bounds the start-time map; a handful covers the current + a few pending session-end signals.
        const val MAX_TRACKED_STARTS = 8
    }
}
