package com.viperplayer.presentation.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SongWithStats
import com.viperplayer.domain.model.StatPeriod
import com.viperplayer.domain.model.StatsSummary
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/**
 * Stats screen: a period chips row (week / month / 3mo / 6mo / year / all) drives a time-windowed
 * summary (total plays, distinct songs, listening time) and a ranked "most played" list that is
 * tappable to play, with play-all / shuffle actions.
 */
@Composable
fun AnalyticsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    AnalyticsScreenContent(
        rootPadding = rootPadding,
        period = period,
        summary = summary,
        mostPlayed = mostPlayed,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        onNavigateBack = onNavigateBack,
        onSelectPeriod = viewModel::selectPeriod,
        onPlayFrom = viewModel::playFrom,
        onPlayAll = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsScreenContent(
    rootPadding: PaddingValues,
    period: StatPeriod,
    summary: StatsSummary,
    mostPlayed: List<SongWithStats>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit,
    onSelectPeriod: (StatPeriod) -> Unit,
    onPlayFrom: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = rootPadding.calculateBottomPadding() + 8.dp
            )
        ) {
            // Period chips
            item(key = "chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPeriod.entries.forEach { entry ->
                        FilterChip(
                            selected = period == entry,
                            onClick = { onSelectPeriod(entry) },
                            label = { Text(entry.label()) }
                        )
                    }
                }
            }

            // Summary header for the selected period
            item(key = "summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(
                        value = summary.totalPlays.toString(),
                        label = stringResource(R.string.stats_plays)
                    )
                    StatColumn(
                        value = summary.distinctSongs.toString(),
                        label = stringResource(R.string.stats_songs)
                    )
                    StatColumn(
                        value = formatListeningTime(summary.totalTimeMs).ifEmpty { "0m" },
                        label = stringResource(R.string.stats_listening_time)
                    )
                }
            }

            // "Most played" header with play-all / shuffle
            item(key = "most_played_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stats_most_played),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (mostPlayed.isNotEmpty()) {
                        Row {
                            IconButton(onClick = onPlayAll) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play_all)
                                )
                            }
                            IconButton(onClick = onShuffle) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.action_shuffle)
                                )
                            }
                        }
                    }
                }
            }

            if (mostPlayed.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = mostPlayed,
                    key = { _, item -> item.song.id.encode() }
                ) { index, item ->
                    val isActive = currentSongId == item.song.id
                    val plays = pluralStringResource(
                        R.plurals.play_count,
                        item.playCount,
                        item.playCount
                    )
                    val time = formatListeningTime(item.timeListenedMs)
                    val subtitle = if (time.isEmpty()) plays else "$plays • $time"

                    ListItem(
                        title = "${index + 1}. ${item.song.title}",
                        badges = if (item.song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                        subtitle = subtitle,
                        isActive = isActive,
                        leadingContent = {
                            ListItemLeadingArtwork(
                                artworkUrl = item.song.artworkUrl,
                                type = SearchItem.Type.SONG,
                                isActive = isActive,
                                isPlaying = isActive && isPlaying
                            )
                        },
                        trailingContent = {},
                        onClick = if (item.song.isPlayable) {
                            { onPlayFrom(index) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatPeriod.label(): String = stringResource(
    when (this) {
        StatPeriod.PAST_WEEK -> R.string.stat_period_week
        StatPeriod.PAST_MONTH -> R.string.stat_period_month
        StatPeriod.PAST_3_MONTHS -> R.string.stat_period_3_months
        StatPeriod.PAST_6_MONTHS -> R.string.stat_period_6_months
        StatPeriod.PAST_YEAR -> R.string.stat_period_year
        StatPeriod.ALL_TIME -> R.string.stat_period_all
    }
)

/**
 * Formats a listening duration into a compact human string, e.g. "3d 4h", "2h 15m", "45m".
 * Returns "" for non-positive durations so callers can omit it.
 */
private fun formatListeningTime(ms: Long): String {
    if (ms <= 0L) return ""
    var minutes = ms / 60_000L
    val days = minutes / (60L * 24)
    minutes %= (60L * 24)
    val hours = minutes / 60L
    minutes %= 60L
    val parts = buildList {
        if (days > 0L) add("${days}d")
        if (hours > 0L) add("${hours}h")
        if (minutes > 0L || isEmpty()) add("${minutes}m")
    }
    return parts.joinToString(" ")
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsScreenPreview() {
    ViPERPlayerTheme {
        AnalyticsScreenContent(
            rootPadding = PaddingValues(0.dp),
            period = StatPeriod.PAST_WEEK,
            summary = StatsSummary(totalPlays = 0, distinctSongs = 0, totalTimeMs = 0L),
            mostPlayed = emptyList(),
            currentSongId = null,
            isPlaying = false,
            onNavigateBack = {},
            onSelectPeriod = {},
            onPlayFrom = {},
            onPlayAll = {},
            onShuffle = {}
        )
    }
}
