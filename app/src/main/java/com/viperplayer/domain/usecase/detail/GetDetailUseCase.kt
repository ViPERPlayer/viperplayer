package com.viperplayer.domain.usecase.detail

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for getting song details.
 */
class GetSongUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(mediaId: MediaId): Result<Song> {
        return repository.getSong(mediaId)
    }
}

/**
 * Use case for getting album details.
 */
class GetAlbumUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(mediaId: MediaId): Result<Album> {
        return repository.getAlbum(mediaId)
    }
}

/**
 * Use case for getting artist details.
 */
class GetArtistUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(mediaId: MediaId): Result<Artist> {
        return repository.getArtist(mediaId)
    }
}

/**
 * Use case for getting playlist details.
 */
class GetPlaylistUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(mediaId: MediaId): Result<Playlist> {
        return repository.getPlaylist(mediaId)
    }
}

/**
 * Use case for getting artist songs.
 */
class GetArtistSongsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        artistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>> {
        return repository.getArtistSongs(artistId, cursor, limit)
    }
}

/**
 * Use case for getting artist albums.
 */
class GetArtistAlbumsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        artistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Album>> {
        return repository.getArtistAlbums(artistId, cursor, limit)
    }
}

/**
 * Use case for getting playlist songs.
 */
class GetPlaylistSongsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        playlistId: MediaId,
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>> {
        return repository.getPlaylistSongs(playlistId, cursor, limit)
    }
}

