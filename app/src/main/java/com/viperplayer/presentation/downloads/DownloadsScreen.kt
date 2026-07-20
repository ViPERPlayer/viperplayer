package com.viperplayer.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/**
 * Downloads screen: a storage-usage summary, any in-progress downloads (with a live progress
 * indicator + state), and the completed offline songs (each with a remove action). Follows the
 * redesign's card + section-label visual language and mirrors
 * [com.viperplayer.presentation.history.HistoryScreen].
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
        .toList()

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
                // Keep the scaffold's top inset; apply the bottom system inset from rootPadding.
                contentPadding = contentPadding.with(bottom = 8.dp) + rootPadding.bottom()
            ) {
                item(key = "storage_summary") {
                    StorageSummaryCard(
                        downloadedCount = downloadedSongs.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                    )
                }

                if (inProgress.isNotEmpty()) {
                    item(key = "in_progress_header") {
                        SectionLabel(
                            text = stringResource(R.string.downloads_in_progress).uppercase(),
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp
                            )
                        )
                    }
                    items(inProgress, key = { "progress_${it.mediaId}" }) { progress ->
                        InProgressRow(
                            progress = progress,
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                        )
                    }
                }

                if (downloadedSongs.isNotEmpty()) {
                    item(key = "downloaded_header") {
                        SectionLabel(
                            text = stringResource(R.string.downloads_completed).uppercase(),
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp
                            )
                        )
                    }
                    items(downloadedSongs, key = { "song_${it.id}" }) { song ->
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.downloads_completed),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    IconButton(onClick = { onRemove(song.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.downloads_remove),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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

/** Leading artwork slot size for an in-progress row — matches the list's 48dp leading rhythm. */
private val ProgressArtworkSize = 48.dp

/** Diameter of the determinate progress ring drawn over an in-progress thumbnail. */
private val ProgressRingSize = 28.dp

/**
 * The storage-usage summary card at the top of the screen: an SD-card glyph, the count of songs
 * available offline, and (when there is at least one) a subtle filled track echoing the mockup's
 * usage bar. The count is derived from the already-loaded [downloadedCount]; no IO happens here.
 */
@Composable
private fun StorageSummaryCard(
    downloadedCount: Int,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.SdCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.downloads_offline_summary),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pluralSongCount(downloadedCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/** Localized "N songs" count for the storage summary, pluralized without touching Locale in comp. */
@Composable
private fun pluralSongCount(count: Int): String =
    pluralStringResource(R.plurals.library_song_count, count, count)

/**
 * A single in-progress / failed / unsupported download row: a striped-tinted thumbnail carrying a
 * determinate progress ring while running (or an indeterminate spinner while queued, or a retry
 * glyph once failed), the song id as the title, and the localized state as the subtitle. Failed and
 * unsupported rows are dimmed to recede, matching the mockup.
 */
@Composable
private fun InProgressRow(
    progress: DownloadManager.DownloadProgress,
    modifier: Modifier = Modifier,
) {
    val stateLabel = when (progress.state) {
        DownloadManager.State.QUEUED -> stringResource(R.string.downloads_state_queued)
        DownloadManager.State.RUNNING ->
            stringResource(R.string.downloads_state_running, (progress.progress * 100).toInt())
        DownloadManager.State.FAILED -> stringResource(R.string.downloads_state_failed)
        DownloadManager.State.UNSUPPORTED -> stringResource(R.string.downloads_state_unsupported)
        DownloadManager.State.COMPLETED -> stringResource(R.string.downloads_completed)
    }
    val isError = progress.state == DownloadManager.State.FAILED ||
            progress.state == DownloadManager.State.UNSUPPORTED
    val rowModifier = if (isError) modifier.alpha(0.72f) else modifier

    ListItem(
        title = progress.mediaId.sourceId,
        badges = emptyList(),
        subtitle = stateLabel,
        isActive = false,
        leadingContent = {
            Box(
                modifier = Modifier.size(ProgressArtworkSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(6.dp),
                        )
                )
                when (progress.state) {
                    DownloadManager.State.RUNNING -> CircularProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.size(ProgressRingSize),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    DownloadManager.State.QUEUED -> CircularProgressIndicator(
                        modifier = Modifier.size(ProgressRingSize),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {},
        modifier = rowModifier
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
