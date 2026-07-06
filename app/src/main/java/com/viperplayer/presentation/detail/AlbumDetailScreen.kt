package com.viperplayer.presentation.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.AlbumType
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.CollapsingArtworkScaffold
import com.viperplayer.presentation.common.ErrorState
import com.viperplayer.presentation.plugins.PluginActionsViewModel
import com.viperplayer.presentation.plugins.rememberPluginActionResolver
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme


/**
 * Sealed class to represent items in the album song list (disc headers or songs).
 */
private sealed class DiscItem {
    data class HeaderItem(val discNumber: Int) : DiscItem()
    data class SongItem(val song: Song) : DiscItem()
}

/**
 * Screen that displays the details of an album, including its artwork, metadata, and song list.
 *
 * @param rootPadding The padding to apply to the root of the screen, typically from a parent Scaffold.
 * @param onNavigateBack Callback when the user wants to go back.
 * @param onNavigateToArtist Callback when the user clicks on an artist.
 * @param viewModel The ViewModel for this screen.
 */
@Composable
fun AlbumDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // If this screen's plugin needs the user to act (sign in, verify...), offer it on errors.
    val actionsViewModel: PluginActionsViewModel = hiltViewModel()
    val pendingActions by actionsViewModel.pendingActions.collectAsStateWithLifecycle()
    val pluginAction = pendingActions.firstOrNull { it.pluginId == viewModel.pluginId }
    val resolvePluginAction = rememberPluginActionResolver { actionsViewModel.refresh() }

    AlbumDetailScreenContent(
        rootPadding = rootPadding,
        uiState = uiState,
        currentSong = currentSong,
        isPlaying = isPlaying,
        pluginAction = pluginAction,
        onResolvePluginAction = resolvePluginAction,
        onNavigateBack = onNavigateBack,
        onNavigateToArtist = onNavigateToArtist,
        onRefresh = viewModel::refresh,
        onPlayAlbum = viewModel::playAlbum,
        onShuffle = viewModel::shuffle,
        onPlaySong = viewModel::playSong,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onToggleLike = { /* TODO: Implement toggle like */ }
    )
}

/**
 * Stateless content for the Album Detail screen.
 *
 * Uses [CollapsingArtworkScaffold] for a collapsing artwork header with the album title.
 */
