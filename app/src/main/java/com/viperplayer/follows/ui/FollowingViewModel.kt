package com.viperplayer.follows.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.data.FollowedArtistsRepository
import com.viperplayer.follows.data.LatestRelease
import com.viperplayer.follows.data.LatestReleaseFetcher
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * ViewModel for the Following screen. Streams the followed artists (alphabetical by name) and
 * forwards unfollow to the repository. Also resolves an optional best-effort "latest release" line per
 * artist via [LatestReleaseFetcher] (throttled + in-memory-cached; never persisted) and merges it into
 * the emitted [FollowingUiModel]s. All list logic lives in the repository/ordering helper.
 */
@HiltViewModel
class FollowingViewModel @Inject constructor(
    private val repository: FollowedArtistsRepository,
    private val latestReleaseFetcher: LatestReleaseFetcher,
) : ViewModel() {

    // Resolved latest-release lines keyed by artist id, filled in lazily as artists appear.
    private val latestReleases = MutableStateFlow<Map<MediaId, LatestRelease>>(emptyMap())

    // Cap concurrent plugin calls so a large follow list doesn't fan out into a burst of requests.
    private val fetchPermits = Semaphore(MAX_CONCURRENT_FETCHES)

    // Ids we've already kicked off a fetch for, so each artist is only ever fetched once per process.
    private val requested = mutableSetOf<MediaId>()

    val uiModels: StateFlow<List<FollowingUiModel>> =
        combine(
            repository.followedArtists(FollowedArtistSort.NAME),
            latestReleases,
        ) { artists, releases ->
            ensureFetched(artists)
            artists.map { artist ->
                FollowingUiModel(artist = artist, latestRelease = releases[artist.mediaId])
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** Kick off a (throttled, once-per-id) latest-release fetch for any newly-seen artists. */
    private fun ensureFetched(artists: List<FollowedArtist>) {
        val toFetch = artists.map { it.mediaId }.filter { requested.add(it) }
        for (id in toFetch) {
            viewModelScope.launch {
                fetchPermits.withPermit {
                    val release = latestReleaseFetcher.latestRelease(id) ?: return@withPermit
                    latestReleases.update { it + (id to release) }
                }
            }
        }
    }

    fun unfollow(mediaId: MediaId) {
        viewModelScope.launch {
            repository.unfollow(mediaId)
        }
    }

    private companion object {
        const val MAX_CONCURRENT_FETCHES = 3
    }
}

/**
 * A followed artist plus its optional best-effort latest release (album + year), rendered as the
 * secondary line in the Following list. [latestRelease] is null while unresolved or when none could be
 * determined (unsupported source / offline / no albums).
 */
data class FollowingUiModel(
    val artist: FollowedArtist,
    val latestRelease: LatestRelease?,
)
