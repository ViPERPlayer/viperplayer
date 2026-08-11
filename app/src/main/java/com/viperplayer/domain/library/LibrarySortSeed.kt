package com.viperplayer.domain.library

import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * The persisted [SortOrder] for each library tab, prefetched once at cold start so the first content
 * load can render already-sorted (no DEFAULT-then-flip). See [seedLibrarySortOrders].
 */
data class LibrarySortSeed(
    val songs: SortOrder,
    val albums: SortOrder,
    val artists: SortOrder,
    val playlists: SortOrder,
)

/**
 * Read the current persisted [SortOrder] for each of the four library tabs.
 *
 * Uses [kotlinx.coroutines.flow.first] so the initial value is available synchronously to the caller
 * before it builds the first UI state — this is the seam that fixes the cold-start one-frame flip where
 * the list would otherwise render in DEFAULT order and then jump to the persisted order once DataStore's
 * first emission arrived. Kept as a plain suspend function (no Android deps) so it is unit-testable
 * against a fake [SettingsRepository].
 */
suspend fun seedLibrarySortOrders(settingsRepository: SettingsRepository): LibrarySortSeed =
    LibrarySortSeed(
        songs = settingsRepository.sortOrder(SortView.LIBRARY_SONGS).first(),
        albums = settingsRepository.sortOrder(SortView.LIBRARY_ALBUMS).first(),
        artists = settingsRepository.sortOrder(SortView.LIBRARY_ARTISTS).first(),
        playlists = settingsRepository.sortOrder(SortView.LIBRARY_PLAYLISTS).first(),
    )
