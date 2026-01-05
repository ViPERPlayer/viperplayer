package com.viperplayer.data.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song

/**
 * Mapper between domain Song model and Media3 MediaItem.
 */
object MediaItemMapper {

    /**
     * Converts a domain Song to a Media3 MediaItem.
     */
    fun Song.toMediaItem(): MediaItem {
        val mediaId = id.toString()
        
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistName)
            .setAlbumTitle(albumName)
            .setArtworkUri(artworkUrl?.toUri())
            .setDurationMs(durationMs)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
//            .setIsExplicit(isExplicit)

        // Store domain model data in extras for later retrieval
        val extras = android.os.Bundle().apply {
            putString("pluginId", id.pluginId)
            putString("sourceId", id.sourceId)
            putString("title", title)
            putString("artistName", artistName)
            putString("albumName", albumName)
            putString("artworkUrl", artworkUrl)
            putLong("durationMs", durationMs ?: 0L)
        }

        metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId) // The ViperPlayerResolver will handle the actual URI resolution
            .setMediaMetadata(metadataBuilder.build())
//            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build()
    }

    /**
     * Converts a Media3 MediaItem back to a domain Song.
     * Note: This is a best-effort conversion as MediaItem may not contain all Song data.
     */
    fun MediaItem.toSong(): Song? {
        val metadata = mediaMetadata
        val extras = metadata.extras
        
        val mediaIdString = mediaId ?: return null
        
        // Try to parse MediaId from string format "pluginId:sourceId"
        val mediaId = try {
            MediaId.fromString(mediaIdString)
        } catch (e: Exception) {
            // If parsing fails, it might be a URI - create a default MediaId
            // Extract a reasonable identifier from the URI
            val identifier = try {
                val uri = mediaIdString.toUri()
                uri.lastPathSegment ?: uri.host ?: mediaIdString.hashCode().toString()
            } catch (e2: Exception) {
                mediaIdString.hashCode().toString()
            }
            MediaId("unknown", identifier)
        }

        // Try to get title from metadata or extras, with multiple fallbacks
        val title = metadata.title?.toString() 
            ?: extras?.getString("title")
            ?: metadata.displayTitle?.toString()
            ?: try {
                // Try to extract filename from URI as last resort
                val uri = mediaIdString.toUri()
                uri.lastPathSegment?.substringBefore("?")?.substringBefore("#")
            } catch (e: Exception) {
                null
            }
            ?: mediaIdString // Final fallback to mediaId string
        
        val artistName = metadata.artist?.toString() 
            ?: extras?.getString("artistName") 
            ?: "Unknown Artist"
        
        val albumName = metadata.albumTitle?.toString() 
            ?: extras?.getString("albumName") 
            ?: ""
        
        val artworkUrl = metadata.artworkUri?.toString() 
            ?: extras?.getString("artworkUrl")
        
        val durationMs = extras?.getLong("durationMs")?.takeIf { it > 0 }
//            ?: metadata.duration?.toMillis()?.takeIf { it > 0 }

        // Create minimal Artist and Album objects
        val artist = Artist(
            id = MediaId(mediaId.pluginId, "artist_${artistName.hashCode()}"),
            name = artistName
        )

        val album = if (albumName.isNotEmpty()) {
            Album(
                id = MediaId(mediaId.pluginId, "album_${albumName.hashCode()}"),
                name = albumName,
                artists = listOf(artist),
                artworkUrl = artworkUrl
            )
        } else {
            null
        }

        return Song(
            id = mediaId,
            title = title,
            artists = listOf(artist),
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            trackNumber = metadata.trackNumber?.toInt(),
            discNumber = metadata.discNumber?.toInt(),
            isExplicit = metadata.extras?.getBoolean("isExplicit") ?: false
        )
    }

    /**
     * Converts a list of Songs to MediaItems.
     */
    fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }
}

