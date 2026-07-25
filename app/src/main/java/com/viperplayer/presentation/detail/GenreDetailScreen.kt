package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.SortMenu
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.library.EmptyLibraryContent
import com.viperplayer.presentation.library.SongRow

// Genre song-list sort options. DEFAULT (name order from the query) is first and selected initially.
private val GENRE_SONG_SORT_OPTIONS = listOf(
    SortOption.DEFAULT,
    SortOption.TITLE,
    SortOption.ARTIST,
    SortOption.ALBUM,
    SortOption.DURATION,
)

/**
 * Genre detail: the local library songs tagged with a genre, with play / shuffle affordances, a sort
 * menu, and the shared per-song options sheet. Mirrors [AlbumDetailScreen] but without artwork (a genre
 * has none). Renders state only — all data + playback logic lives in [GenreDetailViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    viewModel: GenreDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()

    ViperScaffold(
        modifier = Modifier.padding(rootPadding.bottom()),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.name,
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
            when {
                uiState.isLoading -> LoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.songs.isEmpty() -> EmptyLibraryContent(
                    message = stringResource(R.string.genre_empty),
                )

                else -> GenreSongList(
                    songs = uiState.songs,
                    sort = uiState.sortOrder,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    contentPadding = rootPadding.bottom(),
                    onOrderChange = viewModel::setSortOrder,
                    onPlayAll = viewModel::playAll,
                    onShuffle = viewModel::shuffle,
                    onPlaySong = viewModel::playSong,
                    onSongMore = { optionsController.show(it) },
                    onSongPlayNext = viewModel::playNext,
                    onSongAddToQueue = viewModel::addToQueue,
                )
            }
        }

        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { if (it is Song) viewModel.playSong(it) },
            onPlayNext = { if (it is Song) viewModel.playNext(it) },
            onAddToQueue = { if (it is Song) viewModel.addToQueue(it) },
            onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
        )
        AddToPlaylistSheetHost(controller = addToPlaylistController)
    }
}

/** The genre's song list: a header (count + play/shuffle + sort) followed by the song rows. */
@Composable
private fun GenreSongList(
    songs: List<Song>,
    sort: SortOrder,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp) + contentPadding,
    ) {
        item(key = "genre_header") {
            GenreListHeader(
                count = songs.size,
                sort = sort,
                onOrderChange = onOrderChange,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
        }
        itemsIndexed(songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
            SongRow(
                song = song,
                isActive = currentSongId == song.id,
                isPlaying = currentSongId == song.id && isPlaying,
                onPlay = onPlaySong,
                onMore = onSongMore,
                onPlayNext = onSongPlayNext,
                onAddToQueue = onSongAddToQueue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The scroll-away header: a "N songs" count with the sort control, then the Play / Shuffle buttons. */
@Composable
private fun GenreListHeader(
    count: Int,
    sort: SortOrder,
    onOrderChange: (SortOrder) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.library_song_count, count, count),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SortMenu(
            current = sort,
            options = GENRE_SONG_SORT_OPTIONS,
            onOrderChange = onOrderChange,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_play))
        }
        OutlinedButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_shuffle))
        }
    }
}
