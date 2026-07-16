package com.viperplayer.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.presentation.common.CollapsingArtworkScaffold
import com.viperplayer.presentation.common.ErrorState
import com.viperplayer.presentation.common.SortMenu
import com.viperplayer.presentation.plugins.PluginActionsViewModel
import com.viperplayer.presentation.plugins.rememberPluginActionResolver
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ListItemTrailingWithDuration
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme

// Artist Top Songs sort options. DEFAULT (plugin order) is first and selected initially.
private val ARTIST_SONG_SORT_OPTIONS = listOf(
    SortOption.DEFAULT,
    SortOption.TITLE,
    SortOption.ALBUM,
    SortOption.DURATION,
)

@Composable
fun ArtistDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // If this screen's plugin needs the user to act (sign in, verify...), offer it on errors.
    val actionsViewModel: PluginActionsViewModel = hiltViewModel()
    val pendingActions by actionsViewModel.pendingActions.collectAsStateWithLifecycle()
    val pluginAction = pendingActions.firstOrNull { it.pluginId == viewModel.pluginId }
    val resolvePluginAction = rememberPluginActionResolver { actionsViewModel.refresh() }

    ArtistDetailScreenContent(
        rootPadding = rootPadding,
        uiState = uiState,
        currentSong = currentSong,
        isPlaying = isPlaying,
        pluginAction = pluginAction,
        onResolvePluginAction = resolvePluginAction,
        onNavigateBack = onNavigateBack,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToPlaylist = onNavigateToPlaylist,
        onNavigateToArtist = onNavigateToArtist,
        onRefresh = viewModel::refresh,
        onSortOrderChange = viewModel::setSortOrder,
        onPlayAllSongs = viewModel::playAllSongs,
        onPlaySong = viewModel::playSong,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue
    )
}

