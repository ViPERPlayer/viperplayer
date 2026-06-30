package com.viperplayer.domain.model

import androidx.compose.runtime.Immutable

/**
 * Full artist profile — the "detail" half of the former Artist god-model. Built only by the
 * artist-detail fetch ([com.viperplayer.data.mapper.PluginMapper] `toDomainDetail`) and consumed only
 * by the artist-detail screen/VM, the Android-Auto browse callback, and `saveArtist` (decomposed into
 * Room). Deliberately NOT a [MediaItem] and NOT @Serializable/@Parcelize — it is never placed in a nav
 * key, Bundle, or persisted as a blob.
 *
 * The heavy fields hold lightweight [Artist] refs (and [Song]s whose own `artists` are refs), so unlike
 * the old god-model this structurally cannot recurse.
 */
@Immutable
data class ArtistDetail(
    val id: MediaId,
    val name: String,
    val imageUrl: String? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val featuring: List<Playlist> = emptyList(),
    val appearsOn: List<MediaItem> = emptyList(),
    val similarArtists: List<Artist> = emptyList()
)

/** The lightweight ref half of this detail — for caching and cross-references. */
fun ArtistDetail.toArtist(): Artist = Artist(id = id, name = name, imageUrl = imageUrl)
