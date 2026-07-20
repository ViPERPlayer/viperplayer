package com.viperplayer.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.common.PlayingArtworkOverlay
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Corner radius for the filled active-row container (mockup 5h, mirrors the Library/Search rows). */
private val ActiveRowCorner = 14.dp

/** Row artwork thumbnail size + corner radius (mockup 5h bumps the radius from the shared 6dp). */
private val RowArtworkSize = 50.dp
private val RowArtworkCorner = 10.dp

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

                        HistoryRow(
                            entry = entry,
                            isActive = currentSongId == entry.song.id,
                            isPlaying = isPlaying,
                            timeFormatter = timeFormatter,
                            onClick = if (entry.song.isPlayable) {
                                { onPlay(index) }
                            } else null
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
 * A single history row, built locally (not via the shared `ListItem`) so it can carry the mockup-5h
 * fidelity the shared row can't: the now-playing highlight (a filled [ActiveRowCorner]-rounded
 * `surfaceContainerHigh` container with an 8dp horizontal inset + small vertical margin, a
 * primary-tinted title, and a trailing `primary` graphic_eq glyph) and the small rounded-square "E"
 * explicit tag after the title. The leading artwork ([HistoryRowArtwork]) stays visible — the eq
 * lives beside the title, never over the thumbnail. There is no real per-row action beyond play, so
 * no trailing more button is shown. Unplayable rows simply don't accept taps.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    isActive: Boolean,
    isPlaying: Boolean,
    timeFormatter: DateTimeFormatter,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The active row is inset + filled; the flat row uses the full 16dp edge padding.
            .then(
                if (isActive) {
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(ActiveRowCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (isActive) 8.dp else 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HistoryRowArtwork(
            artworkUrl = entry.song.artworkUrl,
            isActive = isActive,
            isPlaying = isActive && isPlaying,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.song.title,
                    modifier = Modifier.weight(1f, fill = false).infiniteBasicMarquee(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (entry.song.isExplicit) ExplicitTag()
            }
            Text(
                text = rowSubtitle(entry, timeFormatter),
                modifier = Modifier.infiniteBasicMarquee(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The history row's leading artwork — a local thumbnail at [RowArtworkSize] (50dp) with
 * [RowArtworkCorner] (10dp, up from the shared row's 6dp per mockup 5h) and the shared now-playing
 * overlay centered on top. Built locally (like the Library rows) rather than reusing the shared
 * artwork so the larger radius applies cleanly without changing shared UI.
 */
@Composable
private fun HistoryRowArtwork(
    artworkUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
) {
    Box(
        modifier = Modifier.size(RowArtworkSize),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = stringResource(R.string.cd_artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(RowArtworkCorner)),
        )
        PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
    }
}

/**
 * The small rounded-square "E" explicit tag (mockup 5h): a surfaceVariant-filled square with a bold
 * onSurfaceVariant "E", replacing the Explicit vector icon. Shown right after a song title.
 */
@Composable
private fun ExplicitTag() {
    Box(
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.library_explicit_short),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
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
