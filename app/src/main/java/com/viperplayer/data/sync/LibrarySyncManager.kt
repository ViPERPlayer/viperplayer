package com.viperplayer.data.sync

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.data.sync.push.PushSyncManager
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Counts of items pulled from a plugin's account library into the local library. */
data class SyncResult(
    val songs: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlists: Int = 0,
) {
    val total: Int get() = songs + albums + artists + playlists
    val isEmpty: Boolean get() = total == 0
}

/**
 * One-way pull of a plugin's signed-in account library into the local (Room-backed) library:
 * pages through the plugin's library endpoints and persists + marks-saved each item. Resilient by
 * design — one bad item never aborts a sync, and paging is bounded by [MAX_PAGES].
 */
@Singleton
class LibrarySyncManager @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val pushSyncManager: PushSyncManager,
    private val statusStore: LibrarySyncStatusStore,
) : LibrarySync {
    private val _syncing = MutableStateFlow<Set<String>>(emptySet())
    /** Plugin ids currently syncing, for the UI to show progress. */
    override val syncing: StateFlow<Set<String>> = _syncing.asStateFlow()

    /**
     * Sync every currently-connected plugin's account library into the local library, sequentially.
     * Iteration lives here (not the ViewModel) so the UI only launches + observes [syncing]. Each
     * plugin's [syncPlugin] records its outcome into the status store; failures are isolated per
     * plugin. A no-op when no plugins are connected.
     */
    override suspend fun syncConnectedPlugins() {
        val plugins = pluginRepository.connectedPlugins.first()
        for (plugin in plugins) {
            runCatching { syncPlugin(plugin.info.id) }
                .onFailure { Timber.w(it, "Library sync failed for ${plugin.info.id}") }
        }
    }

    /**
     * Sync [pluginId]'s account library into the local library. Returns per-type counts. An empty
     * result (a source with no library) is a normal outcome, not an error.
     */
    suspend fun syncPlugin(pluginId: String): SyncResult = withContext(Dispatchers.IO) {
        _syncing.update { it + pluginId }
        try {
            // Two-way sync: flush any local changes queued for this plugin (PUSH) before pulling, so
            // a locally-driven like/follow isn't clobbered by the incoming remote snapshot below. A
            // disabled/unsupported plugin makes this a no-op.
            runCatching { pushSyncManager.drainPlugin(pluginId) }
                .onFailure { Timber.w(it, "Push drain before pull failed for $pluginId") }

            val playlists = syncType(
                pluginId = pluginId,
                fetch = { cursor -> pluginRepository.getLibraryPlaylists(pluginId, cursor, PAGE_SIZE).getOrNull() },
                persist = { playlist ->
                    mediaLibraryRepository.savePlaylist(playlist)
                    mediaLibraryRepository.setPlaylistSaved(playlist.id, true)
                },
            )

            val songs = syncType(
                pluginId = pluginId,
                fetch = { cursor -> pluginRepository.getLibrarySongs(pluginId, cursor, PAGE_SIZE).getOrNull() },
                persist = { song ->
                    mediaLibraryRepository.saveSong(song)
                    mediaLibraryRepository.setSongSaved(song.id, true)
                },
            )

            val albums = syncType(
                pluginId = pluginId,
                fetch = { cursor -> pluginRepository.getLibraryAlbums(pluginId, cursor, PAGE_SIZE).getOrNull() },
                persist = { album ->
                    mediaLibraryRepository.saveAlbum(album)
                    mediaLibraryRepository.setAlbumSaved(album.id, true)
                },
            )

            val artists = syncType(
                pluginId = pluginId,
                fetch = { cursor -> pluginRepository.getLibraryArtists(pluginId, cursor, PAGE_SIZE).getOrNull() },
                persist = { artist ->
                    mediaLibraryRepository.saveArtist(artist.toDetail())
                    mediaLibraryRepository.setArtistSaved(artist.id, true)
                },
            )

            SyncResult(songs = songs, albums = albums, artists = artists, playlists = playlists)
                .also {
                    Timber.d("Synced library for $pluginId: $it")
                    statusStore.record(it, System.currentTimeMillis())
                }
        } finally {
            _syncing.update { it - pluginId }
        }
    }

    /**
     * Page through [fetch] following its cursor until exhausted (or [MAX_PAGES]), persisting each
     * item via [persist]. Each item is wrapped in runCatching so one failure never aborts the sync.
     * Returns the number of items persisted.
     */
    private suspend fun <T> syncType(
        pluginId: String,
        fetch: suspend (cursor: String?) -> PagedResult<T>?,
        persist: suspend (T) -> Unit,
    ): Int {
        var count = 0
        var cursor: String? = null
        var page = 0
        do {
            val result = runCatching { fetch(cursor) }
                .onFailure { Timber.w(it, "Library page fetch failed for $pluginId") }
                .getOrNull() ?: break
            for (item in result.items) {
                runCatching { persist(item) }
                    .onSuccess { count++ }
                    .onFailure { Timber.w(it, "Failed to persist synced item for $pluginId") }
            }
            cursor = result.nextCursor
            page++
        } while (cursor != null && page < MAX_PAGES)
        return count
    }

    /** The library artist ref carries no detail; wrap it as a minimal [ArtistDetail] to persist. */
    private fun Artist.toDetail(): ArtistDetail =
        ArtistDetail(id = id, name = name, imageUrl = imageUrl)

    private companion object {
        /** Items requested per library page. */
        const val PAGE_SIZE = 50
        /** Safety cap on pages per library type, so a mis-behaving cursor can't loop forever. */
        const val MAX_PAGES = 50
    }
}
