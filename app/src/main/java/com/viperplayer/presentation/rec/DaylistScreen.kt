package com.viperplayer.presentation.rec

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController

/**
 * The full **Daylist** screen: the current time-of-day daylist as a titled, playable song list. Renders
 * the shared [RecommendationsScreen] and wires the per-song options sheet — all logic lives in
 * [DaylistViewModel]. The header title/description come from the generated daylist itself (not a fixed
 * string), so this screen leaves [RecUiState.title]/[RecUiState.subtitle] as the ViewModel set them.
 */
@Composable
fun DaylistScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onViewDetails: (MediaItem) -> Unit,
    onMoreLikeThis: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()

    RecommendationsScreen(
        state = uiState,
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
