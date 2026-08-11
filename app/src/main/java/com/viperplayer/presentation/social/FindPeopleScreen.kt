package com.viperplayer.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.social.FollowUser
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.theme.Spacing

/**
 * "Find people" discovery screen (social S1): a search field that queries [FollowUser]s by name/handle
 * and lists each with an avatar, `@handle`, display name, and a Follow / Following toggle. Backed by
 * [FindPeopleViewModel]; when the backend is off it renders the gated state. Render-only — the search,
 * debounce and follow/unfollow logic all live in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPeopleScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FindPeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_people_title)) },
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
        if (!state.enabled) {
            SocialGatedState(innerPadding = innerPadding, rootPadding = rootPadding)
            return@ViperScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.find_people_search_hint)) },
            )
            when {
                state.searching && state.results.isEmpty() -> SocialLoadingState()
                state.error != null && state.results.isEmpty() ->
                    SocialMessageState(
                        message = state.error!!,
                        rootPadding = rootPadding,
                    )
                state.query.isBlank() ->
                    SocialMessageState(
                        message = stringResource(R.string.find_people_prompt),
                        rootPadding = rootPadding,
                    )
                state.results.isEmpty() ->
                    SocialMessageState(
                        message = stringResource(R.string.find_people_no_results),
                        rootPadding = rootPadding,
                    )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm) + rootPadding.bottom(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(items = state.results, key = { it.userId }) { user ->
                        FollowUserRow(
                            user = user,
                            isFollowing = user.userId in state.followingIds,
                            onToggleFollow = { viewModel.onToggleFollow(user) },
                        )
                    }
                }
            }
        }
    }
}

/** Centered spinner while a search is in flight. */
@Composable
private fun SocialLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** A centered informational card (prompt / no-results / error), matching the other social states. */
@Composable
internal fun SocialMessageState(
    message: String,
    rootPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .padding(rootPadding.bottom()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SurfaceCard(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 32.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
