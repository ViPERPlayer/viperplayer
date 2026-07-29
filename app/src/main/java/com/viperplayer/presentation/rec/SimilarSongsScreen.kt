package com.viperplayer.presentation.rec

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController

/**
 * "More like this" screen: the ranked similar songs for a seed track. Renders the shared
 * [RecommendationsScreen] and wires the shared per-song options sheet — all logic lives in
 * [SimilarSongsViewModel].
 */
@Composable
fun SimilarSongsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onViewDetails: (MediaItem) -> Unit,
    onMoreLikeThis: (Song) -> Unit,
    viewModel: SimilarSongsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()

    // "Similar to <title>" once the seed title is known, else a plain header.
    val headerTitle = if (uiState.title.isNotBlank()) {
        stringResource(R.string.rec_similar_to, uiState.title)
    } else {
        stringResource(R.string.rec_similar_songs_title)
    }

    RecommendationsScreen(
        state = uiState.copy(title = headerTitle),
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
