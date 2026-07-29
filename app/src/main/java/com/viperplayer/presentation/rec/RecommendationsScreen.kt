package com.viperplayer.presentation.rec

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.library.SongRow

/**
 * The shared render surface for the recommendation lists ("More like this" / "For You"). A pure
 * composable driven entirely by [state] + callbacks — it holds no ViewModel and no data logic. Mirrors
 * the M3 detail-list styling (a scroll-away header with Play/Shuffle, then [SongRow]s).
 *
 * State handling:
 *  - loading → a centered [LoadingIndicator];
 *  - has songs → the header + list, plus a subtle fallback note when [RecUiState.usingFallback];
 *  - empty → an explanatory state chosen from [RecUiState.emptyReason] (disabled / indexing / none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    state: RecUiState,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Overrides the [RecEmptyReason.NO_RESULTS] empty-state message. Discover uses this for a
     * discovery-flavored "nothing new to discover right now" (the default is "No similar songs yet",
     * which suits the library-only surfaces). Null keeps the default.
     */
    noResultsMessage: String? = null,
    /**
     * Explicit-feedback callbacks (P4). When both are provided, each row shows a thumbs-up / thumbs-down
     * pair; null (the default) hides them (e.g. surfaces that don't take feedback).
     */
    onThumbsUp: ((Song) -> Unit)? = null,
    onThumbsDown: ((Song) -> Unit)? = null,
) {
    ViperScaffold(
        modifier = modifier.padding(rootPadding.bottom()),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                state.isLoading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

                state.hasSongs -> RecSongList(
                    state = state,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    contentPadding = rootPadding.bottom(),
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onPlaySong = onPlaySong,
                    onSongMore = onSongMore,
                    onSongPlayNext = onSongPlayNext,
                    onSongAddToQueue = onSongAddToQueue,
                    onThumbsUp = onThumbsUp,
                    onThumbsDown = onThumbsDown,
                )

                else -> RecEmptyState(
                    reason = state.emptyReason,
                    indexingProcessed = state.indexingProcessed,
                    indexingTotal = state.indexingTotal,
                    noResultsMessage = noResultsMessage,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** Header (artwork + title/subtitle + Play/Shuffle, and an optional fallback note) then the song rows. */
@Composable
private fun RecSongList(
    state: RecUiState,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
    onThumbsUp: ((Song) -> Unit)?,
    onThumbsDown: ((Song) -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp) + contentPadding,
    ) {
        item(key = "rec_header") {
            RecListHeader(
                title = state.title,
                subtitle = state.subtitle,
                artworkUrl = state.artworkUrl,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
        }
        if (state.usingFallback) {
            item(key = "rec_fallback_note") {
                Text(
                    text = stringResource(R.string.rec_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        itemsIndexed(state.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
            SongRow(
                song = song,
                isActive = currentSongId == song.id,
                isPlaying = currentSongId == song.id && isPlaying,
                onPlay = onPlaySong,
                onMore = onSongMore,
                onPlayNext = onSongPlayNext,
                onAddToQueue = onSongAddToQueue,
                modifier = Modifier.fillMaxWidth(),
                reason = state.reasons[song.id]?.let { recReasonText(it) },
                onThumbsUp = onThumbsUp,
                onThumbsDown = onThumbsDown,
                isDisliked = song.id in state.dislikedIds,
            )
        }
    }
}

/** The scroll-away header: optional artwork + title/subtitle, then the Play / Shuffle buttons. */
@Composable
private fun RecListHeader(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_play))
        }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_shuffle))
        }
    }
}

/**
 * The explanatory empty state. The message depends on [reason]: recommendations disabled, library not
 * indexed yet (with the "Analyzing your library… N/M" progress when a run is active), an unexpected
 * error (retryable), or simply no results.
 */
@Composable
private fun RecEmptyState(
    reason: RecEmptyReason?,
    indexingProcessed: Int?,
    indexingTotal: Int?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    noResultsMessage: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.size(12.dp))
        val message = when (reason) {
            RecEmptyReason.RECOMMENDATIONS_DISABLED -> stringResource(R.string.rec_empty_disabled)
            RecEmptyReason.NOT_INDEXED_YET ->
                if (indexingProcessed != null && indexingTotal != null) {
                    stringResource(R.string.rec_indexing_progress, indexingProcessed, indexingTotal)
                } else {
                    stringResource(R.string.rec_empty_not_indexed)
                }
            RecEmptyReason.ERROR -> stringResource(R.string.rec_empty_error)
            RecEmptyReason.NO_RESULTS, null ->
                noResultsMessage ?: stringResource(R.string.rec_empty_no_results)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (reason == RecEmptyReason.ERROR || reason == RecEmptyReason.NO_RESULTS) {
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/** Localizes a domain [RecReason] into its user-facing "Sounds like X" caption. */
@Composable
private fun recReasonText(reason: RecReason): String = when (reason.kind) {
    RecReason.Kind.SOUNDS_LIKE -> stringResource(R.string.rec_reason_sounds_like, reason.arg.orEmpty())
    RecReason.Kind.BECAUSE_YOU_LIKE -> stringResource(R.string.rec_reason_because_you_like, reason.arg.orEmpty())
    RecReason.Kind.MORE_LIKE -> stringResource(R.string.rec_reason_more_like, reason.arg.orEmpty())
    RecReason.Kind.MATCHES_TASTE -> stringResource(R.string.rec_reason_matches_taste)
}
