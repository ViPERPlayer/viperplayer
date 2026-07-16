package com.viperplayer.presentation.listeningstats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.stats.ActivityBucket
import com.viperplayer.domain.stats.ListeningStats
import com.viperplayer.domain.stats.RankedItem
import com.viperplayer.domain.stats.StatsRange
import com.viperplayer.presentation.common.ViperScaffold
import java.time.Year

/**
 * The Listening stats screen: a range chips row drives a summary + top songs/artists/albums + an
 * activity breakdown, with a Wrapped entry point and a clear-history action. All aggregation is done
 * in the ViewModel via the pure aggregator — this composable only renders [ListeningStats] and
 * forwards events.
 */
@Composable
fun ListeningStatsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToWrapped: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListeningStatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val hasHistory by viewModel.hasHistory.collectAsStateWithLifecycle()

    ListeningStatsContent(
        rootPadding = rootPadding,
        stats = stats,
        range = range,
        hasHistory = hasHistory,
        onNavigateBack = onNavigateBack,
        onNavigateToWrapped = onNavigateToWrapped,
        onSelectRange = viewModel::selectRange,
        onClearHistory = viewModel::clearHistory,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningStatsContent(
    rootPadding: PaddingValues,
    stats: ListeningStats,
    range: StatsRange,
    hasHistory: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToWrapped: () -> Unit,
    onSelectRange: (StatsRange) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.listening_stats)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (hasHistory) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.listening_stats_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = rootPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "range_chips") {
                RangeChips(range = range, onSelectRange = onSelectRange)
            }

            item(key = "wrapped_cta") {
                WrappedCta(onClick = onNavigateToWrapped)
            }

            if (stats.isEmpty) {
                item(key = "empty") { EmptyState() }
                return@LazyColumn
            }

            item(key = "summary") { SummaryGrid(stats = stats) }

            if (stats.topSongs.isNotEmpty()) {
                item(key = "top_songs_header") {
                    SectionHeader(stringResource(R.string.listening_stats_top_songs))
                }
                itemsWithRank(stats.topSongs, keyPrefix = "song")
            }

            if (stats.topArtists.isNotEmpty()) {
                item(key = "top_artists_header") {
                    SectionHeader(stringResource(R.string.listening_stats_top_artists))
                }
                itemsWithRank(stats.topArtists, keyPrefix = "artist")
            }

            if (stats.topAlbums.isNotEmpty()) {
                item(key = "top_albums_header") {
                    SectionHeader(stringResource(R.string.listening_stats_top_albums))
                }
                itemsWithRank(stats.topAlbums, keyPrefix = "album")
            }

            item(key = "activity_header") {
                SectionHeader(stringResource(R.string.listening_stats_activity))
            }
            item(key = "activity_day") {
                ActivityChart(
                    title = stringResource(R.string.listening_stats_activity_by_day),
                    buckets = stats.byDayOfWeek,
                    labelOf = { StatsFormat.dayLabels.getOrElse(it) { "" } },
                )
            }
            item(key = "activity_hour") {
                ActivityChart(
                    title = stringResource(R.string.listening_stats_activity_by_hour),
                    buckets = stats.byHourOfDay,
                    // Label every 6 hours to avoid crowding.
                    labelOf = { if (it % 6 == 0) StatsFormat.hourLabel(it) else "" },
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.listening_stats_clear_confirm_title)) },
            text = { Text(stringResource(R.string.listening_stats_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}

private fun LazyListScope.itemsWithRank(
    items: List<RankedItem>,
    keyPrefix: String,
) {
    items.forEachIndexed { index, item ->
        item(key = "$keyPrefix:${item.key}") {
            RankedRow(rank = index + 1, item = item)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChips(range: StatsRange, onSelectRange: (StatsRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { entry ->
            FilterChip(
                selected = range == entry,
                onClick = { onSelectRange(entry) },
                label = { Text(entry.label()) },
            )
        }
    }
}

@Composable
private fun WrappedCta(onClick: () -> Unit) {
    val year = remember { Year.now().value }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.listening_stats_wrapped_cta_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.listening_stats_wrapped_cta_body, year),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SummaryGrid(stats: ListeningStats) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(
                value = StatsFormat.listeningTime(stats.totalListenedMs),
                label = stringResource(R.string.listening_stats_stat_time),
            )
            StatColumn(
                value = stats.totalPlays.toString(),
                label = stringResource(R.string.listening_stats_stat_plays),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(
                value = stats.uniqueSongs.toString(),
                label = stringResource(R.string.listening_stats_stat_songs),
            )
            StatColumn(
                value = stats.uniqueArtists.toString(),
                label = stringResource(R.string.listening_stats_stat_artists),
            )
            StatColumn(
                value = stats.uniqueAlbums.toString(),
                label = stringResource(R.string.listening_stats_stat_albums),
            )
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RankedRow(rank: Int, item: RankedItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.listening_stats_rank_prefix, rank),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp),
        )
        if (item.artworkUrl != null) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.playCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = StatsFormat.listeningTime(item.listenedMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityChart(
    title: String,
    buckets: List<ActivityBucket>,
    labelOf: (Int) -> String,
) {
    val max = (buckets.maxOfOrNull { it.playCount } ?: 0).coerceAtLeast(1)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraction = bucket.playCount.toFloat() / max
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((72.dp * fraction).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = labelOf(bucket.labelIndex),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.listening_stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.listening_stats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsRange.label(): String = stringResource(
    when (this) {
        StatsRange.LAST_7_DAYS -> R.string.listening_stats_range_7_days
        StatsRange.LAST_4_WEEKS -> R.string.listening_stats_range_4_weeks
        StatsRange.LAST_6_MONTHS -> R.string.listening_stats_range_6_months
        StatsRange.LAST_YEAR -> R.string.listening_stats_range_year
        StatsRange.ALL_TIME -> R.string.listening_stats_range_all
    },
)
