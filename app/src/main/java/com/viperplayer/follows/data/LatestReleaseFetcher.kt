package com.viperplayer.follows.data

import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A followed artist's most-recent release, synthesised best-effort from the plugin's album list.
 *
 * NOTE: only the release [year] is available from any plugin — there is no release-note / description
 * prose in the plugin API — so this is deliberately minimal (album name + year). Callers format it for
 * display (see `following_latest_release`).
 */
data class LatestRelease(
    val albumName: String,
    val year: Int?,
)

/**
 * Best-effort "latest release" lookup for followed artists, backing the optional second line on the
 * Following screen. NOT persisted (never touches the follows Room DB): it hits the source plugin on
 * demand and caches the result in memory for the process lifetime, degrading silently to `null` on
 * failure / unsupported source / offline.
 *
 * Purely a data-layer helper (I/O over [PluginDataSource]) — the ViewModel throttles/limits how many
 * artists it asks about; this class just resolves one id at a time and memoises.
 */
@Singleton
class LatestReleaseFetcher @Inject constructor(
    private val pluginDataSource: PluginDataSource,
) {
    // Process-lifetime memo: a resolved entry (present or absent) is never re-fetched, so scrolling the
    // list back and forth costs one plugin call per artist at most.
    private val cache = ConcurrentHashMap<MediaId, LatestRelease?>()
    private val marker = ConcurrentHashMap<MediaId, Boolean>()
    private val locks = ConcurrentHashMap<MediaId, Mutex>()

    /**
     * The latest release for [artistId], or null when none can be determined (no albums, plugin error,
     * unsupported source, or offline). Cached after the first resolution. Fetches at most [limit]
     * albums (a small first page is enough — most plugins return newest-first, and we max-by year
     * regardless of order).
     */
    suspend fun latestRelease(artistId: MediaId, limit: Int = DEFAULT_LIMIT): LatestRelease? {
        if (marker.containsKey(artistId)) return cache[artistId]
        // Serialise concurrent lookups of the same id so we make one plugin call, not N.
        val lock = locks.getOrPut(artistId) { Mutex() }
        return lock.withLock {
            if (marker.containsKey(artistId)) return@withLock cache[artistId]
            val resolved = pluginDataSource.getArtistAlbums(artistId, cursor = null, limit = limit)
                .getOrNull()
                ?.items
                ?.maxByOrNull { it.releaseYear ?: Int.MIN_VALUE }
                ?.let { LatestRelease(albumName = it.name, year = it.releaseYear) }
            cache[artistId] = resolved
            marker[artistId] = true
            locks.remove(artistId)
            resolved
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
    }
}