@Composable
private fun AlbumDetailScreenContent(
    rootPadding: PaddingValues,
    uiState: AlbumDetailUiState,
    currentSong: Song?,
    isPlaying: Boolean,
    pluginAction: PluginPendingAction? = null,
    onResolvePluginAction: (PluginPendingAction) -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onRefresh: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
) {
    val optionsController = rememberMediaItemOptionsController()

    val album = when (uiState) {
        is AlbumDetailUiState.Success -> uiState.album
        is AlbumDetailUiState.Loading -> uiState.initialAlbum
        else -> null
    }
    val title = album?.name ?: stringResource(R.string.album_type_album)

    CollapsingArtworkScaffold(
        artworkUrl = album?.artworkUrl,
        title = title,
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) { contentPadding ->
        when (uiState) {
            is AlbumDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(rootPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            is AlbumDetailUiState.Error -> {
                ErrorState(
                    message = uiState.message,
                    actionLabel = pluginAction?.title,
                    onAction = pluginAction?.let { action -> { onResolvePluginAction(action) } },
                    onRetry = onRefresh,
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(rootPadding),
                )
            }

            is AlbumDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = rootPadding
                ) {
                    // Album metadata header (name, artists, info, buttons)
                    item(key = "metadata") {
                        AlbumMetadataHeader(
                            album = uiState.album,
                            songCount = uiState.album.songs.orEmpty().size,
                            onPlayAlbum = onPlayAlbum,
                            onShuffle = onShuffle,
                            onArtistClick = onNavigateToArtist,
                        )
                    }

                    // Songs list
                    if (uiState.album.songs.orEmpty().isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.album_no_songs),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Group songs by disc number and check if we have multiple discs
                        val sortedSongs = uiState.album.songs.orEmpty().sortedWith(
                            compareBy(
                                { it.discNumber ?: 1 },
                                { it.trackNumber ?: 0 }
                            ))
                        val songsByDisc = sortedSongs.groupBy { it.discNumber ?: 1 }
                        val hasMultipleDiscs = songsByDisc.size > 1

                        if (hasMultipleDiscs) {
                            val discItems = buildList {
                                songsByDisc.toSortedMap().forEach { (discNumber, discSongs) ->
                                    add(DiscItem.HeaderItem(discNumber))
                                    discSongs.forEach { song ->
                                        add(DiscItem.SongItem(song))
                                    }
                                }
                            }

                            items(
                                items = discItems,
                                key = { item ->
                                    when (item) {
                                        is DiscItem.HeaderItem -> "disc_${item.discNumber}"
                                        is DiscItem.SongItem -> item.song.id.toString()
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is DiscItem.HeaderItem -> {
                                        DiscHeader(
                                            discNumber = item.discNumber,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    is DiscItem.SongItem -> {
                                        val song = item.song
                                        ListItem(
                                            type = SearchItem.Type.SONG,
                                            title = song.title,
                                            badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                            subtitle = song.artistNames,
                                            trackNumber = song.trackNumber,
                                            durationMs = song.durationMs,
                                            isActive = currentSong?.id == song.id,
                                            isPlaying = currentSong?.id == song.id && isPlaying,
                                            onClick = { onPlaySong(song) },
                                            onMoreClick = { optionsController.show(song) },
                                            onLongClick = { optionsController.show(song) },
                                            onPlayNext = { onPlayNext(song) },
                                            onAddToQueue = { onAddToQueue(song) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = sortedSongs,
                                key = { song -> song.id.toString() }
                            ) { song ->
                                ListItem(
                                    type = SearchItem.Type.SONG,
                                    title = song.title,
                                    badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                    subtitle = song.artistNames,
                                    trackNumber = song.trackNumber,
                                    durationMs = song.durationMs,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = { onPlaySong(song) },
                                    onMoreClick = { optionsController.show(song) },
                                    onLongClick = { optionsController.show(song) },
                                    onPlayNext = { onPlayNext(song) },
                                    onAddToQueue = { onAddToQueue(song) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Media item options bottom sheet
    MediaItemOptionsSheetHost(
        controller = optionsController,
        onPlay = { if (it is Song) onPlaySong(it) },
        onPlayNext = { if (it is Song) onPlayNext(it) },
        onAddToQueue = { if (it is Song) onAddToQueue(it) },
        onLike = { if (it is Song) onToggleLike(it) },
        onViewArtist = onNavigateToArtist,
    )
}

/**
 * Metadata header section for the album (below the collapsing app bar).
 * Shows album name, clickable artists, type/year/count info line, and action buttons.
 * Everything is left-aligned.
 */
@Composable
private fun AlbumMetadataHeader(
    album: Album,
    songCount: Int,
    onPlayAlbum: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Artists (clickable)
        if (album.artists.isNotEmpty()) {
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            val annotatedString = remember(album.artists) {
                buildAnnotatedString {
                    album.artists.forEachIndexed { index, artist ->
                        val startIndex = length
                        append(artist.name)
                        val endIndex = length

                        addStringAnnotation(
                            tag = "artist",
                            annotation = index.toString(),
                            start = startIndex,
                            end = endIndex
                        )

                        if (index < album.artists.size - 1) {
                            append(", ")
                        }
                    }
                }
            }

            Text(
                text = annotatedString,
                modifier = Modifier
                    .pointerInput(textLayoutResult, annotatedString) {
                        detectTapGestures { position ->
                            textLayoutResult?.let {
                                val offset = it.getOffsetForPosition(position)
                                annotatedString
                                    .getStringAnnotations(
                                        tag = "artist",
                                        start = offset,
                                        end = offset
                                    )
                                    .firstOrNull()
                                    ?.let { annotation ->
                                        val artist = album.artists[annotation.item.toInt()]
                                        onArtistClick(artist)
                                    }
                            }
                        }
                    },
                onTextLayout = { textLayoutResult = it },
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Album info: type • year • song count (left-aligned)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album type first
            Text(
                text = stringResource(
                    when (album.type) {
                        AlbumType.ALBUM -> R.string.album_type_album
                        AlbumType.SINGLE -> R.string.album_type_single
                        AlbumType.EP -> R.string.album_type_ep
                        AlbumType.COMPILATION -> R.string.album_type_compilation
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Year second
            album.releaseYear?.let { year ->
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Song count third
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (songCount == 1) {
                    stringResource(R.string.album_song_count_one, songCount)
                } else {
                    stringResource(R.string.album_song_count_many, songCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAlbum,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_play))
            }
            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_shuffle))
            }
        }
    }
}

/**
 * Header for a specific disc in a multi-disc album.
 */
@Composable
private fun DiscHeader(
    discNumber: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(R.string.album_disc_header, discNumber),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlbumDetailScreenPreview() {
    val songs = listOf(
        Song(
            id = MediaId("plugin", "song1"),
            title = "Speak to Me",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 90000,
            trackNumber = 1,
            discNumber = 1
        ),
        Song(
            id = MediaId("plugin", "song2"),
            title = "Breathe (In the Air)",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 169000,
            trackNumber = 2,
            discNumber = 1
        ),
        Song(
            id = MediaId("plugin", "song3"),
            title = "On the Run",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 213000,
            trackNumber = 3,
            discNumber = 1,
            isExplicit = true
        ),
        Song(
            id = MediaId("plugin", "song4"),
            title = "Time",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 421000,
            trackNumber = 4,
            discNumber = 1
        ),
        Song(
            id = MediaId("plugin", "song11"),
            title = "Bonus Track 1",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 300000,
            trackNumber = 1,
            discNumber = 2
        ),
        Song(
            id = MediaId("plugin", "song12"),
            title = "Bonus Track 2",
            artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
            durationMs = 240000,
            trackNumber = 2,
            discNumber = 2
        )
    )

    val album = Album(
        id = MediaId("plugin", "album1"),
        name = "The Dark Side of the Moon",
        artists = listOf(Artist(MediaId("plugin", "artist1"), "Pink Floyd")),
        artworkUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png",
        releaseYear = 1973,
        trackCount = 10,
        type = AlbumType.ALBUM,
        songs = songs
    )

    ViPERPlayerTheme {
        AlbumDetailScreenContent(
            rootPadding = PaddingValues(0.dp),
            uiState = AlbumDetailUiState.Success(album),
            currentSong = songs[1],
            isPlaying = true,
            onNavigateBack = {},
            onNavigateToArtist = {},
            onRefresh = {},
            onPlayAlbum = {},
            onShuffle = {},
            onPlaySong = {},
            onPlayNext = {},
            onAddToQueue = {},
            onToggleLike = {}
        )
    }
}
