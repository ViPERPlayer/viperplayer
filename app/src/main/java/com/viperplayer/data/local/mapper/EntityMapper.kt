package com.viperplayer.data.local.mapper

import com.viperplayer.data.local.entity.AlbumEntity
import com.viperplayer.data.local.entity.AlbumTypeConverter
import com.viperplayer.data.local.entity.ArtistEntity
import com.viperplayer.data.local.entity.PlaylistEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song

/** Room `idType` discriminator values for a persisted [MediaId]. Library rows are Plugin or Local. */
const val ID_TYPE_PLUGIN = "plugin"
const val ID_TYPE_LOCAL = "local"

/**
 * The (idType, pluginId) column pair for a persisted [MediaId]. `pluginId` is `""` for a [MediaId.Local]
 * so the `(idType, pluginId, sourceId)` unique index and equality queries work without NULL handling.
 * Radio ids are never persisted in the library tables.
 */
val MediaId.idType: String
    get() = when (this) {
        is MediaId.Plugin -> ID_TYPE_PLUGIN
        is MediaId.Local -> ID_TYPE_LOCAL
        is MediaId.Radio -> error("Radio MediaId is not persisted in the library database")
    }

/** The `pluginId` column value for a persisted [MediaId] ("" for local). */
val MediaId.entityPluginId: String
    get() = when (this) {
        is MediaId.Plugin -> pluginId
        is MediaId.Local -> ""
        is MediaId.Radio -> error("Radio MediaId is not persisted in the library database")
    }

/** Rebuilds a persisted library [MediaId] from its type-discriminated columns. */
fun mediaIdFromColumns(idType: String, pluginId: String, sourceId: String): MediaId =
    when (idType) {
        ID_TYPE_LOCAL -> MediaId.Local(sourceId)
        else -> MediaId.Plugin(pluginId, sourceId)
    }

/**
 * Mappers between Room entities and domain models.
 */
object EntityMapper {

    // Artist mappings
    // Artist is a lightweight ref (id/name/image); heavy fields live on ArtistDetail (plugin API only).
    // A domain [Artist] is always navigable, so this is only valid for a row with a real sourceId
    // (reached via getArtist(mediaId) lookups). Unlinked rows surface as an [ArtistRef] via [toRef].
    fun ArtistEntity.toDomain(): Artist {
        return Artist(
            id = mediaIdFromColumns(
                idType,
                pluginId,
                checkNotNull(sourceId) { "navigable artist row must have a sourceId" },
            ),
            name = name,
            imageUrl = imageUrl
        )
    }

    /** A linked byline ref for this entity artist (used when rebuilding a byline from cross-refs). */
    fun Artist.toRef(): ArtistRef = ArtistRef(name = name, id = id)

    /** A byline ref from a stored artist row: a null sourceId is an unlinked (null-id) ref. */
    fun ArtistEntity.toRef(): ArtistRef =
        ArtistRef(name = name, id = sourceId?.let { mediaIdFromColumns(idType, pluginId, it) })

    fun Artist.toEntity(): ArtistEntity {
        return ArtistEntity(
            idType = id.idType,
            pluginId = id.entityPluginId,
            sourceId = id.sourceId,
            name = name,
            imageUrl = imageUrl,
            isLiked = false, // Will be updated separately
            isSaved = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    // Album mappings (requires artists to be loaded separately)
    fun AlbumEntity.toDomain(artists: List<ArtistRef> = emptyList()): Album {
        return Album(
            id = mediaIdFromColumns(idType, pluginId, sourceId),
            name = name,
            artists = artists,
            artworkUrl = artworkUrl,
            releaseYear = releaseYear,
            trackCount = trackCount,
            type = AlbumTypeConverter.toDomainType(type)
        )
    }

    fun Album.toEntity(primaryArtistId: Long? = null): AlbumEntity {
        return AlbumEntity(
            idType = id.idType,
            pluginId = id.entityPluginId,
            sourceId = id.sourceId,
            name = name,
            primaryArtistId = primaryArtistId,
            artworkUrl = artworkUrl,
            releaseYear = releaseYear,
            trackCount = trackCount,
            type = AlbumTypeConverter.fromDomainType(type),
            isLiked = false,
            isSaved = false,
            isDownloaded = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    // Song mappings (requires album and artists to be loaded separately)
    // Note: isPlayable is NOT stored in the database - it's computed at runtime
    // Note: requiresInternet defaults to true (for streaming), but can be set by plugins
    fun SongEntity.toDomain(
        album: Album? = null,
        artists: List<ArtistRef> = emptyList(),
        isPlayable: Boolean = true,
        requiresInternet: Boolean = true
    ): Song {
        // Use local artwork path if available (convert to file:// URI), otherwise fall back to remote URL
        val effectiveArtworkUrl = localArtworkPath?.let {
            if (it.startsWith("file://")) it else "file://$it"
        } ?: artworkUrl

        return Song(
            id = mediaIdFromColumns(idType, pluginId, sourceId),
            title = title,
            artists = artists,
            album = album,
            durationMs = durationMs,
            artworkUrl = effectiveArtworkUrl,
            trackNumber = trackNumber,
            discNumber = discNumber,
            isExplicit = isExplicit,
            isPlayable = isPlayable,
            requiresInternet = requiresInternet,
            replayGainDb = replayGainDb,
            peakAmplitude = peakAmplitude,
            isLiked = isLiked,
            isDownloaded = isDownloaded,
            isVideo = isVideo,
        )
    }

    fun Song.toEntity(albumId: Long? = null): SongEntity {
        return SongEntity(
            idType = id.idType,
            pluginId = id.entityPluginId,
            sourceId = id.sourceId,
            title = title,
            albumId = albumId,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            trackNumber = trackNumber,
            discNumber = discNumber,
            isExplicit = isExplicit,
            isLiked = isLiked,
            isSaved = false,
            isDownloaded = isDownloaded,
            isVideo = isVideo,
            downloadPath = null,
            localArtworkPath = null,
            playCount = 0,
            lastPlayed = null,
            lastUpdated = System.currentTimeMillis(),
            replayGainDb = replayGainDb,
            peakAmplitude = peakAmplitude,
        )
    }

    // Playlist mappings
    fun PlaylistEntity.toDomain(songs: List<Song>? = null): Playlist {
        return Playlist(
            id = mediaIdFromColumns(idType, pluginId, sourceId),
            name = name,
            description = description,
            artworkUrl = artworkUrl,
            ownerName = ownerName,
            songCount = songCount,
            isPublic = isPublic,
            isEditable = isEditable,
            songs = songs
        )
    }

    fun Playlist.toEntity(): PlaylistEntity {
        return PlaylistEntity(
            idType = id.idType,
            pluginId = id.entityPluginId,
            sourceId = id.sourceId,
            name = name,
            description = description,
            artworkUrl = artworkUrl,
            ownerName = ownerName,
            songCount = songCount,
            isPublic = isPublic,
            isEditable = isEditable,
            isLiked = false,
            isSaved = false,
            isDownloaded = false,
            lastUpdated = System.currentTimeMillis()
        )
    }
}

