package com.viperplayer.presentation.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.viperplayer.R
import com.viperplayer.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Collection-widget data source for the playlist / quick-launch widget. Builds one [RemoteViews]
 * row per playlist (Liked Songs first, then the user's local playlists) with a per-row fill-in
 * intent carrying the encoded playlist id, which [PlaylistWidgetProvider] turns into playback.
 *
 * A widget has no ViewModel; the data is read from the app's singleton repositories via
 * [WidgetEntryPoint]. All repository reads happen on [onDataSetChanged]'s background thread (the
 * framework never calls it on the main thread).
 */
class PlaylistWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PlaylistRemoteViewsFactory(applicationContext)
}

private class PlaylistRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var entries: List<PlaylistWidgetEntry> = emptyList()
    private val artwork = HashMap<String, Bitmap>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .mediaLibraryRepository()
        entries = try {
            runBlocking {
                val liked = repository.getLikedSongsPlaylist().first()
                val locals = repository.getLocalPlaylists().first()
                PlaylistWidgetData.select(liked, locals)
            }
        } catch (e: Exception) {
            Timber.w(e, "Playlist widget failed to load playlists")
            emptyList()
        }
        // Decode a small thumbnail per row so the list renders artwork; ignore failures.
        artwork.clear()
        entries.forEach { entry ->
            val url = entry.artworkUrl ?: return@forEach
            runCatching {
                runBlocking {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .build()
                    context.imageLoader.execute(request).image?.toBitmap()
                }
            }.getOrNull()?.let { artwork[entry.mediaId.encode()] = it }
        }
    }

    override fun onDestroy() {
        entries = emptyList()
        artwork.clear()
    }

    override fun getCount(): Int = entries.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist_item)
        val entry = entries.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_playlist_item_name, entry.name)
        views.setTextViewText(
            R.id.widget_playlist_item_count,
            context.getString(R.string.widget_playlists_song_count, entry.songCount)
        )
        val encodedId = entry.mediaId.encode()
        val bitmap = artwork[encodedId]
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_playlist_item_artwork, bitmap)
        } else {
            views.setImageViewResource(
                R.id.widget_playlist_item_artwork,
                R.drawable.ic_widget_album_placeholder
            )
        }
        // Per-row fill-in intent: the collection's template pending intent supplies the action.
        val fillIn = Intent().putExtra(PlaylistWidgetProvider.EXTRA_PLAYLIST_ID, encodedId)
        views.setOnClickFillInIntent(R.id.widget_playlist_item_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        entries.getOrNull(position)?.mediaId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
