package com.viperplayer.local.mapper

import com.viperplayer.local.model.LocalAlbum
import com.viperplayer.local.model.LocalArtist
import com.viperplayer.local.model.LocalSong
import com.viperplayer.plugin.v1.Album
import com.viperplayer.plugin.v1.AlbumType
import com.viperplayer.plugin.v1.Artist
import com.viperplayer.plugin.v1.Artwork
import com.viperplayer.plugin.v1.Song

/**
 * Maps internal local models to plugin AIDL models.
 */
class LocalMapper {

    fun toSong(localSong: LocalSong): Song {
        return Song(
            id = "local:${localSong.contentUri}",
            title = localSong.title,
            artists = listOf(
                Artist(
                    id = "local:artist:${localSong.artistId}",
                    name = localSong.artistName
                )
            ),
            album = Album(
                id = "local:album:${localSong.albumId}",
                name = localSong.albumName,
                artists = listOf(
                    Artist(
                        id = "local:artist:${localSong.artistId}",
                        name = localSong.artistName
                    )
                ),
                artwork = localSong.albumArtUri?.let { uri ->
                    Artwork(thumbnail = uri.toString(), full = uri.toString())
                },
                releaseYear = localSong.year
            ),
            durationMs = localSong.duration,
            artwork = localSong.albumArtUri?.let { uri ->
                Artwork(thumbnail = uri.toString(), full = uri.toString())
            },
            trackNumber = localSong.trackNumber,
            isExplicit = false,
            isPlayable = true,
            requiresInternet = false, // Local files don't require internet
            releaseYear = localSong.year
        )
    }

    fun toAlbum(localAlbum: LocalAlbum): Album {
        val songs = localAlbum.songs.map { toSong(it) }
        
        return Album(
            id = "local:album:${localAlbum.id}",
            name = localAlbum.name,
            artists = listOf(
                Artist(
                    id = "local:artist:${localAlbum.artistId}",
                    name = localAlbum.artistName
                )
            ),
            artwork = localAlbum.artworkUri?.let { uri ->
                Artwork(thumbnail = uri.toString(), full = uri.toString())
            },
            releaseYear = localAlbum.year,
            trackCount = localAlbum.trackCount,
            type = determineAlbumType(localAlbum.trackCount),
            songs = songs
        )
    }

    fun toArtist(localArtist: LocalArtist): Artist {
        val topSongs = localArtist.songs.take(10).map { toSong(it) }
        val albums = localArtist.albums.map { album ->
            // Create album without songs list for artist view
            Album(
                id = "local:album:${album.id}",
                name = album.name,
                artists = listOf(
                    Artist(
                        id = "local:artist:${localArtist.id}",
                        name = localArtist.name
                    )
                ),
                artwork = album.artworkUri?.let { uri ->
                    Artwork(thumbnail = uri.toString(), full = uri.toString())
                },
                releaseYear = album.year,
                trackCount = album.trackCount,
                type = determineAlbumType(album.trackCount),
                songs = null // Don't include songs in artist view
            )
        }

        return Artist(
            id = "local:artist:${localArtist.id}",
            name = localArtist.name,
            artwork = null, // Local files don't typically have artist artwork
            topSongs = topSongs,
            albums = albums
        )
    }

    private fun determineAlbumType(trackCount: Int): AlbumType {
        return when {
            trackCount == 1 -> AlbumType.SINGLE
            trackCount <= 4 -> AlbumType.EP
            else -> AlbumType.ALBUM
        }
    }
}
