package com.viperplayer.local.mapper

import com.viperplayer.local.model.LocalAlbum
import com.viperplayer.local.model.LocalArtist
import com.viperplayer.local.model.LocalSong
import com.viperplayer.plugin.v1.Album
import com.viperplayer.plugin.v1.Artist
import com.viperplayer.plugin.v1.Artwork
import com.viperplayer.plugin.v1.Song

/**
 * Maps internal local models to plugin AIDL models.
 */
class LocalMapper {

    fun toSong(localSong: LocalSong): Song {
        return Song().apply {
            id = "local:${localSong.contentUri}"
            title = localSong.title
            artists = listOf(
                Artist().apply {
                    id = "local:artist:${localSong.artistId}"
                    name = localSong.artistName
                }
            )
            album = Album().apply {
                id = "local:album:${localSong.albumId}"
                name = localSong.albumName
                artists = listOf(
                    Artist().apply {
                        id = "local:artist:${localSong.artistId}"
                        name = localSong.artistName
                    }
                )
                artwork = localSong.albumArtUri?.let { uri ->
                    Artwork().apply {
                        thumbnail = uri.toString()
                        full = uri.toString()
                    }
                }
                releaseYear = localSong.year ?: 0
                hasReleaseYear = localSong.year != null
            }
            durationMs = localSong.duration
            hasDurationMs = true
            artwork = localSong.albumArtUri?.let { uri ->
                Artwork().apply {
                    thumbnail = uri.toString()
                    full = uri.toString()
                }
            }
            trackNumber = localSong.trackNumber
            hasTrackNumber = true
            isExplicit = false
            isPlayable = true
            requiresInternet = false
            releaseYear = localSong.year ?: 0
            hasReleaseYear = localSong.year != null
        }
    }

    fun toAlbum(localAlbum: LocalAlbum): Album {
        val songs = localAlbum.songs.map { toSong(it) }
        
        return Album().apply {
            id = "local:album:${localAlbum.id}"
            name = localAlbum.name
            artists = listOf(
                Artist().apply {
                    id = "local:artist:${localAlbum.artistId}"
                    name = localAlbum.artistName
                }
            )
            artwork = localAlbum.artworkUri?.let { uri ->
                Artwork().apply {
                    thumbnail = uri.toString()
                    full = uri.toString()
                }
            }
            releaseYear = localAlbum.year ?: 0
            hasReleaseYear = localAlbum.year != null
            trackCount = localAlbum.trackCount
            type = determineAlbumType(localAlbum.trackCount)
            this.songs = songs
        }
    }

    fun toArtist(localArtist: LocalArtist): Artist {
        val topSongs = localArtist.songs.take(10).map { toSong(it) }
        val albums = localArtist.albums.map { album ->
            // Create album without songs list for artist view
            Album().apply {
                id = "local:album:${album.id}"
                name = album.name
                artists = listOf(
                    Artist().apply {
                        id = "local:artist:${localArtist.id}"
                        name = localArtist.name
                    }
                )
                artwork = album.artworkUri?.let { uri ->
                    Artwork().apply {
                        thumbnail = uri.toString()
                        full = uri.toString()
                    }
                }
                releaseYear = album.year ?: 0
                hasReleaseYear = album.year != null
                trackCount = album.trackCount
                type = determineAlbumType(album.trackCount)
                this.songs = ArrayList()
            }
        }

        return Artist().apply {
            id = "local:artist:${localArtist.id}"
            name = localArtist.name
            artwork = null
            this.topSongs = topSongs
            this.albums = albums
            this.playlists = ArrayList()
            this.featuring = ArrayList()
            this.appearsOn = ArrayList()
            this.similarArtists = ArrayList()
        }
    }

    private fun determineAlbumType(trackCount: Int): Byte {
        return when {
            trackCount == 1 -> Album.AlbumType.SINGLE
            trackCount <= 4 -> Album.AlbumType.EP
            else -> Album.AlbumType.ALBUM
        }
    }
}
