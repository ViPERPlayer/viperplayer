package com.viperplayer.follows.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.PersonGlyphAvatar
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/** Diameter of the circular artist avatar in a followed-artist row. */
private val ArtistAvatarSize = 56.dp

/**
 * Followed-artists list. Stateful wrapper: collects [FollowingViewModel] state and forwards taps
 * (open artist detail) and unfollow back to the ViewModel. Rendering lives in the stateless
 * [FollowingScreenContent].
 */
@Composable
fun FollowingScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowingViewModel = hiltViewModel(),
) {
    val artists by viewModel.uiModels.collectAsStateWithLifecycle()

    FollowingScreenContent(
        artists = artists,
        rootPadding = rootPadding,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onArtistClick = { artist ->
            onNavigateToArtist(Artist(id = artist.mediaId, name = artist.name, imageUrl = artist.artworkUrl))
        },
        onUnfollow = viewModel::unfollow,
    )
}

/**
 * Stateless followed-artists list (mockup 5j). A caption with the follow count, then a flat,
 * edge-to-edge list of artist rows — each an avatar, name, and a "Following" chip (tap to unfollow,
 * also reachable via long-press / the chip's own menu). Every interaction is forwarded as an event,
 * so this is testable without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreenContent(
    artists: List<FollowingUiModel>,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
    onUnfollow: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
) {
    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.following_title)) },
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
        if (artists.isEmpty()) {
            FollowingEmptyContent(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(rootPadding.bottom()),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .testTag("followingList"),
                contentPadding = PaddingValues(vertical = 8.dp) + rootPadding.bottom(),
            ) {
                item(key = "caption") {
                    Text(
                        text = stringResource(
                            R.string.following_subtitle,
                            pluralStringResource(
                                R.plurals.library_artist_count,
                                artists.size,
                                artists.size,
                            ),
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(
                    items = artists,
                    key = { model -> model.artist.mediaId.toString() },
                ) { model ->
                    FollowedArtistRow(
                        model = model,
                        onClick = { onArtistClick(model.artist) },
                        onUnfollow = { onUnfollow(model.artist.mediaId) },
                    )
                }
            }
        }
    }
}

/**
 * A single followed-artist row: circular avatar, name (plus an optional best-effort latest-release
 * line), and a "Following" chip. Tapping the row body opens the artist; tapping the chip (or
 * long-pressing the row) opens the unfollow menu, preserving the original screen's affordances.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowedArtistRow(
    model: FollowingUiModel,
    onClick: () -> Unit,
    onUnfollow: () -> Unit,
) {
    val artist = model.artist
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(name = artist.name, artworkUrl = artist.artworkUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val release = model.latestRelease
            if (release != null) {
                Text(
                    text = if (release.year != null) {
                        stringResource(R.string.following_latest_release, release.albumName, release.year)
                    } else {
                        stringResource(R.string.following_latest_release_no_year, release.albumName)
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            FollowingChip(
                onClick = { menuExpanded = true },
                contentDescription = stringResource(R.string.following_unfollow_cd, artist.name),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.following_unfollow)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.PersonRemove, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onUnfollow()
                    },
                    modifier = Modifier.testTag("unfollow_${artist.mediaId}"),
                )
            }
        }
    }
}

/** The tonal "Following" state chip: a check + label, tap to open the unfollow menu. */
@Composable
private fun FollowingChip(
    onClick: () -> Unit,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(R.string.following_state),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * A circular artist avatar: artwork when available, otherwise initials (or a person glyph when the
 * name is empty). Purely visual — the enclosing row owns the click/long-press.
 */
@Composable
private fun ArtistAvatar(
    name: String,
    artworkUrl: String?,
) {
    AvatarRing(size = ArtistAvatarSize) {
        when {
            artworkUrl != null -> AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            name.isNotBlank() -> InitialsAvatar(text = name.first().uppercase())
            else -> PersonGlyphAvatar()
        }
    }
}

@Composable
private fun FollowingEmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.PersonSearch,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.following_empty),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
