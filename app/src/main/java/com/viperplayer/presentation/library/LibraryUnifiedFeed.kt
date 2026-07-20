package com.viperplayer.presentation.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song

/**
 * Build the Library's unified "all types" feed: songs, albums, artists and playlists interleaved into
 * a single list ordered by most-recently-played (newest first).
 *
 * Recency uses the only real play signal the app records — the chronological listening [history]
 * ([HistoryEntry.playedAt], epoch millis per play). For each media item we take the most recent play
 * that touches it:
 *  - a **song** matches a history entry whose song shares its [MediaId];
 *  - an **album** inherits the newest play among the songs credited to it (`song.album?.id`);
 *  - an **artist** inherits the newest play among the songs crediting it (`song.artists[].id`);
 *  - a **playlist** has no play history and therefore no recency timestamp.
 *
 * Items that carry a recency timestamp sort first, newest→oldest. Items with no play signal (never
 * played, plus every playlist) keep their incoming library order and follow, so nothing is fabricated
 * — an unplayed item is never assigned a fake time; it simply falls to the stable tail. Within equal
 * timestamps the incoming order is preserved (the sort is stable).
 *
 * This is a pure function (no Android / coroutine deps) so it is trivially unit-testable: a test hands
 * it plain lists and a `List<HistoryEntry>` and asserts on the resulting order.
 */
fun buildUnifiedRecencyFeed(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    history: List<HistoryEntry>,
): List<LibraryFeedItem> {
    // Most-recent play timestamp per song MediaId. History is newest-first, so the FIRST time we see a
    // song id is its most recent play; keep the max defensively in case ordering ever changes.
    val songLastPlayed = HashMap<MediaId, Long>()
    // The newest play that touches each album / artist, propagated from the plays of their songs.
    val albumLastPlayed = HashMap<MediaId, Long>()
    val artistLastPlayed = HashMap<MediaId, Long>()

    for (entry in history) {
        val at = entry.playedAt
        val song = entry.song
        songLastPlayed.merge(song.id, at, ::maxOf)
        song.album?.id?.let { albumLastPlayed.merge(it, at, ::maxOf) }
        for (ref in song.artists) {
            ref.id?.let { artistLastPlayed.merge(it, at, ::maxOf) }
        }
    }

    // Wrap every item and pair it with its recency timestamp (null when there is no play signal).
    // `index` records the incoming order so unplayed items keep a deterministic, stable tail order.
    data class Ranked(val item: LibraryFeedItem, val playedAt: Long?, val index: Int)

    var index = 0
    val ranked = ArrayList<Ranked>(songs.size + albums.size + artists.size + playlists.size)
    for (song in songs) {
        ranked.add(Ranked(LibraryFeedItem.SongItem(song), songLastPlayed[song.id], index++))
    }
    for (album in albums) {
        ranked.add(Ranked(LibraryFeedItem.AlbumItem(album), albumLastPlayed[album.id], index++))
    }
    for (artist in artists) {
        ranked.add(Ranked(LibraryFeedItem.ArtistItem(artist), artistLastPlayed[artist.id], index++))
    }
    for (playlist in playlists) {
        // Playlists carry no play history — always unranked (null), so they trail in library order.
        ranked.add(Ranked(LibraryFeedItem.PlaylistItem(playlist), null, index++))
    }

    // Played items first (newest → oldest); unplayed items after, in their original library order.
    // sortedWith is stable, so equal keys never reshuffle.
    return ranked
        .sortedWith(
            compareByDescending<Ranked> { it.playedAt != null }
                .thenByDescending { it.playedAt ?: Long.MIN_VALUE }
                .thenBy { it.index }
        )
        .map { it.item }
}
