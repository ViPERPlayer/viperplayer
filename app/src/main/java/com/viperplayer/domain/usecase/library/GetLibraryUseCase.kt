package com.viperplayer.domain.usecase.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for getting library songs.
 */
class GetLibrarySongsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Song>> {
        return repository.getLibrarySongs(cursor, limit)
    }
}

/**
 * Use case for getting library albums.
 */
class GetLibraryAlbumsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Album>> {
        return repository.getLibraryAlbums(cursor, limit)
    }
}

/**
 * Use case for getting library artists.
 */
class GetLibraryArtistsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Artist>> {
        return repository.getLibraryArtists(cursor, limit)
    }
}

/**
 * Use case for getting library playlists.
 */
class GetLibraryPlaylistsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        cursor: String? = null,
        limit: Int = 50
    ): Result<PagedResult<Playlist>> {
        return repository.getLibraryPlaylists(cursor, limit)
    }
}

