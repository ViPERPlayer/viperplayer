package com.viperplayer.presentation.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.viperplayer.data.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the home-screen widget in sync with playback.
 *
 * Connects a single long-lived [MediaController] to [PlaybackService] and registers a
 * [Player.Listener]; on track / metadata / play-state changes it re-renders the widget and pushes
 * it to every widget instance via [PlayerWidgetRenderer]. A process-wide singleton so at most one
 * controller is held.
 *
 * This is UI-adjacent glue for the widget, not app business logic — it only reflects player state
 * onto RemoteViews.
 */
internal object PlayerWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var starting = false

    // Cache the last loaded artwork so a play/pause toggle doesn't re-decode the bitmap.
    private var lastArtworkUri: String? = null
    private var cachedBitmap: Bitmap? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            refresh(reloadArtwork = true)

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
            refresh(reloadArtwork = true)

        override fun onIsPlayingChanged(isPlaying: Boolean) =
            refresh(reloadArtwork = false)
    }

    /**
     * Ensures the long-lived controller is connected and listening. Safe to call repeatedly (from
     * the provider's onUpdate/onEnabled and from the service). No-op once connected/connecting; when
     * already connected it pushes a fresh render so a just-added widget shows the current state.
     */
    @Synchronized
    fun ensureStarted(context: Context) {
        appContext = context.applicationContext
        if (controller != null) {
            refresh(reloadArtwork = true)
            return
        }
        if (starting) return
        starting = true

        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh(reloadArtwork = true)
            } catch (e: Exception) {
                Timber.w(e, "Widget updater failed to connect MediaController")
                controller = null
            } finally {
                starting = false
            }
        }, MoreExecutors.directExecutor())
    }

    /** Re-render the widget from the current controller state, optionally reloading artwork. */
    private fun refresh(reloadArtwork: Boolean) {
        val c = controller ?: return
        val ctx = appContext ?: return
        val uri = c.mediaMetadata.artworkUri?.toString()
        scope.launch {
            val bitmap = if (reloadArtwork || uri != lastArtworkUri) {
                lastArtworkUri = uri
                PlayerWidgetRenderer.loadArtwork(ctx, c).also { cachedBitmap = it }
            } else {
                cachedBitmap
            }
            PlayerWidgetRenderer.render(ctx, c, bitmap)
        }
    }
}
