package com.viperplayer.data.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.viperplayer.domain.model.Song

/**
 * Mapper between domain Song model and Media3 MediaItem.
 */
object MediaItemMapper {

    /**
     * Converts a domain Song to a Media3 MediaItem.
     */
    fun Song.toMediaItem(): MediaItem {
        val mediaId = id.encode()

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistNames)
            .setAlbumTitle(album?.name)
            .setArtworkUri(artworkUrl?.toUri())
            .setDurationMs(durationMs)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setIsPlayable(true)
            .setIsBrowsable(false)
//            .setIsExplicit(isExplicit)

        // Store domain model data in extras for later retrieval
        val extras = Bundle().apply {
            putString("pluginId", id.routingPluginId)
            putString("sourceId", id.sourceId)
            putString("title", title)
            putString("artistName", artistNames)
            putString("albumName", album?.name)
            putString("artworkUrl", artworkUrl)
            putLong("durationMs", durationMs ?: 0L)
            putBoolean("isVideo", isVideo)
            replayGainDb?.let { putFloat("replayGainDb", it) }
            peakAmplitude?.let { putFloat("peakAmplitude", it) }
        }

        metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("") // required for exoplayer to not crash :)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * Converts a list of Songs to MediaItems.
     */
    fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

    /**
     * Marks this item as an auto-loaded suggestion (appended when the original queue ran out).
     * The player surfaces a "Suggested songs" context while one is playing.
     */
    fun MediaItem.asSuggestion(): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
            putBoolean(EXTRA_IS_SUGGESTION, true)
        }
        return buildUpon()
            .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    /** Whether this item was appended by autoplay rather than queued by the user. */
    val MediaItem.isSuggestion: Boolean
        get() = mediaMetadata.extras?.getBoolean(EXTRA_IS_SUGGESTION) == true

    private const val EXTRA_IS_SUGGESTION = "isSuggestion"
}

