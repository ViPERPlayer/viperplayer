package com.viperplayer.data.mapper

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.SearchSuggestionItem
import com.viperplayer.domain.model.SearchSuggestionType
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.v1.SearchSuggestionsItemV1
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import com.viperplayer.plugin.v1.Album as AlbumV1
import com.viperplayer.plugin.v1.AlbumType as AlbumTypeV1
import com.viperplayer.plugin.v1.Artist as ArtistV1
import com.viperplayer.plugin.v1.Playlist as PlaylistV1
import com.viperplayer.plugin.v1.Song as SongV1

/**
 * Mapper for converting plugin search suggestions models to domain models.
 */
object SearchSuggestionsMapper {
    fun SearchSuggestionsResultV1.toDomain(pluginId: String): SearchSuggestions {
        return SearchSuggestions(
            pluginId = pluginId,
            suggestions = this.suggestions,
            items = this.items.map { it.toDomainItem(pluginId) }
        )
    }

    private fun SearchSuggestionsItemV1.toDomainItem(pluginId: String): SearchSuggestionItem {
        return SearchSuggestionItem(
            type = when (this.type) {
                SearchSuggestionsItemV1.Type.SONG -> SearchSuggestionType.SONG
                SearchSuggestionsItemV1.Type.ARTIST -> SearchSuggestionType.ARTIST
                SearchSuggestionsItemV1.Type.ALBUM -> SearchSuggestionType.ALBUM
                SearchSuggestionsItemV1.Type.PLAYLIST -> SearchSuggestionType.PLAYLIST
            },
            song = this.song?.toDomainSong(pluginId),
            artist = this.artist?.toDomainArtist(pluginId),
            album = this.album?.toDomainAlbum(pluginId),
            playlist = this.playlist?.toDomainPlaylist(pluginId)
        )
    }

    private fun SongV1.toDomainSong(pluginId: String): Song {
        return Song(
            id = MediaId(pluginId = pluginId, sourceId = this.id),
            title = this.title,
            artists = this.artists.map { it.toDomainArtist(pluginId) },
            album = this.album?.toDomainAlbum(pluginId),
            durationMs = this.durationMs,
            artworkUrl = this.artworkUrl,
            trackNumber = this.trackNumber,
            discNumber = this.discNumber,
            isExplicit = this.isExplicit,
            isPlayable = this.isPlayable
        )
    }

    private fun ArtistV1.toDomainArtist(pluginId: String): Artist {
        return Artist(
            id = MediaId(pluginId = pluginId, sourceId = this.id),
            name = this.name,
            imageUrl = this.imageUrl,
            genres = this.genres,
            followerCount = this.followerCount,
            bio = this.bio
        )
    }

    private fun AlbumV1.toDomainAlbum(pluginId: String): Album {
        return Album(
            id = MediaId(pluginId = pluginId, sourceId = this.id),
            name = this.name,
            artists = this.artists.map { it.toDomainArtist(pluginId) },
            artworkUrl = this.artworkUrl,
            releaseYear = this.releaseYear,
            trackCount = this.trackCount,
            type = when (this.type) {
                AlbumTypeV1.ALBUM -> AlbumType.ALBUM
                AlbumTypeV1.SINGLE -> AlbumType.SINGLE
                AlbumTypeV1.EP -> AlbumType.EP
                AlbumTypeV1.COMPILATION -> AlbumType.COMPILATION
            }
        )
    }

    private fun PlaylistV1.toDomainPlaylist(pluginId: String): Playlist {
        return Playlist(
            id = MediaId(pluginId = pluginId, sourceId = this.id),
            name = this.name,
            description = this.description,
            artworkUrl = this.artworkUrl,
            ownerName = this.ownerName,
            songCount = this.songCount,
            isPublic = this.isPublic,
            isEditable = this.isEditable
        )
    }
}