@Composable
private fun ArtistDetailScreenContent(
    rootPadding: PaddingValues,
    uiState: ArtistDetailUiState,
    currentSong: Song?,
    isPlaying: Boolean,
    pluginAction: PluginPendingAction? = null,
    onResolvePluginAction: (PluginPendingAction) -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onRefresh: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit = {},
    onPlayAllSongs: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
) {
    val optionsController = rememberMediaItemOptionsController()

    val artist = when (uiState) {
        is ArtistDetailUiState.Success -> uiState.artist
        is ArtistDetailUiState.Loading -> uiState.initialArtist
        else -> null
    }
    val title = artist?.name ?: "Artist"

    CollapsingArtworkScaffold(
        artworkUrl = artist?.imageUrl,
        title = title,
        onNavigateBack = onNavigateBack,
        actions = {
            // Sort applies to the Top Songs list; only offer it once there are songs to sort.
            if (uiState is ArtistDetailUiState.Success && uiState.artist.topSongs.isNotEmpty()) {
                SortMenu(
                    current = uiState.songsSort,
                    options = ARTIST_SONG_SORT_OPTIONS,
                    onOrderChange = onSortOrderChange,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
    ) { contentPadding ->
        when (val state = uiState) {
            is ArtistDetailUiState.Loading -> {
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

            is ArtistDetailUiState.Error -> {
                ErrorState(
                    message = state.message,
                    actionLabel = pluginAction?.title,
                    onAction = pluginAction?.let { action -> { onResolvePluginAction(action) } },
                    onRetry = onRefresh,
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(rootPadding),
                )
            }

            is ArtistDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = rootPadding
                ) {
                    val artistData = state.artist

                    // Play all button
                    item {
                        Button(
                            onClick = onPlayAllSongs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_play_all))
                        }
                    }

                    // Top Songs section (rendered in the user-chosen sort order).
                    if (state.sortedSongs.isNotEmpty()) {
                        item {
                            SectionHeader("Top Songs")
                        }
                        items(
                            items = state.sortedSongs,
                            key = { song -> song.id.toString() }
                        ) { song ->
                            ListItem(
                                title = song.title,
                                badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                subtitle = song.artistNames,
                                isActive = currentSong?.id == song.id,
                                leadingContent = {
                                    ListItemLeadingArtwork(
                                        artworkUrl = song.artworkUrl ?: song.album?.artworkUrl,
                                        type = SearchItem.Type.SONG,
                                        isActive = currentSong?.id == song.id,
                                        isPlaying = currentSong?.id == song.id && isPlaying
                                    )
                                },
                                trailingContent = {
                                    ListItemTrailingWithDuration(
                                        durationMs = song.durationMs,
                                        onMoreClick = { optionsController.show(song) }
                                    )
                                },
                                onClick = { onPlaySong(song) },
                                onLongClick = { optionsController.show(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }

                    // Albums section
                    if (artistData.albums.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            SectionHeader("Albums")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artistData.albums,
                                    key = { album -> album.id.toString() }
                                ) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { onNavigateToAlbum(album) }
                                    )
                                }
                            }
                        }
                    }

                    // Playlists section
                    if (artistData.playlists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Playlists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artistData.playlists,
                                    key = { playlist -> playlist.id.toString() }
                                ) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }

                    // Featuring section
                    if (artistData.featuring.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Featuring")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artistData.featuring,
                                    key = { playlist -> playlist.id.toString() }
                                ) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }

                    // Appears On section
                    if (artistData.appearsOn.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Appears On")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artistData.appearsOn.filter { it is Album || it is Playlist },
                                    // Namespace by type: an Album and a Playlist can share a MediaId,
                                    // which would otherwise be a duplicate LazyList key (crash).
                                    key = { item -> "${item.javaClass.simpleName}:${item.id}" }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            AlbumCard(
                                                album = item,
                                                onClick = { onNavigateToAlbum(item) }
                                            )
                                        }

                                        is Playlist -> {
                                            PlaylistCard(
                                                playlist = item,
                                                onClick = { onNavigateToPlaylist(item) }
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                    }

                    // Similar Artists section
                    if (artistData.similarArtists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Similar Artists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artistData.similarArtists,
                                    key = { similarArtist -> similarArtist.id.toString() }
                                ) { similarArtist ->
                                    ArtistCard(
                                        artist = similarArtist,
                                        onClick = { onNavigateToArtist(similarArtist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val addToPlaylistController = rememberAddToPlaylistController()

        // Media item options bottom sheet
        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { if (it is Song) onPlaySong(it) },
            onPlayNext = { if (it is Song) onPlayNext(it) },
            onAddToQueue = { if (it is Song) onAddToQueue(it) },
            onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
            onViewAlbum = onNavigateToAlbum,
        )

        // Add-to-playlist picker for a song's options sheet (existing playlists + create new).
        AddToPlaylistSheetHost(controller = addToPlaylistController)
    }
}

@Preview(showBackground = true)
@Composable
private fun ArtistDetailScreenPreview() {
    val artist = ArtistDetail(
        id = MediaId("plugin", "artist1"),
        name = "Pink Floyd",
        imageUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png",
        topSongs = listOf(
            Song(
                id = MediaId("plugin", "song1"),
                title = "Money",
                artists = listOf(ArtistRef("Pink Floyd", MediaId("plugin", "artist1"))),
                durationMs = 382000
            ),
            Song(
                id = MediaId("plugin", "song2"),
                title = "Another Brick in the Wall, Pt. 2",
                artists = listOf(ArtistRef("Pink Floyd", MediaId("plugin", "artist1"))),
                durationMs = 239000,
                isExplicit = true
            )
        ),
        albums = listOf(
            Album(
                id = MediaId("plugin", "album1"),
                name = "The Dark Side of the Moon",
                artists = emptyList(),
                artworkUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png"
            ),
            Album(
                id = MediaId("plugin", "album2"),
                name = "The Wall",
                artists = emptyList(),
                artworkUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png"
            )
        )
    )

    ViPERPlayerTheme {
        ArtistDetailScreenContent(
            rootPadding = PaddingValues(0.dp),
            uiState = ArtistDetailUiState.Success(artist),
            currentSong = artist.topSongs[0],
            isPlaying = true,
            onNavigateBack = {},
            onNavigateToAlbum = {},
            onNavigateToPlaylist = {},
            onNavigateToArtist = {},
            onRefresh = {},
            onPlayAllSongs = {},
            onPlaySong = {},
            onPlayNext = {},
            onAddToQueue = {}
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artists.joinToString { it.name }.takeIf { it.isNotEmpty() } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = playlist.artworkUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.ownerName != null) {
            Text(
                text = playlist.ownerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
