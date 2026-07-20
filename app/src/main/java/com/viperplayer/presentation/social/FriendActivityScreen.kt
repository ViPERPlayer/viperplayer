package com.viperplayer.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.presentation.common.components.SurfaceCard

/**
 * Placeholder Friend-activity feed (route `FriendActivity`). The full feed (backed by
 * [com.viperplayer.domain.social.FriendActivityRepository]) is a later phase; this renders a titled
 * empty-state card so the You hub link resolves and works when social is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendActivityScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SocialPlaceholderScaffold(
        title = stringResource(R.string.friend_activity_title),
        emptyMessage = stringResource(R.string.friend_activity_empty),
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * Placeholder Shared-with-you inbox (route `SharedWithYou`). The full inbox (backed by
 * [com.viperplayer.domain.social.SharedPlaylistsRepository]) is a later phase; this renders a titled
 * empty-state card so the You hub link resolves and works when social is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedWithYouScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SocialPlaceholderScaffold(
        title = stringResource(R.string.shared_with_you_title),
        emptyMessage = stringResource(R.string.shared_with_you_empty),
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialPlaceholderScaffold(
    title: String,
    emptyMessage: String,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = rootPadding.calculateTopPadding())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + rootPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
