package com.viperplayer.data.player.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executor

/**
 * Owns the [CastPlayer] and drives the local↔cast handover for the media session.
 *
 * The [com.viperplayer.data.player.PlaybackService] hosts one [MediaLibrarySession] whose active
 * [Player] is swapped between the local [Player] (the DSP-enabled ExoPlayer) and this [CastPlayer]
 * when a cast session comes and goes. On each switch we transfer the queue (as freshly-resolved,
 * castable progressive [MediaItem]s), the current index, position and play-when-ready, following
 * media3's documented local↔cast switching pattern. The app's [MediaController] proxies whichever
 * player is active, so play/pause/queue/position all keep working across the switch unchanged.
 *
 * Because casting bypasses the local DSP chain entirely, [onCastingChanged] fires so the UI can show
 * the "ViPER FX unavailable while casting" notice.
 *
 * All player interaction happens on the main (application) thread; only stream URL resolution hops
 * to IO.
 */
class CastPlayerConnection(
    private val context: Context,
    private val localPlayer: Player,
    private val pluginDataSource: PluginDataSource,
    private val mainScope: CoroutineScope,
    /**
     * Executor the Cast framework uses to initialize the [CastContext] off the calling thread, so a
     * non-casting user's playback start never pays the (potentially heavy) Play-Services Cast init on
     * the main thread. Callbacks post back to the main thread via [mainScope].
     */
    private val initExecutor: Executor,
    /** Applies the swapped player to the media session (`mediaLibrarySession.setPlayer`). */
    private val setSessionPlayer: (Player) -> Unit,
    /** Reports whether we are currently casting so the UI can show the ViPER-FX-unavailable notice. */
    private val onCastingChanged: (isCasting: Boolean) -> Unit,
    /**
     * Invoked once per switch-to-cast when one or more queued tracks could not be cast (local/DRM/
     * header-authed/etc), with how many were dropped ([count]). [count] is 0 when the whole queue is
     * un-castable so nothing could be sent to the receiver.
     */
    private val onUncastableTracks: (count: Int, nothingCastable: Boolean) -> Unit,
) {
    /**
     * The Cast player, or null until (and unless) the Cast framework initializes. Stays null when
     * Google Play Services / the Cast framework is unavailable on this device, in which case casting
     * is simply never offered and the local player is always used. Assigned on the main thread once
     * the async [CastContext] init resolves.
     */
    var castPlayer: CastPlayer? = null
        private set

    /** True once we have handed the session over to the [castPlayer]. Main thread only. */
    private var isCasting = false

    /** In-flight queue-transfer coroutine, cancelled if another switch supersedes it. */
    private var transferJob: Job? = null

    /** True after [release]; guards the async init callback from resurrecting a dead connection. */
    private var released = false

    /**
     * Initializes the Cast framework off the main thread and, once ready, starts listening for cast
     * session availability. A no-op degradation (never assigns [castPlayer]) when Play Services / the
     * Cast framework is unavailable, so those devices simply never see casting.
     */
    fun start() {
        runCatching {
            CastContext.getSharedInstance(context, initExecutor)
                .addOnSuccessListener(initExecutor) { castContext ->
                    // Hop to the main thread: CastPlayer creation and session listening are main-only.
                    mainScope.launch { onCastContextReady(castContext) }
                }
                .addOnFailureListener(initExecutor) {
                    Timber.w(it, "Cast framework unavailable; casting disabled")
                }
        }.onFailure {
            Timber.w(it, "Cast framework unavailable; casting disabled")
        }
    }

    private fun onCastContextReady(castContext: CastContext) {
        if (released) return
        val player = runCatching { CastPlayer(castContext) }
            .onFailure { Timber.w(it, "Failed to create CastPlayer; casting disabled") }
            .getOrNull() ?: return
        castPlayer = player
        player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                Timber.i("Cast session available; switching to CastPlayer")
                switchToCast()
            }

            override fun onCastSessionUnavailable() {
                Timber.i("Cast session unavailable; switching back to local player")
                switchToLocal()
            }
        })
        // If a session is already live when we attach (e.g. a resumed session), reflect it.
        if (player.isCastSessionAvailable) {
            switchToCast()
        }
    }

    /** Releases the Cast player and its listener. */
    fun release() {
        released = true
        transferJob?.cancel()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
    }

    private fun switchToCast() {
        val castPlayer = castPlayer ?: return
        if (isCasting) return

        // Snapshot the local queue, but do NOT pause/swap yet — resolve the castable set first so we
        // never leave the session on an empty CastPlayer with dead controls when the whole queue is
        // un-castable (all local/DRM/header-authed).
        val transfer = snapshotTransfer(localPlayer)

        transferJob?.cancel()
        transferJob = mainScope.launch {
            val castItems = resolveCastItems(transfer)
            // A session may have arrived+departed during resolution; bail if it's gone.
            if (!castPlayer.isCastSessionAvailable) return@launch

            if (castItems.items.isEmpty()) {
                // Nothing in the queue can be cast: stay local, tell the user, keep isCasting=false.
                Timber.w("No castable tracks in the queue; staying on the local player")
                onUncastableTracks(0, /* nothingCastable = */ true)
                return@launch
            }
            if (castItems.skipped > 0) {
                onUncastableTracks(castItems.skipped, /* nothingCastable = */ false)
            }

            // Commit the switch now that we have a non-empty castable queue.
            isCasting = true
            onCastingChanged(true)
            localPlayer.pause() // don't keep playing on the phone while we cast
            setSessionPlayer(castPlayer)

            castPlayer.setMediaItems(castItems.items, castItems.startIndex, transfer.safePositionMs)
            castPlayer.playWhenReady = transfer.playWhenReady
            castPlayer.prepare()
        }
    }

    private fun switchToLocal() {
        if (!isCasting) return
        isCasting = false
        onCastingChanged(false)

        val castPlayer = castPlayer
        // Capture where the cast player is *before* tearing it down.
        val castMediaId = castPlayer?.currentMediaItem?.mediaId
        val castPosition = castPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val castPlayWhenReady = castPlayer?.playWhenReady ?: false

        transferJob?.cancel()
        castPlayer?.stop()
        castPlayer?.clearMediaItems()

        setSessionPlayer(localPlayer)

        // The local player still holds the ORIGINAL full queue (placeholder MediaItems that resolve
        // lazily via ViperMediaSource), which may include tracks that were dropped as un-castable —
        // so the cast player's index doesn't line up with the local queue. Map back by mediaId to
        // resume on the same track the receiver was on, at the same position. If the id can't be
        // found (or nothing was casting), leave the local player where it already is.
        if (castMediaId != null) {
            val localIndex = indexOfMediaId(localPlayer, castMediaId)
            if (localIndex >= 0) {
                localPlayer.seekTo(localIndex, castPosition)
            }
        }
        localPlayer.playWhenReady = castPlayWhenReady
        localPlayer.prepare()
    }

    /** First index in [player]'s queue whose item has [mediaId], or -1 if absent. */
    private fun indexOfMediaId(player: Player, mediaId: String): Int {
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == mediaId) return i
        }
        return -1
    }

    /**
     * A resolved cast queue: the surviving castable [items], the [startIndex] the current item maps
     * to after un-castable drops, and how many tracks were [skipped].
     */
    private data class CastItems(val items: List<MediaItem>, val startIndex: Int, val skipped: Int)

    /**
     * Resolves each queued item to a castable progressive [MediaItem], dropping ones that can't be
     * cast (local files, DRM/DASH/HLS/PCM, header-authed proxies). Returns the surviving items, the
     * index the currently playing item maps to among them (clamped), and the skipped count. Runs URL
     * resolution on IO.
     */
    private suspend fun resolveCastItems(transfer: PlayerTransfer): CastItems {
        val local = localPlayer
        val current = transfer.safeIndex
        val built = ArrayList<MediaItem>(transfer.mediaIds.size)
        // Where the currently-playing item lands among the survivors, or -1 if it was itself dropped.
        var currentSurvivorIndex = -1
        // Survivors with a raw index < current — used to pick the nearest kept item when the current
        // one was dropped.
        var survivorsBeforeCurrent = 0
        var skipped = 0
        for (i in transfer.mediaIds.indices) {
            val castItem = buildCastItem(local.getMediaItemAt(i))
            if (castItem == null) {
                skipped++
                continue
            }
            if (i == current) currentSurvivorIndex = built.size
            if (i < current) survivorsBeforeCurrent++
            built.add(castItem)
        }
        // If the current item survived, resume on it; otherwise fall back to the nearest kept item
        // that preceded it (clamped into range).
        val startIndex = if (built.isEmpty()) {
            0
        } else if (currentSurvivorIndex >= 0) {
            currentSurvivorIndex
        } else {
            survivorsBeforeCurrent.coerceIn(0, built.lastIndex)
        }
        return CastItems(built, startIndex, skipped)
    }

    /**
     * Resolve one queued placeholder item to a cast-ready [MediaItem] carrying a real progressive
     * URL + MIME, or null when the track can't be cast. The stream is resolved via the same
     * [PluginDataSource.getStream] path the local player uses; the resulting [CastEligibility]
     * verdict gates what we send.
     */
    private suspend fun buildCastItem(sourceItem: MediaItem): MediaItem? {
        val mediaId = MediaId.decode(sourceItem.mediaId) ?: return null
        val isVideo = sourceItem.mediaMetadata.extras?.getBoolean("isVideo") == true

        val resolved = withContext(Dispatchers.IO) {
            runCatching { pluginDataSource.getStream(mediaId, isVideo).getOrNull() }.getOrNull()
        }
        // A downloaded file short-circuits stream resolution in ViperMediaSource; here getStream
        // still runs, but such a track is a local file the receiver can't reach. We can't cheaply
        // re-run the download gate off the service, so rely on the stream verdict: a downloaded
        // track resolves to a UrlStream only if the plugin also has a remote URL, which is fine to
        // cast. If resolution fails entirely, treat as not castable.
        val verdict = CastEligibility.evaluate(resolved?.source, isDownloadedLocalFile = false)
        return when (verdict) {
            is CastEligibility.Result.Castable -> castMediaItem(sourceItem, verdict.url, verdict.mimeType)
            is CastEligibility.Result.NotCastable -> {
                Timber.d("Skipping un-castable track %s (%s)", mediaId, verdict.reason)
                null
            }
        }
    }

    /**
     * Build a [MediaItem] for the Cast receiver: the resolved progressive [url] + [mimeType], with
     * the display metadata copied from [sourceItem] so the receiver / notification shows the right
     * title, artist and artwork.
     */
    private fun castMediaItem(sourceItem: MediaItem, url: String, mimeType: String?): MediaItem {
        val source = sourceItem.mediaMetadata
        val metadata = MediaMetadata.Builder()
            .setTitle(source.title)
            .setArtist(source.artist)
            .setAlbumTitle(source.albumTitle)
            .setArtworkUri(source.artworkUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(sourceItem.mediaId)
            .setUri(url)
            .setMimeType(mimeType ?: MimeTypes.BASE_TYPE_AUDIO + "/*")
            .setMediaMetadata(metadata)
            .build()
    }

    /** Read the transportable state off [player] on the main thread. */
    private fun snapshotTransfer(player: Player): PlayerTransfer {
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        return PlayerTransfer(
            mediaIds = ids,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
        )
    }
}
