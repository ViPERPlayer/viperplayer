package com.viperplayer.presentation.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.account.AccountState
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.ActivityDot
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.CountBadge
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.common.components.SurfaceCardRow
import com.viperplayer.presentation.common.components.TonalChip
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/** Alpha applied to locked (signed-out) social rows so they read as gated. */
private const val LockedRowAlpha = 0.55f

/**
 * The You hub: account + sync status, plus grouped SOCIAL / MUSIC / SETUP cards. A pure render surface
 * — every value comes from [YouViewModel]. Reachable-today destinations (Plugins, History, stats,
 * Downloads, Last.fm, Alarms, Settings) reuse the same routes the old Settings/Home used.
 */
@Composable
fun YouScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToFriendActivity: () -> Unit,
    onNavigateToSharedWithYou: () -> Unit,
    onNavigateToJoinSession: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToListeningStats: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLastfm: () -> Unit,
    onNavigateToAlarms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    YouContent(
        state = uiState,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToSignIn = onNavigateToSignIn,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToFriendActivity = onNavigateToFriendActivity,
        onNavigateToSharedWithYou = onNavigateToSharedWithYou,
        onNavigateToJoinSession = onNavigateToJoinSession,
        onNavigateToPlugins = onNavigateToPlugins,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToListeningStats = onNavigateToListeningStats,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToLastfm = onNavigateToLastfm,
        onNavigateToAlarms = onNavigateToAlarms,
        onSyncNow = viewModel::syncNow,
        modifier = modifier,
    )
}

