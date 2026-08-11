package com.viperplayer.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.PlayingArtworkOverlay
import com.viperplayer.presentation.theme.Spacing

/**
 * The pinned host-generated **"Suggested for you"** surface, added at the TOP of Home's `LazyColumn`
 * (above the plugin sections, so it is never swept into the Wave 2 randomize-order shuffle). Renders one
 * of the [SuggestionsUiState] cases:
 *
 * - [SuggestionsUiState.Disabled] → a gentle, dismissible enable card.
 * - [SuggestionsUiState.Personalizing] → a subtle "personalizing… N/M" hint row.
 * - [SuggestionsUiState.Ready] → the titled multi-browse carousel (reusing Wave 1's card style), each
 *   card captioned by its "Sounds like X" [RecReason] when present.
 * - [SuggestionsUiState.Hidden] → nothing (don't nag on an empty/no-taste library).
 *
 * Pure rendering — playback + enable/dismiss are forwarded to the ViewModel via the callbacks.
 */
fun LazyListScope.suggestionsHomeSurface(
    state: SuggestionsUiState,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        SuggestionsUiState.Hidden -> Unit

        SuggestionsUiState.Disabled -> item(key = "suggestions-enable") {
            SuggestionsEnableCard(
                onEnable = onEnable,
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        is SuggestionsUiState.Personalizing -> item(key = "suggestions-personalizing") {
            SuggestionsPersonalizingRow(
                state = state,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        is SuggestionsUiState.Ready -> {
            item(key = "suggestions-title") {
                Text(
                    text = stringResource(R.string.suggestions_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            item(key = "suggestions-carousel") {
                SuggestionsCarousel(
                    songs = state.songs,
                    reasons = state.reasons,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onPlay = onPlay,
                )
            }
        }
    }
}

/**
 * The compact **"Suggested for you"** surface for the empty Search state: a title + a horizontal row of
 * small song tiles from the SAME on-device source, or a compact enable hint when recommendations are
 * off. Nothing renders while personalizing/hidden (keeps the empty-search state light). This is a
 * `LazyListScope` extension so it slots into the Search suggestions `LazyColumn`.
 */
fun LazyListScope.suggestionsSearchSurface(
    state: SuggestionsUiState,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onEnable: () -> Unit,
) {
    when (state) {
        SuggestionsUiState.Hidden -> Unit
        is SuggestionsUiState.Personalizing -> Unit

        SuggestionsUiState.Disabled -> item(key = "search-suggestions-enable") {
            SuggestionsEnableHint(
                onEnable = onEnable,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        is SuggestionsUiState.Ready -> {
            item(key = "search-suggestions-title") {
                Text(
                    text = stringResource(R.string.suggestions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
                )
            }
            item(key = "search-suggestions-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.songs, key = { "search-suggestion-${it.id}" }) { song ->
                        SuggestionTile(
                            song = song,
                            reason = state.reasons[song.id],
                            isActive = song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onPlay(song) },
                        )
                    }
                }
            }
        }
    }
}

/** The gentle, dismissible enable card for Home. */
@Composable
private fun SuggestionsEnableCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = stringResource(R.string.suggestions_enable_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.suggestions_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.suggestions_enable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.suggestions_enable_how_it_works),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = onEnable,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.suggestions_enable_action))
            }
        }
    }
}

/** The compact enable hint for the empty Search state (tappable row, no dismiss). */
@Composable
private fun SuggestionsEnableHint(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEnable),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.suggestions_enable_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.suggestions_enable_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A subtle "personalizing… N/M" hint row shown while the model downloads or the library indexes. */
@Composable
private fun SuggestionsPersonalizingRow(
    state: SuggestionsUiState.Personalizing,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        val text = if (state.hasProgress) {
            stringResource(R.string.suggestions_personalizing_progress, state.processed!!, state.total!!)
        } else {
            stringResource(R.string.suggestions_personalizing_generic)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Home suggestions carousel — reuses Wave 1's [HorizontalMultiBrowseCarousel] card style: large
 * scrim-over-artwork cards with the song title/subtitle and, when present, a "Sounds like X" reason
 * caption overlaid at the bottom. Neighbouring cards peek in at the edges.
 */
@Composable
private fun SuggestionsCarousel(
    songs: List<Song>,
    reasons: Map<MediaId, RecReason>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
) {
    val carouselState = rememberCarouselState { songs.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = Spacing.md,
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) { index ->
        val song = songs[index]
        val active = song.id == currentSongId
        SuggestionsCard(
            song = song,
            reason = reasons[song.id],
            isActive = active,
            isPlaying = active && isPlaying,
            modifier = Modifier
                .maskClip(MaterialTheme.shapes.extraLarge)
                .fillMaxWidth(),
            onClick = { onPlay(song) },
        )
    }
}

/** A single large scrim-over-artwork suggestion card with an optional reason caption. */
@Composable
private fun SuggestionsCard(
    song: Song,
    reason: RecReason?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = song.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    )
                )
        )
        PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.xl),
        ) {
            reason?.label()?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            song.artistNames?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Width of a compact Search-suggestion tile (small square art + title + reason). */
private val SearchSuggestionTileWidth = 132.dp

/** A compact square-artwork suggestion tile for the empty Search state. */
@Composable
private fun SuggestionTile(
    song: Song,
    reason: RecReason?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(SearchSuggestionTileWidth)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(SearchSuggestionTileWidth)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = ContentScale.Crop,
            )
            PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val caption = reason?.label() ?: song.artistNames
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Localizes a [RecReason] to its "Sounds like X" / "Because you like X" / … caption. */
@Composable
private fun RecReason.label(): String = when (kind) {
    RecReason.Kind.SOUNDS_LIKE -> stringResource(R.string.rec_reason_sounds_like, arg.orEmpty())
    RecReason.Kind.BECAUSE_YOU_LIKE -> stringResource(R.string.rec_reason_because_you_like, arg.orEmpty())
    RecReason.Kind.MORE_LIKE -> stringResource(R.string.rec_reason_more_like, arg.orEmpty())
    RecReason.Kind.MATCHES_TASTE -> stringResource(R.string.rec_reason_matches_taste)
}
