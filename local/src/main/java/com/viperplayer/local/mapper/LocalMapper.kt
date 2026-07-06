package com.viperplayer.local.mapper

import android.net.Uri
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
 *
 * Missing tags reach this layer as nulls (see LocalSong) and only get display fallbacks here.
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
                    name = localSong.artistName ?: UNKNOWN_ARTIST,
                ),
            ),
            album = Album(
                id = localSong.albumId.toString(),
                name = localSong.albumName ?: UNKNOWN_ALBUM,
                artists = listOf(
                    Artist(
                        id = localSong.artistId.toString(),
                        name = localSong.albumArtist ?: localSong.artistName ?: UNKNOWN_ARTIST,
                    ),
                ),
                artwork = artwork,
                releaseYear = localSong.year,
            ),
            durationMs = localSong.duration,
            artwork = artwork,
            trackNumber = localSong.trackNumber,
            discNumber = localSong.discNumber,
            releaseYear = localSong.year,
            genres = listOfNotNull(localSong.genre),
            isExplicit = false,
            isPlayable = true,
            requiresInternet = false,
            extras = buildMap {
                localSong.fileName?.let { put(EXTRA_FILE_NAME, it) }
                localSong.mimeType?.let { put(EXTRA_MIME_TYPE, it) }
            },
        )
    }

    fun toAlbum(localAlbum: LocalAlbum): Album {
        return Album(
            id = localAlbum.id.toString(),
            name = localAlbum.name ?: UNKNOWN_ALBUM,
            artists = listOf(localAlbum.displayArtist()),
            artwork = localAlbum.artworkUri?.toArtwork(),
            releaseYear = localAlbum.year,
            trackCount = localAlbum.trackCount,
            type = determineAlbumType(localAlbum),
            songs = localAlbum.songs.map { toSong(it) },
        )
    }

    fun toArtist(localArtist: LocalArtist): Artist {
        return Artist(
            id = localArtist.id.toString(),
            name = localArtist.name ?: UNKNOWN_ARTIST,
            artwork = null,
            topSongs = localArtist.songs.take(10).map { toSong(it) },
            albums = localArtist.albums.map { album -> toAlbumSummary(album) },
        )
    }

    /** Album as it appears inside an artist view: no songs list. */
    private fun toAlbumSummary(album: LocalAlbum): Album {
        return Album(
            id = album.id.toString(),
            name = album.name ?: UNKNOWN_ALBUM,
            artists = listOf(album.displayArtist()),
            artwork = album.artworkUri?.toArtwork(),
            releaseYear = album.year,
            trackCount = album.trackCount,
            type = determineAlbumType(album),
        )
    }

    /**
     * The artist credited on the album: the inferred album artist when known, "Various artists"
     * for multi-artist albums without one, otherwise the (single) song artist.
     */
    private fun LocalAlbum.displayArtist(): Artist {
        val songArtists = songs.mapNotNull { it.artistName }.distinct()
        val name = albumArtistName
            ?: if (songArtists.size > 1) VARIOUS_ARTISTS else songArtists.firstOrNull() ?: UNKNOWN_ARTIST
        val id = albumArtistId ?: songs.first().artistId
        return Artist(id = id.toString(), name = name)
    }

    private fun determineAlbumType(album: LocalAlbum): AlbumType {
        return when {
            album.isCompilation -> AlbumType.COMPILATION
            album.albumArtistName == null &&
                album.songs.mapNotNull { it.artistName }.distinct().size > 1 -> AlbumType.COMPILATION
            album.trackCount == 1 -> AlbumType.SINGLE
            album.trackCount <= 4 -> AlbumType.EP
            else -> AlbumType.ALBUM
        }
    }

    private fun Uri.toArtwork(): Artwork {
        val uri = toString()
        return Artwork(thumbnailUrl = uri, fullUrl = uri)
    }

    companion object {
        private const val UNKNOWN_ARTIST = "Unknown artist"
        private const val UNKNOWN_ALBUM = "Unknown album"
        private const val VARIOUS_ARTISTS = "Various artists"

        /** Extras keys the host can read from a local Song. */
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_MIME_TYPE = "mimeType"
    }
}
