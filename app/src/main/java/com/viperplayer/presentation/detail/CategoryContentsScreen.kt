package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.ErrorState
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.common.revealOnAppear
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.launch

/**
 * Browse a category's contents: the items a plugin returns for one of Home's "Browse categories" tiles,
 * rendered with the shared search rows. Tapping a song plays it; tapping an album / artist / playlist
 * opens its detail. The list paginates via the plugin's cursor as it scrolls. Renders state only — all
 * data + playback logic lives in [CategoryContentsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryContentsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onViewDetails: (MediaItem) -> Unit,
    onMoreLikeThis: (Song) -> Unit = {},
    viewModel: CategoryContentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()
    val scope = rememberCoroutineScope()

    // Resolve a tapped row to its natural action: play a song, or open the album / artist / playlist.
    val onItemActivated: (SearchItem) -> Unit = { item ->
        when (item.type) {
            SearchItem.Type.SONG -> viewModel.playSong(item.id)
            SearchItem.Type.ALBUM -> onNavigateToAlbum(Album(id = item.id, name = item.title))
            SearchItem.Type.ARTIST -> onNavigateToArtist(Artist(id = item.id, name = item.title))
            SearchItem.Type.PLAYLIST -> onNavigateToPlaylist(Playlist(id = item.id, name = item.title))
        }
    }

    // Resolve a row to its full domain media (suspending) and open the shared options sheet.
    val openOptions: (SearchItem) -> Unit = { item ->
        scope.launch {
            val media = when (item.type) {
                SearchItem.Type.SONG -> item.song ?: viewModel.getSong(item.id)
                SearchItem.Type.ALBUM -> viewModel.getAlbum(item.id)
                SearchItem.Type.ARTIST -> viewModel.getArtist(item.id)
                SearchItem.Type.PLAYLIST -> viewModel.getPlaylist(item.id)
            }
            media?.let { optionsController.show(it) }
        }
    }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.name.ifBlank { stringResource(R.string.category_title_fallback) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (val state = uiState) {
                is CategoryContentsUiState.Loading -> LoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is CategoryContentsUiState.Empty -> Text(
                    text = stringResource(R.string.category_empty),
                    modifier = Modifier.align(Alignment.Center),
                )

                is CategoryContentsUiState.Error -> ErrorState(
                    title = stringResource(R.string.action_error),
                    message = state.message,
                    onRetry = viewModel::load,
                )

                is CategoryContentsUiState.Success -> CategoryContentsList(
                    items = state.items,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onItemActivated = onItemActivated,
                    onMore = openOptions,
                    onPlayNext = { item ->
                        scope.launch {
                            val song = item.song ?: viewModel.getSong(item.id)
                            if (song != null) viewModel.playNext(song)
                        }
                    },
                    onAddToQueue = { item ->
                        scope.launch {
                            val song = item.song ?: viewModel.getSong(item.id)
                            if (song != null) viewModel.addToQueue(song)
                        }
                    },
                    onLoadMore = viewModel::loadMore,
                    contentPadding = rootPadding.bottom(),
                )
            }
        }

        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { scope.launch { if (it is Song) viewModel.playSong(it.id) } },
            onPlayNext = { scope.launch { if (it is Song) viewModel.playNext(it) } },
            onAddToQueue = { scope.launch { if (it is Song) viewModel.addToQueue(it) } },
            onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
            onViewArtist = onNavigateToArtist,
            onViewAlbum = onNavigateToAlbum,
            onViewDetails = onViewDetails,
            onMoreLikeThis = { if (it is Song) onMoreLikeThis(it) },
        )
        AddToPlaylistSheetHost(controller = addToPlaylistController)
    }
}

/**
 * The category's items as a flat list of shared search rows, with a trailing sentinel that requests the
 * next page ([onLoadMore]) once it scrolls into view. Song rows carry swipe play-next / add-to-queue.
 */
@Composable
private fun CategoryContentsList(
    items: List<SearchItem>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onItemActivated: (SearchItem) -> Unit,
    onMore: (SearchItem) -> Unit,
    onPlayNext: (SearchItem) -> Unit,
    onAddToQueue: (SearchItem) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> "${item.type}_${item.id}" },
            contentType = { _, item -> item.type },
        ) { index, item ->
            val isSong = item.type == SearchItem.Type.SONG
            ListItem(
                type = item.type,
                title = item.title,
                badges = item.badges,
                subtitle = item.subtitle,
                artworkUrl = item.artworkUrl,
                isActive = isSong && item.id == currentSongId,
                isPlaying = isPlaying,
                onClick = { onItemActivated(item) },
                onMoreClick = { onMore(item) },
                onLongClick = { onMore(item) },
                onPlayNext = if (isSong) ({ onPlayNext(item) }) else null,
                onAddToQueue = if (isSong) ({ onAddToQueue(item) }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .revealOnAppear(index),
            )
        }

        item(key = "load_more") {
            LaunchedEffect(Unit) { onLoadMore() }
        }
    }
}
