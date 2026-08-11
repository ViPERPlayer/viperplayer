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
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
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
import com.viperplayer.presentation.theme.Spacing

/**
 * The Account & sync screen (route `Account`, mockup 5e), reached from the You hub's "Manage
 * account" chip. Shows the signed-in identity, a library-sync card (toggle + status + "Sync now"),
 * an account-details card (with change-password + delete-account actions) and the sign-out action.
 * Sign-in / register live on their own [SignInScreen] / [RegisterScreen]; the signed-out fallback
 * here just routes back to sign-in.
 *
 * Render-only: all auth + sync logic stays in [AccountViewModel]; the change-password and
 * delete-account flows are in-place dialogs (no new nav route).
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
    val snackbarHostState = remember { SnackbarHostState() }
    val changePasswordSuccessMessage = stringResource(R.string.account_change_password_success)
    val setHandleSuccessMessage = stringResource(R.string.account_set_handle_success)
    var showChangePassword by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showSetHandle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.changePasswordSucceeded.collect {
            showChangePassword = false
            snackbarHostState.showSnackbar(changePasswordSuccessMessage)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.setHandleSucceeded.collect {
            showSetHandle = false
            snackbarHostState.showSnackbar(setHandleSuccessMessage)
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    PaddingValues(start = Spacing.lg, top = Spacing.sm, end = Spacing.lg, bottom = Spacing.lg) +
                        rootPadding.bottom(),
                ),
        ) {
            when {
                !uiState.isConfigured -> NotConfiguredCard()
                uiState.account.isSignedIn -> SignedInContent(
                    uiState = uiState,
                    showChangePassword = showChangePassword,
                    showDeleteAccount = showDeleteAccount,
                    showSetHandle = showSetHandle,
                    onShowChangePassword = { showChangePassword = it },
                    onShowDeleteAccount = { showDeleteAccount = it },
                    onShowSetHandle = { showSetHandle = it },
                    onClearError = viewModel::clearError,
                    onSignOut = viewModel::signOut,
                    onSyncToggle = viewModel::setSyncEnabled,
                    onSyncNow = viewModel::syncNow,
                    onChangePassword = viewModel::changePassword,
                    onDeleteAccount = viewModel::deleteAccount,
                    onSetHandle = viewModel::setHandle,
                )
                else -> SignedOutPrompt(onNavigateToSignIn)
            }
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    SurfaceCard(contentPadding = PaddingValues(Spacing.lg)) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.account_not_configured),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SignedInContent(
    uiState: AccountUiState,
    showChangePassword: Boolean,
    showDeleteAccount: Boolean,
    showSetHandle: Boolean,
    onShowChangePassword: (Boolean) -> Unit,
    onShowDeleteAccount: (Boolean) -> Unit,
    onShowSetHandle: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onChangePassword: (current: String, new: String) -> Unit,
    onDeleteAccount: (password: String) -> Unit,
    onSetHandle: (handle: String) -> Unit,
) {
    val account = uiState.account
    val user = account.user ?: return
    val displayName = user.displayName.ifBlank { user.email }
    val initials = displayName.firstOrNull()?.uppercase().orEmpty()

    ProfileHeader(displayName = displayName, email = user.email, initials = initials)

    Spacer(Modifier.height(18.dp))
    SectionLabel(
        text = stringResource(R.string.account_section_sync).uppercase(),
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
    )
    SurfaceCard {
        SyncToggleRow(
            enabled = uiState.syncEnabled,
            statusSubtitle = syncStatusSubtitle(uiState),
            onToggle = onSyncToggle,
        )
        InsetDivider()
        SyncNowRow(
            enabled = uiState.syncEnabled,
            isSyncing = uiState.isSyncing,
            onSyncNow = onSyncNow,
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel(
        text = stringResource(R.string.account_section_details).uppercase(),
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
    )
    SurfaceCard {
        FieldRow(
            leadingIcon = Icons.Rounded.Badge,
            label = stringResource(R.string.account_display_name_label),
            value = user.displayName.ifBlank { stringResource(R.string.account_display_name_unset) },
        )
        InsetDivider()
        ActionRow(
            leadingIcon = Icons.Rounded.AlternateEmail,
            title = user.handle
                ?.let { stringResource(R.string.account_handle_value, it) }
                ?: stringResource(R.string.account_set_handle),
            onClick = {
                onClearError()
                onShowSetHandle(true)
            },
        )
        InsetDivider()
        FieldRow(
            leadingIcon = Icons.Rounded.Email,
            label = stringResource(R.string.account_email),
            value = user.email,
        )
        if (uiState.memberSince.isNotBlank()) {
            InsetDivider()
            FieldRow(
                leadingIcon = Icons.Rounded.CalendarMonth,
                label = stringResource(R.string.account_member_since_label),
                value = uiState.memberSince,
            )
        }
        InsetDivider()
        ActionRow(
            leadingIcon = Icons.Rounded.Password,
            title = stringResource(R.string.account_change_password),
            onClick = {
                onClearError()
                onShowChangePassword(true)
            },
        )
    }

    Spacer(Modifier.height(Spacing.xl))
    SignOutButton(onSignOut = onSignOut)

    Spacer(Modifier.height(Spacing.sm))
    DeleteAccountLink(onClick = {
        onClearError()
        onShowDeleteAccount(true)
    })

    if (showSetHandle) {
        SetHandleDialog(
            currentHandle = user.handle,
            isSubmitting = uiState.isSubmitting,
            error = uiState.error,
            onConfirm = onSetHandle,
            onDismiss = {
                onShowSetHandle(false)
                onClearError()
            },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            isSubmitting = uiState.isSubmitting,
            error = uiState.error,
            onConfirm = onChangePassword,
            onDismiss = {
                onShowChangePassword(false)
                onClearError()
            },
        )
    }
    if (showDeleteAccount) {
        DeleteAccountDialog(
            isSubmitting = uiState.isSubmitting,
            error = uiState.error,
            onConfirm = onDeleteAccount,
            onDismiss = {
                onShowDeleteAccount(false)
                onClearError()
            },
        )
    }
}

/** Centered avatar + name + email block (mockup 5e header). */
@Composable
private fun ProfileHeader(displayName: String, email: String, initials: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.sm))
        AvatarRing(size = 92.dp, showRing = true) {
            InitialsAvatar(text = initials)
        }
        Spacer(Modifier.height(Spacing.md))
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

