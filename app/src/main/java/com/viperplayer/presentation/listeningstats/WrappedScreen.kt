package com.viperplayer.presentation.listeningstats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.stats.RankedItem
import com.viperplayer.domain.stats.WrappedSummary
import com.viperplayer.presentation.theme.Spacing
import java.util.Locale

/**
 * The "Wrapped" (year in review) story: a horizontally-paged sequence of full-screen cards. Reachable
 * from the stats screen. The [WrappedSummary] is computed once by the ViewModel via the pure
 * aggregator; this composable only renders it.
 */
@Composable
fun WrappedScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListeningStatsViewModel = hiltViewModel(),
) {
    val summary by viewModel.wrapped.collectAsStateWithLifecycle()
    WrappedContent(
        summary = summary,
        onClose = onNavigateBack,
        modifier = modifier,
    )
}

/** The distinct kinds of story card shown in the Wrapped sequence. */
private sealed interface WrappedCard {
    data object Intro : WrappedCard
    data object Minutes : WrappedCard
    data class TopSong(val item: RankedItem) : WrappedCard
    data class TopArtist(val item: RankedItem) : WrappedCard
    data class TopAlbum(val item: RankedItem) : WrappedCard
    data object Artists : WrappedCard
    data class TopArtistsList(val items: List<RankedItem>) : WrappedCard
    data class TopSongsList(val items: List<RankedItem>) : WrappedCard
    data class FunFact(val text: String) : WrappedCard
}

@Composable
fun WrappedContent(
    summary: WrappedSummary,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!summary.hasData) {
        WrappedEmpty(onClose = onClose, modifier = modifier)
        return
    }

    val funFact = funFactText(summary)
    val cards: List<WrappedCard> = remember(summary) {
        buildList {
            add(WrappedCard.Intro)
            add(WrappedCard.Minutes)
            summary.topSong?.let { add(WrappedCard.TopSong(it)) }
            if (summary.topSongs.size > 1) add(WrappedCard.TopSongsList(summary.topSongs))
            summary.topArtist?.let { add(WrappedCard.TopArtist(it)) }
            summary.topAlbum?.let { add(WrappedCard.TopAlbum(it)) }
            add(WrappedCard.Artists)
            if (summary.topArtists.size > 1) add(WrappedCard.TopArtistsList(summary.topArtists))
            funFact?.let { add(WrappedCard.FunFact(it)) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { cards.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // Alternate accent backgrounds so each story card feels distinct as you swipe.
            val accent = accentFor(page)
            WrappedCardSurface(background = accent) {
                when (val card = cards[page]) {
                    WrappedCard.Intro -> WrappedIntroCard(summary)
                    WrappedCard.Minutes -> WrappedMinutesCard(summary)
                    is WrappedCard.TopSong -> WrappedTopItemCard(
                        heading = stringResource(R.string.wrapped_top_song_title),
                        item = card.item,
                    )
                    is WrappedCard.TopArtist -> WrappedTopItemCard(
                        heading = stringResource(R.string.wrapped_top_artist_title),
                        item = card.item,
                    )
                    is WrappedCard.TopAlbum -> WrappedTopItemCard(
                        heading = stringResource(R.string.wrapped_top_album_title),
                        item = card.item,
                    )
                    is WrappedCard.TopSongsList -> WrappedLeaderboardCard(
                        heading = stringResource(R.string.wrapped_top_songs_title),
                        items = card.items,
                    )
                    WrappedCard.Artists -> WrappedArtistsCard(summary)
                    is WrappedCard.TopArtistsList -> WrappedLeaderboardCard(
                        heading = stringResource(R.string.wrapped_top_artists_title),
                        items = card.items,
                    )
                    is WrappedCard.FunFact -> WrappedFunFactCard(card.text)
                }
            }
        }

        // Page progress dots.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(cards.size) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else Spacing.sm)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_back),
            )
        }
    }
}

@Composable
private fun WrappedCardSurface(background: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = Spacing.xxxl, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun WrappedIntroCard(summary: WrappedSummary) {
    Text(
        text = stringResource(R.string.wrapped_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = stringResource(R.string.wrapped_intro_title, summary.year),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = stringResource(R.string.wrapped_intro_body),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WrappedMinutesCard(summary: WrappedSummary) {
    Text(
        text = stringResource(R.string.wrapped_minutes_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(
            R.string.wrapped_minutes_value,
            String.format(Locale.US, "%,d", summary.totalMinutes),
        ),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WrappedTopItemCard(heading: String, item: RankedItem) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = item.title,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    item.subtitle?.let {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(R.string.wrapped_plays_value, item.playCount),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun WrappedArtistsCard(summary: WrappedSummary) {
    Text(
        text = stringResource(R.string.wrapped_artists_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.wrapped_artists_value, summary.differentArtists),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(R.string.wrapped_songs_value, summary.differentSongs),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WrappedLeaderboardCard(heading: String, items: List<RankedItem>) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xxl))
    items.take(5).forEachIndexed { index, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.listening_stats_rank_prefix, index + 1),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WrappedFunFactCard(text: String) {
    Text(
        text = stringResource(R.string.wrapped_fun_fact_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WrappedEmpty(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.wrapped_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.wrapped_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_back),
            )
        }
    }
}

/** Builds the fun-fact line from whichever time signal is available (hour preferred, then day). */
@Composable
private fun funFactText(summary: WrappedSummary): String? = when {
    summary.busiestHour != null ->
        stringResource(R.string.wrapped_fun_fact_hour, StatsFormat.hourLabel(summary.busiestHour))
    summary.topDayLabelIndex != null ->
        stringResource(
            R.string.wrapped_fun_fact_day,
            StatsFormat.dayNames.getOrElse(summary.topDayLabelIndex) { "" },
        )
    else -> null
}

/** Distinct-but-cohesive accent colors, cycled across the story cards. */
@Composable
private fun accentFor(page: Int): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.surface,
        scheme.primaryContainer,
        scheme.tertiaryContainer,
        scheme.secondaryContainer,
    )
    return palette[page % palette.size]
}
