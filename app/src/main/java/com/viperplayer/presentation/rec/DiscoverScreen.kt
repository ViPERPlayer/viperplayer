package com.viperplayer.presentation.rec

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController

/**
 * "Discover" screen: server-backed recommendations of songs the user does NOT already own (the
 * backend recommender, ranked against the on-device taste vector). Renders the shared
 * [RecommendationsScreen] and wires the per-song options sheet — all logic lives in [DiscoverViewModel].
 *
 * Backend ranking is the INTENDED experience here (not a degraded fallback), so the shared screen's
 * "showing related songs" note is suppressed (`usingFallback = false`) and a Discover subtitle + a
 * Discover-flavored empty message are supplied instead.
 */
@Composable
fun DiscoverScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onViewDetails: (MediaItem) -> Unit,
    onMoreLikeThis: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()

    RecommendationsScreen(
        state = uiState.copy(
            title = stringResource(R.string.discover_title),
            subtitle = uiState.subtitle ?: stringResource(R.string.discover_subtitle),
            // Backend ranking is intended here, so don't render the "related songs" fallback note.
            usingFallback = false,
        ),
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onPlayAll = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        onPlaySong = viewModel::playSong,
        onSongMore = { optionsController.show(it) },
        onSongPlayNext = viewModel::playNext,
        onSongAddToQueue = viewModel::addToQueue,
        onRetry = viewModel::retry,
        onThumbsUp = viewModel::thumbsUp,
        onThumbsDown = viewModel::thumbsDown,
        noResultsMessage = stringResource(R.string.discover_empty_no_results),
        modifier = modifier,
    )

    MediaItemOptionsSheetHost(
        controller = optionsController,
        onPlay = { if (it is Song) viewModel.playSong(it) },
        onPlayNext = { if (it is Song) viewModel.playNext(it) },
        onAddToQueue = { if (it is Song) viewModel.addToQueue(it) },
        onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
        onMoreLikeThis = { if (it is Song) onMoreLikeThis(it) },
        onViewDetails = onViewDetails,
    )
    AddToPlaylistSheetHost(controller = addToPlaylistController)
}