/** Sync-library toggle row: title + live status subtitle, with a trailing Switch. */
@Composable
private fun SyncToggleRow(
    enabled: Boolean,
    statusSubtitle: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.account_sync_toggle_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/** "Sync now" row: shows a spinner while a run is in flight; disabled when sync is off. */
@Composable
private fun SyncNowRow(
    enabled: Boolean,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val clickable = enabled && !isSyncing
    val contentColor = if (clickable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onSyncNow,
        enabled = clickable,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = if (isSyncing) {
                    stringResource(R.string.account_sync_in_progress)
                } else {
                    stringResource(R.string.account_sync_now)
                },
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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

/** A tappable account-action row: leading icon + title (e.g. "Change password"). */
@Composable
private fun ActionRow(
    leadingIcon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
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
        shape = RoundedCornerShape(Spacing.xxl),
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
                modifier = Modifier.padding(start = Spacing.sm),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Destructive "Delete account" text link below Sign out (opens the confirm dialog). */
@Composable
private fun DeleteAccountLink(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.account_delete),
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/**
 * In-place set / change `@handle` dialog (no nav route): a single handle field with an `@` prefix,
 * seeded with the [currentHandle]. Gated on a non-empty entry; the backend enforces the full
 * `^[a-z0-9_]{3,20}$` shape and rejects invalid (400) / taken (409) handles, surfaced via [error].
 * Confirming forwards the handle (no leading `@`) to [onConfirm].
 */
@Composable
private fun SetHandleDialog(
    currentHandle: String?,
    isSubmitting: Boolean,
    error: String?,
    onConfirm: (handle: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var handle by remember { mutableStateOf(currentHandle.orEmpty()) }
    val canSubmit = handle.isNotBlank() && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
        title = { Text(stringResource(R.string.account_set_handle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.account_set_handle_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AuthTextField(
                    // Accept an optionally-pasted leading "@" and normalise to lower case; the "@" is
                    // shown as a fixed prefix via the label, and stripped again below on submit.
                    value = handle,
                    onValueChange = { handle = it.removePrefix("@").lowercase() },
                    label = stringResource(R.string.account_handle_hint),
                    leadingIcon = Icons.Rounded.AlternateEmail,
                    keyboardType = KeyboardType.Ascii,
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(handle.removePrefix("@").trim()) },
                enabled = canSubmit,
            ) {
                Text(stringResource(R.string.account_set_handle_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_action_cancel))
            }
        },
    )
}

/**
 * In-place change-password dialog (no nav route): a current + new password field, gated on a minimum
 * new-password length. Surfaces the ViewModel [error] inline; confirming forwards to [onConfirm].
 */
@Composable
private fun ChangePasswordDialog(
    isSubmitting: Boolean,
    error: String?,
    onConfirm: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    val canSubmit = current.isNotBlank() && newPassword.length >= MinPasswordLength && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Password, contentDescription = null) },
        title = { Text(stringResource(R.string.account_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AuthTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = stringResource(R.string.account_change_password_current),
                    leadingIcon = Icons.Rounded.Lock,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = passwordTransformation(currentVisible),
                    trailing = {
                        PasswordVisibilityToggle(currentVisible) { currentVisible = !currentVisible }
                    },
                )
                AuthTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.account_change_password_new),
                    leadingIcon = Icons.Rounded.Password,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = passwordTransformation(newVisible),
                    trailing = {
                        PasswordVisibilityToggle(newVisible) { newVisible = !newVisible }
                    },
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, newPassword) },
                enabled = canSubmit,
            ) {
                Text(stringResource(R.string.account_change_password_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_action_cancel))
            }
        },
    )
}

