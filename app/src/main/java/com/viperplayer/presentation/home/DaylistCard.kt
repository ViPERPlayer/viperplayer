package com.viperplayer.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.presentation.theme.Spacing

/**
 * The pinned Home **Daylist card**: a prominent Featured-style card near the top of Home (just below the
 * "Suggested for you" surface). It shows the generated daylist title + description and a small artwork
 * mosaic; tapping it opens the full Daylist screen. Added as a `LazyListScope` item, above the plugin
 * sections, so — like the Suggestions surface — the Wave 2 randomize-order shuffle never moves it.
 *
 * Hidden entirely when there is no daylist ([DaylistCardState.Hidden]) — no nag on a cold library.
 *
 * Pure rendering — the "open" tap is forwarded to the caller (which navigates to the Daylist screen).
 */
fun LazyListScope.daylistHomeSurface(
    state: DaylistCardState,
    onOpen: () -> Unit,
) {
    when (state) {
        DaylistCardState.Hidden -> Unit
        is DaylistCardState.Ready -> item(key = "daylist-card") {
            DaylistCard(
                state = state,
                onOpen = onOpen,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}

/** A single prominent daylist card: label chip, generated title, description, and an artwork mosaic. */
@Composable
private fun DaylistCard(
    state: DaylistCardState.Ready,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.daylist_card_cta), onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.daylist_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.artworkUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.width(Spacing.lg))
                DaylistArtworkMosaic(
                    artworkUrls = state.artworkUrls,
                    modifier = Modifier.size(84.dp),
                )
            }
        }
    }
}

/**
 * A compact 2x2 artwork mosaic for the card. Renders up to four artworks (fewer stretch to fill), so the
 * card carries a taste-of-the-mix preview without a full carousel.
 */
@Composable
private fun DaylistArtworkMosaic(
    artworkUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    val urls = artworkUrls.take(4)
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        when (urls.size) {
            1 -> MosaicImage(urls[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize()) {
                MosaicImage(urls[0], Modifier.weight(1f).fillMaxSize())
                MosaicImage(urls[1], Modifier.weight(1f).fillMaxSize())
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    MosaicImage(urls[0], Modifier.weight(1f).fillMaxSize())
                    MosaicImage(urls.getOrElse(1) { urls[0] }, Modifier.weight(1f).fillMaxSize())
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    MosaicImage(urls.getOrElse(2) { urls[0] }, Modifier.weight(1f).fillMaxSize())
                    MosaicImage(urls.getOrElse(3) { urls.getOrElse(1) { urls[0] } }, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun MosaicImage(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}
