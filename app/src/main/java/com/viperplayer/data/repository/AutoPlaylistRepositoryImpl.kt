package com.viperplayer.data.repository

import android.content.Context
import com.viperplayer.R
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.data.stats.toPlayRecord
import com.viperplayer.domain.autoplaylist.AutoPlaylistGenerator
import com.viperplayer.domain.autoplaylist.AutoPlaylistType
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AutoPlaylistRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.stats.PlayRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Live implementation of [AutoPlaylistRepository]. Combines the reactive library sources
 * ([MediaLibraryRepository]) and the stats play-history ([PlayHistoryDao]) and delegates every bit of
 * ordering/limiting to the pure [AutoPlaylistGenerator]. No generation logic lives here — this class
 * only gathers the inputs, supplies `now`, and wraps the resulting song lists in [Playlist] headers.
 */
class AutoPlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playHistoryDao: PlayHistoryDao,
) : AutoPlaylistRepository {

    private val recordsFlow: Flow<List<PlayRecord>> = playHistoryDao.observeAll()
        .map { entities -> entities.map { it.toPlayRecord() } }

    override fun getAutoPlaylists(): Flow<List<Playlist>> = combine(
        mediaLibraryRepository.getSongsByDateAdded(),
        mediaLibraryRepository.getAllLikedSongs(),
        mediaLibraryRepository.getAllSavedSongs(),
        recordsFlow,
    ) { recentlyAdded, liked, library, records ->
        val hasHistory = records.isNotEmpty()
        AutoPlaylistType.entries.mapNotNull { type ->
            // Hide play-based playlists until there is any history to derive them from.
            if (type.usesPlayHistory && !hasHistory) return@mapNotNull null
            val songs = generate(type, recentlyAdded, liked, library, records)
            if (songs.isEmpty()) return@mapNotNull null
            header(type, songs.size, songs.firstNotNullOfOrNull { it.artworkUrl })
        }
    }.distinctUntilChanged()

    override fun getAutoPlaylist(type: AutoPlaylistType): Flow<Playlist> =
        getAutoPlaylistSongs(type).map { songs ->
            header(type, songs.size, songs.firstNotNullOfOrNull { it.artworkUrl }).copy(songs = songs)
        }

    override fun getAutoPlaylistSongs(type: AutoPlaylistType): Flow<List<Song>> = combine(
        mediaLibraryRepository.getSongsByDateAdded(),
        mediaLibraryRepository.getAllLikedSongs(),
        mediaLibraryRepository.getAllSavedSongs(),
        recordsFlow,
    ) { recentlyAdded, liked, library, records ->
        generate(type, recentlyAdded, liked, library, records)
    }.distinctUntilChanged()

    /** Dispatches to the matching pure generator, supplying the current time where needed. */
    private fun generate(
        type: AutoPlaylistType,
        recentlyAdded: List<Song>,
        liked: List<Song>,
        library: List<Song>,
        records: List<PlayRecord>,
    ): List<Song> {
        val now = System.currentTimeMillis()
        // On-device the play-history stores Song.id via MediaId.encode() (Uri-encoded), so join the
        // library on that exact key rather than the generator's pure default.
        val keyOf: (MediaId) -> String = { it.encode() }
        // For a history song missing from the library, rebuild its MediaId by DECODING the stored
        // (encoded) id via MediaId.decode — round-tripping MediaId.encode() so the plugin later gets the
        // original sourceId (e.g. a local `content://…` URI) rather than a percent-encoded one.
        // decode returns null on a genuinely malformed id, so fall back to the generator's pure parser.
        val mediaIdOf: (String) -> MediaId = { raw ->
            MediaId.decode(raw) ?: AutoPlaylistGenerator.parseMediaId(raw)
        }
        return when (type) {
            AutoPlaylistType.RECENTLY_ADDED -> AutoPlaylistGenerator.recentlyAdded(recentlyAdded)
            AutoPlaylistType.MOST_PLAYED ->
                AutoPlaylistGenerator.mostPlayed(records, library, keyOf = keyOf, mediaIdOf = mediaIdOf)
            AutoPlaylistType.RECENTLY_PLAYED ->
                AutoPlaylistGenerator.recentlyPlayed(records, library, keyOf = keyOf, mediaIdOf = mediaIdOf)
            AutoPlaylistType.FAVORITES -> AutoPlaylistGenerator.favorites(liked)
            AutoPlaylistType.FORGOTTEN -> AutoPlaylistGenerator.forgotten(
                records = records,
                library = library,
                notRecentBeforeMs = now - FORGOTTEN_STALENESS_MS,
                keyOf = keyOf,
                mediaIdOf = mediaIdOf,
            )
            // A per-open shuffle: seed with the current time so it varies each visit.
            AutoPlaylistType.SHUFFLE_ALL -> AutoPlaylistGenerator.shuffleAll(library, seed = now)
        }
    }

    /** Builds the virtual [Playlist] header for [type] with a localized name/description. */
    private fun header(type: AutoPlaylistType, songCount: Int, artworkUrl: String?): Playlist {
        val (nameRes, descRes) = labels(type)
        return Playlist(
            id = type.mediaId,
            name = context.getString(nameRes),
            description = context.getString(descRes),
            artworkUrl = artworkUrl,
            ownerName = null,
            songCount = songCount,
            isPublic = false,
            isEditable = false,
        )
    }

    private fun labels(type: AutoPlaylistType): Pair<Int, Int> = when (type) {
        AutoPlaylistType.RECENTLY_ADDED ->
            R.string.auto_playlist_recently_added to R.string.auto_playlist_recently_added_desc
        AutoPlaylistType.MOST_PLAYED ->
            R.string.auto_playlist_most_played to R.string.auto_playlist_most_played_desc
        AutoPlaylistType.RECENTLY_PLAYED ->
            R.string.auto_playlist_recently_played to R.string.auto_playlist_recently_played_desc
        AutoPlaylistType.FAVORITES ->
            R.string.auto_playlist_favorites to R.string.auto_playlist_favorites_desc
        AutoPlaylistType.FORGOTTEN ->
            R.string.auto_playlist_forgotten to R.string.auto_playlist_forgotten_desc
        AutoPlaylistType.SHUFFLE_ALL ->
            R.string.auto_playlist_shuffle_all to R.string.auto_playlist_shuffle_all_desc
    }

    private companion object {
        /** A song is "forgotten" if its last play is older than 30 days. */
        val FORGOTTEN_STALENESS_MS: Long = TimeUnit.DAYS.toMillis(30)
    }
}