/**
 * In-place delete-account confirm dialog (no nav route): requires the password, styled with the error
 * colour. Confirming forwards to [onConfirm]; on success the ViewModel clears the session and the
 * screen returns to signed-out (which unmounts this dialog).
 */
@Composable
private fun DeleteAccountDialog(
    isSubmitting: Boolean,
    error: String?,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val canSubmit = password.isNotBlank() && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.account_delete_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.account_delete_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AuthTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.account_delete_password),
                    leadingIcon = Icons.Rounded.Lock,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = passwordTransformation(visible),
                    trailing = {
                        PasswordVisibilityToggle(visible) { visible = !visible }
                    },
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = canSubmit,
            ) {
                Text(
                    text = stringResource(R.string.account_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_action_cancel))
            }
        },
    )
}

/** Fallback when the detail is opened without a session (e.g. right after sign-out): route to sign-in. */
@Composable
private fun SignedOutPrompt(onNavigateToSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxxl))
        Text(
            text = stringResource(R.string.you_hero_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Spacing.lg))
        FilledPill(
            text = stringResource(R.string.you_hero_sign_in),
            onClick = onNavigateToSignIn,
        )
    }
}

/** The sync-card status subtitle: "N songs · M albums · synced <rel>" / empty / "Not synced yet". */
@Composable
private fun syncStatusSubtitle(uiState: AccountUiState): String {
    val status = uiState.syncStatus
    return when {
        !status.hasSynced -> stringResource(R.string.account_sync_never)
        status.songs > 0 || status.albums > 0 -> stringResource(
            R.string.account_sync_status_counts,
            status.songs,
            status.albums,
            status.relativeTime,
        )
        else -> stringResource(R.string.account_sync_status_empty, status.relativeTime)
    }
}
