package com.viperplayer.follows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.search.model.SearchItem

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
    val artists by viewModel.followedArtists.collectAsStateWithLifecycle()

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
 * Stateless followed-artists list. Renders each artist with an unfollow menu (also reachable via
 * long-press); forwards every interaction as an event. Testable without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreenContent(
    artists: List<FollowedArtist>,
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    .padding(rootPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .testTag("followingList"),
                contentPadding = rootPadding,
            ) {
                items(artists, key = { it.mediaId.toString() }) { artist ->
                    FollowedArtistRow(
                        artist = artist,
                        onClick = { onArtistClick(artist) },
                        onUnfollow = { onUnfollow(artist.mediaId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowedArtistRow(
    artist: FollowedArtist,
    onClick: () -> Unit,
    onUnfollow: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            type = SearchItem.Type.ARTIST,
            title = artist.name,
            badges = emptyList(),
            subtitle = null,
            artworkUrl = artist.artworkUrl,
            isActive = false,
            isPlaying = false,
            onClick = onClick,
            onMoreClick = { menuExpanded = true },
            onLongClick = { menuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.following_unfollow)) },
                leadingIcon = {
                    Icon(Icons.Default.PersonRemove, contentDescription = null)
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
                imageVector = Icons.Outlined.PersonSearch,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.following_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
