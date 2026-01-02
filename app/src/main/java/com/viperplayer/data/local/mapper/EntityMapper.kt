package com.viperplayer.data.local.mapper

import com.viperplayer.data.local.entity.AlbumEntity
import com.viperplayer.data.local.entity.AlbumTypeConverter
import com.viperplayer.data.local.entity.ArtistEntity
import com.viperplayer.data.local.entity.PlaylistEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song

/**
 * Mappers between Room entities and domain models.
 */
object EntityMapper {
    
    // Artist mappings (requires genres to be loaded separately)
    fun ArtistEntity.toDomain(genres: List<String> = emptyList()): Artist {
        return Artist(
            id = MediaId(pluginId, sourceId),
            name = name,
            imageUrl = imageUrl,
            genres = genres,
            followerCount = followerCount,
            bio = bio
        )
    }
    
    fun Artist.toEntity(): ArtistEntity {
        return ArtistEntity(
            pluginId = id.pluginId,
            sourceId = id.sourceId,
            name = name,
            imageUrl = imageUrl,
            followerCount = followerCount,
            bio = bio,
            isLiked = false, // Will be updated separately
            isSaved = false,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    // Album mappings (requires artists to be loaded separately)
    fun AlbumEntity.toDomain(artists: List<Artist> = emptyList()): Album {
        return Album(
            id = MediaId(pluginId, sourceId),
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
            pluginId = id.pluginId,
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
    fun SongEntity.toDomain(album: Album? = null, artists: List<Artist> = emptyList()): Song {
        return Song(
            id = MediaId(pluginId, sourceId),
            title = title,
            artists = artists,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            trackNumber = trackNumber,
            discNumber = discNumber,
            isExplicit = isExplicit,
            isPlayable = isPlayable
        )
    }
    
    fun Song.toEntity(albumId: Long? = null): SongEntity {
        return SongEntity(
            pluginId = id.pluginId,
            sourceId = id.sourceId,
            title = title,
            albumId = albumId,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            trackNumber = trackNumber,
            discNumber = discNumber,
            isExplicit = isExplicit,
            isPlayable = isPlayable,
            isLiked = false,
            isSaved = false,
            isDownloaded = false,
            playCount = 0,
            lastPlayed = null,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    // Playlist mappings
    fun PlaylistEntity.toDomain(songs: List<Song>? = null): Playlist {
        return Playlist(
            id = MediaId(pluginId, sourceId),
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
            pluginId = id.pluginId,
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

