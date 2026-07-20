package com.viperplayer.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.viperplayer.domain.account.AccountState
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.SurfaceCard

/**
 * The signed-in account detail (route `Account`), reached from the You hub's "Manage account" chip.
 * Sign-in / register now live on their own [SignInScreen] / [RegisterScreen]; this screen shows the
 * signed-in identity + sign-out. A later phase restyles it fully (avatar upload, sync, delete). Render
 * only — all auth logic stays in [AccountViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp + rootPadding.calculateBottomPadding()),
        ) {
            when {
                !uiState.isConfigured -> NotConfiguredCard()
                uiState.account.isSignedIn -> SignedInContent(uiState.account, viewModel::signOut)
                else -> SignedOutPrompt(onNavigateToSignIn)
            }
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    SurfaceCard(contentPadding = PaddingValues(16.dp)) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.account_not_configured),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SignedInContent(account: AccountState, onSignOut: () -> Unit) {
    val user = account.user ?: return
    val displayName = user.displayName.ifBlank { user.email }
    val initials = displayName.firstOrNull()?.uppercase().orEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        AvatarRing(size = 92.dp) {
            InitialsAvatar(text = initials)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
}

/** Fallback when the detail is opened without a session (e.g. right after sign-out): route to sign-in. */
@Composable
private fun SignedOutPrompt(onNavigateToSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.you_hero_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        FilledPill(
            text = stringResource(R.string.you_hero_sign_in),
            onClick = onNavigateToSignIn,
        )
    }
}
