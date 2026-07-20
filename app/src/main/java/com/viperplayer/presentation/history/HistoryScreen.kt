package com.viperplayer.presentation.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * History screen: a chronological, date-grouped timeline of every recorded play. Tapping a song
 * plays the whole timeline as a queue from that point. The overflow menu can clear all history.
 */
@Composable
fun HistoryScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    HistoryScreenContent(
        rootPadding = rootPadding,
        history = history,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        onNavigateBack = onNavigateBack,
        onPlay = viewModel::play,
        onClearHistory = viewModel::clearHistory,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreenContent(
    rootPadding: PaddingValues,
    history: List<HistoryEntry>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Stable "now" so date bucketing doesn't shift across recompositions.
    val now = remember { System.currentTimeMillis() }
    // Localized short time-of-day formatter (e.g. "9:41 PM") for each row's played-at stamp.
    val timeFormatter = remember {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    }

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.history_clear)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_clear)) },
                                onClick = {
                                    menuExpanded = false
                                    showClearConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
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
                itemsIndexed(
                    items = history,
                    key = { index, entry -> "${entry.playedAt}_$index" }
                ) { index, entry ->
                    val bucket = bucketOf(entry.playedAt, now)
                    val prevBucket = history.getOrNull(index - 1)?.let { bucketOf(it.playedAt, now) }

                    Column(modifier = Modifier.animateItem()) {
                        if (bucket != prevBucket) {
                            SectionLabel(
                                // Locale-invariant uppercase for the short section labels; avoids the
                                // NonObservableLocale lint from reading Locale.getDefault() in composition.
                                text = bucket.label().uppercase(),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = if (index == 0) 8.dp else 16.dp,
                                    bottom = 8.dp
                                )
                            )
                        }

                        val isActive = currentSongId == entry.song.id
                        ListItem(
                            title = entry.song.title,
                            badges = if (entry.song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                            subtitle = rowSubtitle(entry, timeFormatter),
                            isActive = isActive,
                            leadingContent = {
                                ListItemLeadingArtwork(
                                    artworkUrl = entry.song.artworkUrl,
                                    type = SearchItem.Type.SONG,
                                    isActive = isActive,
                                    isPlaying = isActive && isPlaying
                                )
                            },
                            trailingContent = {},
                            onClick = if (entry.song.isPlayable) {
                                { onPlay(index) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearHistory()
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Row subtitle in the mockup's "Artist · 9:41 PM" form. The played-at time is formatted at render
 * from the already-loaded [HistoryEntry.playedAt]; the artist prefix is dropped when unknown.
 */
@Composable
private fun rowSubtitle(entry: HistoryEntry, timeFormatter: DateTimeFormatter): String {
    val time = remember(entry.playedAt) {
        Instant.ofEpochMilli(entry.playedAt).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(timeFormatter)
    }
    val artist = entry.song.artistNames
    return if (artist.isNullOrBlank()) {
        time
    } else {
        stringResource(R.string.history_row_subtitle, artist, time)
    }
}

/** Coarse date buckets for grouping history rows under section headers. */
private enum class DateBucket {
    TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, EARLIER
}

@Composable
private fun DateBucket.label(): String = stringResource(
    when (this) {
        DateBucket.TODAY -> R.string.history_today
        DateBucket.YESTERDAY -> R.string.history_yesterday
        DateBucket.THIS_WEEK -> R.string.history_this_week
        DateBucket.THIS_MONTH -> R.string.history_this_month
        DateBucket.EARLIER -> R.string.history_earlier
    }
)

private fun bucketOf(epochMillis: Long, now: Long): DateBucket {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    return when {
        daysAgo <= 0L -> DateBucket.TODAY
        daysAgo == 1L -> DateBucket.YESTERDAY
        daysAgo < 7L -> DateBucket.THIS_WEEK
        date.month == today.month && date.year == today.year -> DateBucket.THIS_MONTH
        else -> DateBucket.EARLIER
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    ViPERPlayerTheme {
        HistoryScreenContent(
            rootPadding = PaddingValues(0.dp),
            history = emptyList(),
            currentSongId = null,
            isPlaying = false,
            onNavigateBack = {},
            onPlay = {},
            onClearHistory = {}
        )
    }
}
