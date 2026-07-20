package com.viperplayer.presentation.downloads

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem as Material3ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * indicator + state, plus cancel/retry actions), and the completed offline songs (each with a
 * remove action). Follows the redesign's card + section-label visual language and mirrors
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
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val fileInfo by viewModel.fileInfo.collectAsStateWithLifecycle()
    val inFlightTitles by viewModel.inFlightTitles.collectAsStateWithLifecycle()

    DownloadsScreenContent(
        rootPadding = rootPadding,
        downloadedSongs = downloadedSongs,
        downloads = downloads,
        storageBytes = storageBytes,
        totalStorageBytes = viewModel.totalStorageBytes,
        fileInfo = fileInfo,
        inFlightTitles = inFlightTitles,
        onNavigateBack = onNavigateBack,
        onRemove = viewModel::remove,
        // A failed in-progress row carries only its [MediaId]; re-queueing re-resolves the stream
        // from the plugin by id, so a minimal Song carrier is enough to drive the retry.
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
    storageBytes: Long,
    totalStorageBytes: Long,
    fileInfo: Map<MediaId, DownloadFileInfo>,
    inFlightTitles: Map<MediaId, String>,
    onNavigateBack: () -> Unit,
    onRemove: (MediaId) -> Unit,
    onRetry: (MediaId) -> Unit,
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
                        storageBytes = storageBytes,
                        totalStorageBytes = totalStorageBytes,
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
                            title = inFlightTitles[progress.mediaId] ?: progress.mediaId.sourceId,
                            onCancel = { onRemove(progress.mediaId) },
                            onRetry = { onRetry(progress.mediaId) },
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
                            subtitle = completedSubtitle(song.artistNames, fileInfo[song.id]),
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

/** Corner radius for the striped in-progress / failed thumbnail — matches list artwork rounding. */
private val ProgressArtworkCorner = 6.dp

/**
 * The storage-usage summary card at the top of the screen: an SD-card glyph, the "available offline"
 * label with the song count, a real "<size> used" figure (the sum of the downloaded files on disk),
 * and a thin usage bar. The bar's fill is a genuine fraction — downloads used / total internal
 * storage — so it never fabricates a percentage; when the total can't be read ([totalStorageBytes]
 * == 0) the bar is shown empty (only the "<size> used" text carries the figure).
 *
 * All figures arrive pre-computed from the ViewModel; the only work here is formatting bytes for
 * display (needs a Context/Locale), so no IO or business logic happens in the composable.
 */
@Composable
private fun StorageSummaryCard(
    downloadedCount: Int,
    storageBytes: Long,
    totalStorageBytes: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val usedText = remember(storageBytes) {
        Formatter.formatShortFileSize(context, storageBytes)
    }
    val fraction =
        if (totalStorageBytes > 0) (storageBytes.toFloat() / totalStorageBytes).coerceIn(0f, 1f) else 0f

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
        Text(
            text = stringResource(R.string.downloads_storage_used, usedText),
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Localized "N songs" count for the storage summary, pluralized without touching Locale in comp. */
@Composable
private fun pluralSongCount(count: Int): String =
    pluralStringResource(R.plurals.library_song_count, count, count)

/**
 * The running in-progress detail line — "64% · 2.1 MB/s · FLAC" — built from the live progress
 * snapshot: percent (always), the throttled transfer rate (only once known), and the codec label
 * derived from the stream's MIME type (only when recognized). Parts are joined by a middle dot.
 */
@Composable
private fun runningDetail(progress: DownloadManager.DownloadProgress): String {
    val context = LocalContext.current
    val separator = stringResource(R.string.downloads_detail_separator)
    val parts = buildList {
        add(stringResource(R.string.downloads_progress_percent, (progress.progress * 100).toInt()))
        if (progress.bytesPerSec > 0) {
            val speed = Formatter.formatShortFileSize(context, progress.bytesPerSec)
            add(stringResource(R.string.downloads_progress_speed, speed))
        }
        DownloadManager.codecLabelFor(progress.mimeType)?.let { add(it) }
    }
    return parts.joinToString(separator)
}

/**
 * The completed-row subtitle — "<artist> · <size> · <format>" — joining the artist byline with the
 * per-file size + format facts (when the file's still on disk). Any missing part is simply dropped,
 * so a song with no artist or a not-yet-derived [info] still renders cleanly.
 */
@Composable
private fun completedSubtitle(artistNames: String?, info: DownloadFileInfo?): String? {
    val context = LocalContext.current
    val separator = stringResource(R.string.downloads_detail_separator)
    val parts = buildList {
        artistNames?.let { add(it) }
        info?.let {
            add(Formatter.formatShortFileSize(context, it.sizeBytes))
            it.formatLabel?.let { label -> add(label) }
        }
    }
    return parts.joinToString(separator).takeIf { it.isNotEmpty() }
}

/**
 * A single in-progress / failed / unsupported download row.
 *
 * While running/queued the striped-tinted thumbnail carries a determinate progress ring (or an
 * indeterminate spinner when queued) and a trailing [Icons.Rounded.Close] cancels the download via
 * [onCancel] (backed by the manager's remove, which drops the in-flight entry). Once failed or
 * unsupported the row dims to recede; a genuine failure additionally becomes tappable with a
 * trailing [Icons.Rounded.Refresh] to re-queue it via [onRetry], and its subtitle is error-tinted.
 *
 * [title] is the song's real title resolved locally by the ViewModel (falling back to the source id
 * for a row whose song isn't in the library). While running, the subtitle is a compact
 * "percent · speed · codec" detail line built from the live [DownloadManager.DownloadProgress].
 */
@Composable
private fun InProgressRow(
    progress: DownloadManager.DownloadProgress,
    title: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFailed = progress.state == DownloadManager.State.FAILED
    val isUnsupported = progress.state == DownloadManager.State.UNSUPPORTED
    val isError = isFailed || isUnsupported

    val subtitle = when (progress.state) {
        DownloadManager.State.QUEUED -> stringResource(R.string.downloads_state_queued)
        DownloadManager.State.RUNNING -> runningDetail(progress)
        DownloadManager.State.FAILED -> stringResource(R.string.downloads_failed_retry)
        DownloadManager.State.UNSUPPORTED -> stringResource(R.string.downloads_state_unsupported)
        DownloadManager.State.COMPLETED -> stringResource(R.string.downloads_completed)
    }
    val subtitleColor =
        if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    val dimmedModifier = if (isError) modifier.alpha(0.72f) else modifier
    // Only a genuine failure is retryable; an unsupported stream can never be downloaded here.
    val rowModifier = if (isFailed) dimmedModifier.clickable(onClick = onRetry) else dimmedModifier

    Material3ListItem(
        headlineContent = {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = subtitleColor,
            )
        },
        leadingContent = { ProgressThumbnail(state = progress.state, progress = progress.progress) },
        trailingContent = {
            when {
                isFailed -> IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.downloads_retry),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                isUnsupported -> {}
                else -> IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.downloads_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier.fillMaxWidth(),
    )
}

/**
 * The striped-tinted thumbnail in an in-progress / failed row's leading slot: a determinate ring
 * while running, an indeterminate spinner while queued, or a refresh glyph once failed/unsupported.
 */
@Composable
private fun ProgressThumbnail(
    state: DownloadManager.State,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(ProgressArtworkSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(ProgressArtworkCorner),
                )
        )
        when (state) {
            DownloadManager.State.RUNNING -> CircularProgressIndicator(
                progress = { progress },
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
}

@Preview(showBackground = true)
@Composable
private fun DownloadsScreenPreview() {
    ViPERPlayerTheme {
        DownloadsScreenContent(
            rootPadding = PaddingValues(0.dp),
            downloadedSongs = emptyList(),
            downloads = emptyMap(),
            storageBytes = 0L,
            totalStorageBytes = 0L,
            fileInfo = emptyMap(),
            inFlightTitles = emptyMap(),
            onNavigateBack = {},
            onRemove = {},
            onRetry = {},
        )
    }
}
