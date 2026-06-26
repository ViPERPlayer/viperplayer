package com.viperplayer.local.mapper

import com.viperplayer.local.model.LocalAlbum
import com.viperplayer.local.model.LocalArtist
import com.viperplayer.local.model.LocalSong
import com.viperplayer.plugin.model.Album
import com.viperplayer.plugin.model.AlbumType
import com.viperplayer.plugin.model.Artist
import com.viperplayer.plugin.model.Artwork
import com.viperplayer.plugin.model.Song

/**
 * Maps internal local models to plugin SDK models.
 *
 * Ids: a song's id is its `content://` URI string (so the host can resolve and play it directly),
 * while album/artist ids are the MediaStore album/artist ids. Local files are always offline, so
 * songs are emitted with `requiresInternet = false`.
 */
class LocalMapper {

    fun toSong(localSong: LocalSong): Song {
        val artwork = localSong.albumArtUri?.toArtwork()
        return Song(
            id = localSong.contentUri.toString(),
            title = localSong.title,
            artists = listOf(
                Artist(
                    id = localSong.artistId.toString(),
                    name = localSong.artistName,
                ),
            ),
            album = Album(
                id = localSong.albumId.toString(),
                name = localSong.albumName,
                artists = listOf(
                    Artist(
                        id = localSong.artistId.toString(),
                        name = localSong.artistName,
                    ),
                ),
                artwork = artwork,
                releaseYear = localSong.year,
            ),
            durationMs = localSong.duration,
            artwork = artwork,
            trackNumber = localSong.trackNumber,
            releaseYear = localSong.year,
            isExplicit = false,
            isPlayable = true,
            requiresInternet = false,
        )
    }

    fun toAlbum(localAlbum: LocalAlbum): Album {
        return Album(
            id = localAlbum.id.toString(),
            name = localAlbum.name,
            artists = listOf(
                Artist(
                    id = localAlbum.artistId.toString(),
                    name = localAlbum.artistName,
                ),
            ),
            artwork = localAlbum.artworkUri?.toArtwork(),
            releaseYear = localAlbum.year,
            trackCount = localAlbum.trackCount,
            type = determineAlbumType(localAlbum.trackCount),
            songs = localAlbum.songs.map { toSong(it) },
        )
    }

    fun toArtist(localArtist: LocalArtist): Artist {
        return Artist(
            id = localArtist.id.toString(),
            name = localArtist.name,
            artwork = null,
            topSongs = localArtist.songs.take(10).map { toSong(it) },
            albums = localArtist.albums.map { album -> toAlbumSummary(album, localArtist) },
        )
    }

    /** Album as it appears inside an artist view: no songs list, artist resolved to the parent. */
    private fun toAlbumSummary(album: LocalAlbum, artist: LocalArtist): Album {
        return Album(
            id = album.id.toString(),
            name = album.name,
            artists = listOf(
                Artist(
                    id = artist.id.toString(),
                    name = artist.name,
                ),
            ),
            artwork = album.artworkUri?.toArtwork(),
            releaseYear = album.year,
            trackCount = album.trackCount,
            type = determineAlbumType(album.trackCount),
        )
    }

    private fun determineAlbumType(trackCount: Int): AlbumType {
        return when {
            trackCount == 1 -> AlbumType.SINGLE
            trackCount <= 4 -> AlbumType.EP
            else -> AlbumType.ALBUM
        }
    }

    private fun android.net.Uri.toArtwork(): Artwork {
        val uri = toString()
        return Artwork(thumbnailUrl = uri, fullUrl = uri)
    }
}
