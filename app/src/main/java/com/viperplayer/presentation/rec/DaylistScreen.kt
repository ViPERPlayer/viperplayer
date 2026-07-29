package com.viperplayer.presentation.rec

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    // Roll the open daylist over as the time-of-day bucket changes (mirrors the Home card): re-derive on
    // every wall-clock minute tick / time or timezone change, so the screen never shows a stale slot.
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.onTimeChanged()
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        onDispose { context.unregisterReceiver(receiver) }
    }

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
