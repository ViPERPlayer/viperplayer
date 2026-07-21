package com.viperplayer.data.player.resumption

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.viperplayer.domain.model.MediaId

/**
 * Android-side bridge between media3 [MediaItem]s and the pure [LastSessionItem] model.
 *
 * The mapping mirrors [com.viperplayer.data.player.MediaItemMapper]: a [MediaItem]'s `mediaId` is
 * the domain `MediaId` string and its metadata `extras` hold `pluginId`/`sourceId` plus cached
 * display fields. Rebuilt items carry an **empty** URI, exactly like the primary path, so
 * [com.viperplayer.data.player.ViperMediaSource] re-resolves the (possibly expired) stream lazily
 * on play — no pre-resolution needed for resumption.
 */
object LastSessionMediaMapper {

    /**
     * Extracts a [LastSessionItem] from a live [MediaItem], or `null` when it lacks the
     * plugin/source id extras (e.g. a non-plugin/browsable item). Reads only display metadata, so
     * this is safe to call for every queue entry when persisting.
     */
    fun fromMediaItem(mediaItem: MediaItem): LastSessionItem? {
        val extras = mediaItem.mediaMetadata.extras ?: return null
        val pluginId = extras.getString("pluginId")?.takeIf { it.isNotBlank() } ?: return null
        val sourceId = extras.getString("sourceId")?.takeIf { it.isNotBlank() } ?: return null
        val metadata = mediaItem.mediaMetadata
        return LastSessionItem(
            pluginId = pluginId,
            sourceId = sourceId,
            title = metadata.title?.toString() ?: extras.getString("title").orEmpty(),
            artist = metadata.artist?.toString() ?: extras.getString("artistName"),
            album = metadata.albumTitle?.toString() ?: extras.getString("albumName"),
            artworkUrl = extras.getString("artworkUrl") ?: metadata.artworkUri?.toString(),
            durationMs = metadata.durationMs ?: extras.getLong("durationMs").takeIf { it > 0L },
            isVideo = extras.getBoolean("isVideo", false),
        )
    }

    /**
     * Rebuilds a playable [MediaItem] from a persisted [LastSessionItem]. The `mediaId` is the
     * domain `MediaId` string so the session callback and [ViperMediaSource] can resolve the stream;
     * the URI is left empty on purpose (see class docs). Display metadata is restored so the
     * resumption card and now-playing UI render immediately, before any network call.
     */
    fun toMediaItem(item: LastSessionItem): MediaItem {
        val mediaId = MediaId.from(item.pluginId, item.sourceId).encode()
        val extras = Bundle().apply {
            putString("pluginId", item.pluginId)
            putString("sourceId", item.sourceId)
            putString("title", item.title)
            putString("artistName", item.artist)
            putString("albumName", item.album)
            putString("artworkUrl", item.artworkUrl)
            putLong("durationMs", item.durationMs ?: 0L)
            putBoolean("isVideo", item.isVideo)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(item.artworkUrl?.toUri())
            .setDurationMs(item.durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("") // resolved lazily by ViperMediaSource, mirroring MediaItemMapper
            .setMediaMetadata(metadata)
            .build()
    }
}
