package com.viperplayer.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.account.AccountState
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/**
 * The Account & sync screen (route `Account`, mockup 5e), reached from the You hub's "Manage
 * account" chip. Shows the signed-in identity, a library-sync status card, an account-details card
 * and the sign-out action. Sign-in / register live on their own [SignInScreen] / [RegisterScreen];
 * the signed-out fallback here just routes back to sign-in.
 *
 * Render-only: all auth logic stays in [AccountViewModel]. The sync card is status-only — the
 * counts/timestamp, sync toggle and "Sync now" action from the mockup have no backing signal on
 * [AccountViewModel] (which exposes no sync API), so they are intentionally deferred rather than
 * faked; only the "library synced" status the app already surfaces on the You hub is shown here.
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
                title = { Text(stringResource(R.string.account_settings_entry)) },
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
                .padding(
                    PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp) +
                        rootPadding.bottom(),
                ),
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
            imageVector = Icons.Rounded.Warning,
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

    ProfileHeader(displayName = displayName, email = user.email, initials = initials)

    Spacer(Modifier.height(18.dp))
    SectionLabel(
        text = stringResource(R.string.account_section_sync).uppercase(),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    SurfaceCard {
        InfoRow(
            leadingIcon = Icons.Rounded.CloudDone,
            title = stringResource(R.string.you_library_synced),
            subtitle = stringResource(R.string.account_sync_subtitle),
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel(
        text = stringResource(R.string.account_section_details).uppercase(),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    SurfaceCard {
        FieldRow(
            leadingIcon = Icons.Rounded.Badge,
            label = stringResource(R.string.account_display_name_label),
            value = user.displayName.ifBlank { stringResource(R.string.account_display_name_unset) },
        )
        InsetDivider()
        FieldRow(
            leadingIcon = Icons.Rounded.Email,
            label = stringResource(R.string.account_email),
            value = user.email,
        )
    }

    Spacer(Modifier.height(20.dp))
    SignOutButton(onSignOut = onSignOut)
}

/** Centered avatar + name + email block (mockup 5e header). */
@Composable
private fun ProfileHeader(displayName: String, email: String, initials: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        AvatarRing(size = 92.dp) {
            InitialsAvatar(text = initials)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A status row inside a card: leading icon (primary-tinted), a title and a supporting subtitle. */
@Composable
private fun InfoRow(
    leadingIcon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A read-only account-detail row: leading icon, a small label above a larger value. */
@Composable
private fun FieldRow(
    leadingIcon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Full-width translucent-error "Sign out" action (mockup 5e). */
@Composable
private fun SignOutButton(onSignOut: () -> Unit) {
    Surface(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.error,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = stringResource(R.string.account_sign_out),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