@Composable
private fun YouContent(
    state: YouUiState,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToFriendActivity: () -> Unit,
    onNavigateToSharedWithYou: () -> Unit,
    onNavigateToJoinSession: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToListeningStats: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLastfm: () -> Unit,
    onNavigateToAlarms: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signedIn = state.account.isSignedIn
    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.you_open_settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp) + rootPadding.bottom(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                if (signedIn) {
                    SignedInHeader(
                        account = state.account,
                        friendsLive = state.socialEnabled && state.firstListeningFriendName != null,
                        syncStatus = state.librarySyncStatus,
                        onManageAccount = onNavigateToAccount,
                        onSyncNow = onSyncNow,
                    )
                } else {
                    SignInHero(
                        onSignIn = onNavigateToSignIn,
                        onCreateAccount = onNavigateToRegister,
                    )
                }
            }

            if (state.socialEnabled) {
                item(key = "social-label") { GroupLabel(stringResource(R.string.you_section_social)) }
                item(key = "social-card") {
                    SocialCard(
                        state = state,
                        signedIn = signedIn,
                        onFriendActivity = onNavigateToFriendActivity,
                        onSharedWithYou = onNavigateToSharedWithYou,
                        onJoinJam = onNavigateToJoinSession,
                        onSignIn = onNavigateToSignIn,
                    )
                }
            }

            item(key = "music-label") { GroupLabel(stringResource(R.string.you_section_music)) }
            item(key = "music-card") {
                MusicCard(
                    state = state,
                    onPlugins = onNavigateToPlugins,
                    onHistory = onNavigateToHistory,
                    onStats = onNavigateToListeningStats,
                    onDownloads = onNavigateToDownloads,
                )
            }

            item(key = "setup-label") { GroupLabel(stringResource(R.string.you_section_setup)) }
            item(key = "setup-card") {
                SetupCard(
                    state = state,
                    onLastfm = onNavigateToLastfm,
                    onAlarms = onNavigateToAlarms,
                    onSettings = onNavigateToSettings,
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    SectionLabel(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SignedInHeader(
    account: AccountState,
    friendsLive: Boolean,
    syncStatus: LibrarySyncStatusUi,
    onManageAccount: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val user = account.user
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email.orEmpty()
    val initials = displayName.firstOrNull()?.uppercase().orEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        AvatarRing(size = 92.dp, hasLiveActivity = friendsLive, showRing = true) {
            InitialsAvatar(text = initials)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!user?.email.isNullOrBlank()) {
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        TonalChip(
            text = stringResource(R.string.you_manage_account),
            onClick = onManageAccount,
        )
        Spacer(Modifier.height(16.dp))
        LibrarySyncCard(onSyncNow = onSyncNow)
    }
}

@Composable
private fun LibrarySyncCard(onSyncNow: () -> Unit) {
    SurfaceCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.you_library_synced),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(
                onClick = onSyncNow,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = stringResource(R.string.you_library_sync_now),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SignInHero(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    // Gradient primaryContainer hero (the mockup's #4F378B→#332D41 maps to primaryContainer→surface).
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(gradient, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.you_hero_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.you_hero_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledPill(
                text = stringResource(R.string.you_hero_sign_in),
                onClick = onSignIn,
            )
            TextButton(onClick = onCreateAccount) {
                Text(
                    text = stringResource(R.string.you_hero_create_account),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SocialCard(
    state: YouUiState,
    signedIn: Boolean,
    onFriendActivity: () -> Unit,
    onSharedWithYou: () -> Unit,
    onJoinJam: () -> Unit,
    onSignIn: () -> Unit,
) {
    val friendActivitySubtitle = friendActivitySubtitle(state)
    SurfaceCard {
        if (signedIn) {
            SurfaceCardRow(
                title = stringResource(R.string.you_friend_activity),
                subtitle = friendActivitySubtitle,
                leadingIcon = Icons.Rounded.Group,
                onClick = onFriendActivity,
                trailing = if (state.firstListeningFriendName != null) {
                    { ActivityDot() }
                } else null,
            )
            InsetDivider()
            SurfaceCardRow(
                title = stringResource(R.string.you_shared_with_you),
                subtitle = stringResource(R.string.you_shared_with_you_subtitle),
                leadingIcon = Icons.Rounded.Inbox,
                onClick = onSharedWithYou,
                trailing = if (state.sharedUnreadCount > 0) {
                    { CountBadge(count = state.sharedUnreadCount) }
                } else null,
            )
            InsetDivider()
        } else {
            LockedRow(
                title = stringResource(R.string.you_friend_activity),
                leadingIcon = Icons.Rounded.Group,
                onClick = onSignIn,
            )
            InsetDivider()
            LockedRow(
                title = stringResource(R.string.you_shared_with_you),
                leadingIcon = Icons.Rounded.Inbox,
                onClick = onSignIn,
            )
            InsetDivider()
        }
        // Join a Jam stays enabled in both states (guests can join with a code).
        SurfaceCardRow(
            title = stringResource(R.string.you_join_a_jam),
            subtitle = stringResource(R.string.you_join_a_jam_subtitle),
            leadingIcon = Icons.Rounded.QrCodeScanner,
            onClick = onJoinJam,
        )
    }
}

/** A gated social row: muted to [LockedRowAlpha] with a trailing lock; tapping routes to sign-in. */
@Composable
private fun LockedRow(
    title: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
) {
    SurfaceCardRow(
        modifier = Modifier.alpha(LockedRowAlpha),
        title = title,
        leadingIcon = leadingIcon,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.you_locked_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun MusicCard(
    state: YouUiState,
    onPlugins: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onDownloads: () -> Unit,
) {
    val pluginsSubtitle = if (state.connectedPluginCount > 0) {
        stringResource(
            R.string.you_plugins_status,
            state.connectedPluginCount,
            state.pluginUpdateCount,
        )
    } else {
        stringResource(R.string.you_plugins_manage)
    }
    SurfaceCard {
        SurfaceCardRow(
            title = stringResource(R.string.you_plugins),
            subtitle = pluginsSubtitle,
            leadingIcon = Icons.Rounded.Extension,
            onClick = onPlugins,
            trailing = if (state.pluginUpdateCount > 0) {
                { ActivityDot() }
            } else null,
        )
        InsetDivider()
        SurfaceCardRow(
            title = stringResource(R.string.you_history),
            leadingIcon = Icons.Rounded.History,
            onClick = onHistory,
        )
        InsetDivider()
        SurfaceCardRow(
            title = stringResource(R.string.you_listening_stats),
            leadingIcon = Icons.Rounded.BarChart,
            onClick = onStats,
        )
        InsetDivider()
        SurfaceCardRow(
            title = stringResource(R.string.you_downloads),
            leadingIcon = Icons.Rounded.Download,
            onClick = onDownloads,
        )
    }
}

@Composable
private fun SetupCard(
    state: YouUiState,
    onLastfm: () -> Unit,
    onAlarms: () -> Unit,
    onSettings: () -> Unit,
) {
    val lastfm = state.lastfm
    val lastfmSubtitle = if (lastfm.connected && !lastfm.username.isNullOrBlank()) {
        stringResource(R.string.you_lastfm_scrobbling_as, lastfm.username)
    } else {
        stringResource(R.string.you_lastfm_not_connected)
    }
    SurfaceCard {
        SurfaceCardRow(
            title = stringResource(R.string.you_lastfm),
            subtitle = lastfmSubtitle,
            leadingIcon = Icons.Rounded.Podcasts,
            onClick = onLastfm,
            trailing = if (lastfm.connected) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else null,
        )
        InsetDivider()
        SurfaceCardRow(
            title = stringResource(R.string.you_alarms),
            leadingIcon = Icons.Rounded.Alarm,
            onClick = onAlarms,
        )
        InsetDivider()
        SurfaceCardRow(
            title = stringResource(R.string.you_app_settings),
            subtitle = stringResource(R.string.you_app_settings_subtitle),
            leadingIcon = Icons.Rounded.Settings,
            onClick = onSettings,
        )
    }
}

/** Friend-activity subtitle: "<name> and N others listening now" / "<name> is listening now" / default. */
@Composable
private fun friendActivitySubtitle(state: YouUiState): String {
    val name = state.firstListeningFriendName ?: return stringResource(R.string.you_friend_activity_subtitle)
    return if (state.otherListenersCount > 0) {
        stringResource(R.string.you_friend_activity_listening_now, name, state.otherListenersCount)
    } else {
        stringResource(R.string.you_friend_activity_one_listening, name)
    }
}
