package com.viperplayer.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/**
 * The "Following" users list (social S1): the accounts the signed-in user follows, each with an
 * "Unfollow" toggle. A "Find people" action in the app bar routes to discovery. Backed by
 * [FollowingUsersViewModel]; when the backend is off it renders the gated state. Render-only — loading
 * and unfollow logic live in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingUsersScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToFindPeople: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowingUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.following_users_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.enabled) {
                        IconButton(onClick = onNavigateToFindPeople) {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = stringResource(R.string.find_people_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.enabled -> SocialGatedState(innerPadding = innerPadding, rootPadding = rootPadding)
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.users.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding),
            ) {
                SocialMessageState(message = state.error!!, rootPadding = rootPadding)
            }
            state.users.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding),
            ) {
                SocialMessageState(
                    message = stringResource(R.string.following_users_empty),
                    rootPadding = rootPadding,
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) + rootPadding.bottom(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.users, key = { it.userId }) { user ->
                    FollowUserRow(
                        user = user,
                        isFollowing = true,
                        onToggleFollow = { viewModel.onUnfollow(user) },
                    )
                }
            }
        }
    }
}
