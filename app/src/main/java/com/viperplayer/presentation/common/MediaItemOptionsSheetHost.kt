package com.viperplayer.presentation.common

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaItem

/**
 * Holds the media item whose options sheet is showing. Replaces the `var selectedMediaItem` that was
 * copy-pasted into every browse screen. Drive it with [show]/[dismiss].
 */
@Stable
class MediaItemOptionsController {
    var selected by mutableStateOf<MediaItem?>(null)
        private set

    fun show(item: MediaItem) {
        selected = item
    }

    fun dismiss() {
        selected = null
    }
}

@Composable
fun rememberMediaItemOptionsController(): MediaItemOptionsController =
    remember { MediaItemOptionsController() }

/**
 * Shared host for the media-item options bottom sheet. A screen drives it with a controller
 * (`controller.show(item)` at the more/long-press sites) and supplies only the real per-item actions;
 * this owns the [ModalBottomSheet] wiring and auto-dismisses after every action.
 *
 * Previously ~40–50 lines of identical host wiring (plus a `selectedMediaItem = null` after each
 * callback) were duplicated across Search / Library / Album / Artist / Playlist screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaItemOptionsSheetHost(
    controller: MediaItemOptionsController,
    onPlay: (MediaItem) -> Unit = {},
    onPlayNext: (MediaItem) -> Unit = {},
    onAddToQueue: (MediaItem) -> Unit = {},
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onShuffle: (MediaItem) -> Unit = {},
    onStartRadio: (MediaItem) -> Unit = {},
    onMoreLikeThis: (MediaItem) -> Unit = {},
    onLike: (MediaItem) -> Unit = {},
    onDownload: (MediaItem) -> Unit = {},
    onShare: (MediaItem) -> Unit = {},
    onViewArtist: (Artist) -> Unit = {},
    onViewAlbum: (Album) -> Unit = {},
    onViewDetails: (MediaItem) -> Unit = {},
) {
    val item = controller.selected ?: return
    ModalBottomSheet(
        onDismissRequest = controller::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        MediaItemOptionsBottomSheet(
            item = item,
            onDismiss = controller::dismiss,
            onPlay = { onPlay(item); controller.dismiss() },
            onPlayNext = { onPlayNext(item); controller.dismiss() },
            onAddToQueue = { onAddToQueue(item); controller.dismiss() },
            onAddToPlaylist = { onAddToPlaylist(item); controller.dismiss() },
            onShuffle = { onShuffle(item); controller.dismiss() },
            onStartRadio = { onStartRadio(item); controller.dismiss() },
            onMoreLikeThis = { controller.dismiss(); onMoreLikeThis(item) },
            onLike = { onLike(item); controller.dismiss() },
            onDownload = { onDownload(item); controller.dismiss() },
            onShare = { onShare(item); controller.dismiss() },
            onViewArtist = { controller.dismiss(); onViewArtist(it) },
            onViewAlbum = { controller.dismiss(); onViewAlbum(it) },
            onViewDetails = { onViewDetails(item); controller.dismiss() },
        )
    }
}
