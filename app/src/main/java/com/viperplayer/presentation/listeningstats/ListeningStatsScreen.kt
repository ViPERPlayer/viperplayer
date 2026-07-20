package com.viperplayer.presentation.listeningstats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.stats.ActivityBucket
import com.viperplayer.domain.stats.ListeningStats
import com.viperplayer.domain.stats.RankedItem
import com.viperplayer.domain.stats.StatsRange
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SelectableChip
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.common.components.SurfaceCardCornerRadius
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import java.time.Year

/** Corner radius for the smaller stat/activity tiles — a step down from the [SurfaceCardCornerRadius]. */
private val TileCornerRadius = 18.dp

/** Artwork thumbnail size + radius for a ranked row, matching the mockup's 46dp / 10dp tile. */
private val RankArtworkSize = 46.dp
private val RankArtworkRadius = 10.dp

/** Fixed width of the leading rank ordinal in a ranked row (mockup: 22px, centered). */
private val RankNumberWidth = 22.dp

/** Alpha applied to non-peak activity bars so the tallest bar reads as the highlight. */
private const val ActivityBarDimAlpha = 0.45f

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
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) + rootPadding.bottom(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                rankedItems(stats.topSongs, keyPrefix = "song")
            }

            if (stats.topArtists.isNotEmpty()) {
                item(key = "top_artists_header") {
                    SectionHeader(stringResource(R.string.listening_stats_top_artists))
                }
                rankedItems(stats.topArtists, keyPrefix = "artist")
            }

            if (stats.topAlbums.isNotEmpty()) {
                item(key = "top_albums_header") {
                    SectionHeader(stringResource(R.string.listening_stats_top_albums))
                }
                rankedItems(stats.topAlbums, keyPrefix = "album")
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
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Emits the whole ranking as one grouped [SurfaceCard] list item, with divided rank rows inside. */
private fun LazyListScope.rankedItems(
    items: List<RankedItem>,
    keyPrefix: String,
) {
    item(key = "$keyPrefix:card") {
        SurfaceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                // Inset the divider past the row's 16dp padding + 22dp rank + 12dp gap so it starts
                // under the artwork, aligning with the visual content rather than the rank ordinal.
                if (index > 0) InsetDivider(startInset = 16.dp + RankNumberWidth + 12.dp)
                RankedRow(rank = index + 1, item = item)
            }
        }
    }
}

@Composable
private fun RangeChips(range: StatsRange, onSelectRange: (StatsRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { entry ->
            SelectableChip(
                text = entry.label(),
                selected = range == entry,
                onClick = { onSelectRange(entry) },
            )
        }
    }
}

@Composable
private fun WrappedCta(onClick: () -> Unit) {
    val year = remember { Year.now().value }
    // The mockup's #4F378B→#633B48 gradient maps to primaryContainer→tertiaryContainer.
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SurfaceCardCornerRadius))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.listening_stats_wrapped_cta_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.listening_stats_wrapped_cta_body, year),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SummaryGrid(stats: ListeningStats) {
    // A 2-column grid of stat tiles. The stat list may be odd-length; a lone tile on the final row
    // keeps its half-width column (balanced by a spacer) rather than stretching across the row.
    val tiles = listOf(
        StatsFormat.listeningTime(stats.totalListenedMs) to stringResource(R.string.listening_stats_stat_time),
        stats.totalPlays.toString() to stringResource(R.string.listening_stats_stat_plays),
        stats.uniqueSongs.toString() to stringResource(R.string.listening_stats_stat_songs),
        stats.uniqueArtists.toString() to stringResource(R.string.listening_stats_stat_artists),
        stats.uniqueAlbums.toString() to stringResource(R.string.listening_stats_stat_albums),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (value, label) ->
                    StatTile(
                        value = value,
                        label = label,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTiles.size == 1) {
                    // Balance the lone trailing tile so it doesn't stretch across the whole row.
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TileCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    SectionLabel(
        text = title,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
private fun RankedRow(rank: Int, item: RankedItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            // The #1 entry gets the primary accent; the rest read as muted ordinals.
            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(RankNumberWidth),
        )
        if (item.artworkUrl != null) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(RankArtworkSize)
                    .clip(RoundedCornerShape(RankArtworkRadius)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(RankArtworkSize)
                    .clip(RoundedCornerShape(RankArtworkRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.play_count, item.playCount, item.playCount),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActivityChart(
    title: String,
    buckets: List<ActivityBucket>,
    labelOf: (Int) -> String,
) {
    val max = (buckets.maxOfOrNull { it.playCount } ?: 0).coerceAtLeast(1)
    SurfaceCard(contentPadding = PaddingValues(14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val fraction = bucket.playCount.toFloat() / max
                // The peak bucket(s) render at full primary; the rest are dimmed so the peak pops.
                val isPeak = bucket.playCount == max && bucket.playCount > 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((72.dp * fraction).coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isPeak) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = ActivityBarDimAlpha)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            buckets.forEach { bucket ->
                val isPeak = bucket.playCount == max && bucket.playCount > 0
                Text(
                    text = labelOf(bucket.labelIndex),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPeak) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
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
            textAlign = TextAlign.Center,
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
