package com.viperplayer.presentation.settings.lastfm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.theme.Spacing

@Composable
fun LastfmSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LastfmSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LastfmSettingsContent(
        state = uiState,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onScrobblingEnabled = viewModel::setScrobblingEnabled,
        onNowPlayingEnabled = viewModel::setNowPlayingEnabled,
        onScrobbleOnWifiOnly = viewModel::setScrobbleOnWifiOnly,
        modifier = modifier,
    )
}

/**
 * Stateless body of the Last.fm settings screen — takes the resolved [LastfmSettingsUiState] + event
 * lambdas so it can be driven directly from a Compose UI test without Hilt.
 */
@Composable
fun LastfmSettingsContent(
    state: LastfmSettingsUiState,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onScrobblingEnabled: (Boolean) -> Unit,
    onNowPlayingEnabled: (Boolean) -> Unit,
    onScrobbleOnWifiOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_lastfm)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = modifier
                .padding(contentPadding)
                .fillMaxWidth()
                .fillMaxSize(),
            contentPadding = rootPadding.bottom()
        ) {
            if (!state.isApiConfigured) {
                item { NotConfiguredNotice() }
            }

            item { SettingsCategory(stringResource(R.string.lastfm_account)) }
            item {
                if (state.settings.isAuthenticated) {
                    LoggedInCard(username = state.settings.username, onLogout = onLogout)
                } else {
                    LoginCard(
                        isLoggingIn = state.isLoggingIn,
                        error = state.loginError,
                        onLogin = onLogin,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(Spacing.sm)) }
            item { SettingsCategory(stringResource(R.string.lastfm_scrobbling)) }
            item {
                SwitchItem(
                    title = stringResource(R.string.lastfm_scrobble_enabled),
                    description = stringResource(R.string.lastfm_scrobble_enabled_desc),
                    icon = Icons.Default.Podcasts,
                    checked = state.settings.scrobblingEnabled,
                    onCheckedChange = onScrobblingEnabled,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lastfm_now_playing_enabled),
                    description = stringResource(R.string.lastfm_now_playing_enabled_desc),
                    icon = Icons.Default.AccountCircle,
                    checked = state.settings.nowPlayingEnabled,
                    onCheckedChange = onNowPlayingEnabled,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lastfm_wifi_only),
                    description = stringResource(R.string.lastfm_wifi_only_desc),
                    icon = Icons.Default.NetworkWifi,
                    checked = state.settings.scrobbleOnWifiOnly,
                    onCheckedChange = onScrobbleOnWifiOnly,
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredNotice(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(R.string.lastfm_not_configured_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.lastfm_not_configured_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer)
        )
    }
}

@Composable
private fun LoggedInCard(
    username: String?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.lastfm_logged_in_as, username.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md),
            ) {
                Text(stringResource(R.string.lastfm_sign_out))
            }
        }
    }
}

@Composable
private fun LoginCard(
    isLoggingIn: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Credentials are held only in local UI state while typing; the password is passed straight to the
    // login callback and never lifted into the ViewModel/persistence.
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.lastfm_username)) },
                singleLine = true,
                enabled = !isLoggingIn,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.lastfm_password)) },
                singleLine = true,
                enabled = !isLoggingIn,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.lastfm_password_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onLogin(username, password) },
                enabled = !isLoggingIn && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(Spacing.xl),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(
                        text = stringResource(R.string.lastfm_signing_in),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                } else {
                    Text(stringResource(R.string.lastfm_sign_in))
                }
            }
        }
    }
}

@Composable
private fun SettingsCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .toggleable(value = checked, onValueChange = onCheckedChange),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            leadingContent = {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        )
    }
}
