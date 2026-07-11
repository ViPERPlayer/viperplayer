package com.viperplayer.presentation.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * Downloads screen: completed offline songs (with a remove action) and any in-progress downloads
 * showing a progress indicator + state. Mirrors [com.viperplayer.presentation.history.HistoryScreen].
 */
@Composable
fun DownloadsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    DownloadsScreenContent(
        rootPadding = rootPadding,
        downloadedSongs = downloadedSongs,
        downloads = downloads,
        onNavigateBack = onNavigateBack,
        onRemove = viewModel::remove,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenContent(
    rootPadding: PaddingValues,
    downloadedSongs: List<Song>,
    downloads: Map<MediaId, DownloadManager.DownloadProgress>,
    onNavigateBack: () -> Unit,
    onRemove: (MediaId) -> Unit,
    onRetry: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    // In-progress / failed / unsupported items that are not yet finished + persisted.
    val downloadedIds = downloadedSongs.map { it.id }.toSet()
    val inProgress = downloads.values
        .filter { it.state != DownloadManager.State.COMPLETED && it.mediaId !in downloadedIds }

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        if (downloadedSongs.isEmpty() && inProgress.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = rootPadding.calculateBottomPadding() + 8.dp
                )
            ) {
                if (inProgress.isNotEmpty()) {
                    item(key = "in_progress_header") {
                        SectionHeader(stringResource(R.string.downloads_in_progress))
                    }
                    items(inProgress, key = { it.mediaId.toString() }) { progress ->
                        InProgressRow(progress = progress)
                    }
                }

                if (downloadedSongs.isNotEmpty()) {
                    item(key = "downloaded_header") {
                        SectionHeader(stringResource(R.string.downloads_completed))
                    }
                    items(downloadedSongs, key = { it.id.toString() }) { song ->
                        ListItem(
                            title = song.title,
                            badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                            subtitle = song.artistNames,
                            isActive = false,
                            leadingContent = {
                                ListItemLeadingArtwork(
                                    artworkUrl = song.artworkUrl,
                                    type = SearchItem.Type.SONG,
                                    isActive = false,
                                    isPlaying = false
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemove(song.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.downloads_remove)
                                    )
                                }
                            },
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun InProgressRow(progress: DownloadManager.DownloadProgress) {
    val stateLabel = when (progress.state) {
        DownloadManager.State.QUEUED -> stringResource(R.string.downloads_state_queued)
        DownloadManager.State.RUNNING ->
            stringResource(R.string.downloads_state_running, (progress.progress * 100).toInt())
        DownloadManager.State.FAILED -> stringResource(R.string.downloads_state_failed)
        DownloadManager.State.UNSUPPORTED -> stringResource(R.string.downloads_state_unsupported)
        DownloadManager.State.COMPLETED -> stringResource(R.string.downloads_completed)
    }
    ListItem(
        title = progress.mediaId.sourceId,
        badges = emptyList(),
        subtitle = stateLabel,
        isActive = false,
        leadingContent = {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (progress.state == DownloadManager.State.RUNNING) {
                    CircularProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.size(28.dp)
                    )
                } else if (progress.state == DownloadManager.State.QUEUED) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun DownloadsScreenPreview() {
    ViPERPlayerTheme {
        DownloadsScreenContent(
            rootPadding = PaddingValues(0.dp),
            downloadedSongs = emptyList(),
            downloads = emptyMap(),
            onNavigateBack = {},
            onRemove = {},
            onRetry = {},
        )
    }
}
