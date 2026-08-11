package com.viperplayer.presentation.settings.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.theme.Spacing

@Composable
fun UpdaterSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdaterSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_updater)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = uiState.updateState !is UpdateState.Checking
                    ) {
                        if (uiState.updateState is UpdateState.Checking) {
                            LoadingIndicator(
                                modifier = Modifier
                                    .padding(Spacing.sm)
                                    .size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.updater_check_for_updates)
                            )
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxSize(),
            // Combine the scaffold top inset with the mini-player/nav bottom inset so the last item clears it.
            contentPadding = contentPadding + rootPadding.bottom()
        ) {
            item {
                SettingsCategory(stringResource(R.string.updater_category_status))
            }
            item {
                UpdateStatusCard(updateState = uiState.updateState)
            }

            when (val state = uiState.updateState) {
                is UpdateState.UpdateAvailable -> {
                    if (state.changelog.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }
                        item {
                            SettingsCategory(stringResource(R.string.updater_category_changelog))
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.lg)
                                ) {
                                    Text(
                                        text = state.changelog,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                    item {
                        Button(
                            onClick = { viewModel.downloadUpdate() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg),
                            enabled = uiState.updateState !is UpdateState.Downloading
                        ) {
                            if (uiState.updateState is UpdateState.Downloading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.padding(horizontal = Spacing.sm))
                                Text(stringResource(R.string.updater_downloading))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.padding(horizontal = Spacing.sm))
                                Text(stringResource(R.string.updater_download_update))
                            }
                        }
                    }
                }

                is UpdateState.Error -> {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.lg)
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                else -> { /* No additional content for other states */
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    updateState: UpdateState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val (icon, title, subtitle, latestVersion) = when (updateState) {
                    is UpdateState.Checking -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            stringResource(R.string.updater_status_checking),
                            stringResource(R.string.updater_please_wait),
                            null
                        )
                    }

                    is UpdateState.NotConfigured -> {
                        Quadruple(
                            Icons.Default.Info,
                            stringResource(R.string.updater_not_configured),
                            stringResource(R.string.updater_not_configured_desc),
                            null
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Quadruple(
                            Icons.Default.CheckCircle,
                            stringResource(R.string.updater_up_to_date),
                            stringResource(R.string.updater_current_version, updateState.currentVersion),
                            null
                        )
                    }

                    is UpdateState.UpdateAvailable -> {
                        Quadruple(
                            Icons.Default.Download,
                            stringResource(R.string.updater_update_available),
                            stringResource(R.string.updater_current_version, updateState.currentVersion),
                            updateState.latestVersion
                        )
                    }

                    is UpdateState.Downloading -> {
                        Quadruple(
                            Icons.Default.Download,
                            stringResource(R.string.updater_downloading_update),
                            stringResource(R.string.updater_current_version, updateState.currentVersion),
                            updateState.latestVersion
                        )
                    }

                    is UpdateState.Error -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            stringResource(R.string.action_error),
                            stringResource(R.string.updater_current_version, updateState.currentVersion),
                            null
                        )
                    }

                    is UpdateState.Idle -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            stringResource(R.string.updater_not_checked),
                            stringResource(R.string.updater_current_version_unknown),
                            null
                        )
                    }
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when (updateState) {
                        is UpdateState.UpdateAvailable, is UpdateState.Downloading -> {
                            MaterialTheme.colorScheme.primary
                        }

                        is UpdateState.UpToDate -> {
                            MaterialTheme.colorScheme.tertiary
                        }

                        is UpdateState.Error -> {
                            MaterialTheme.colorScheme.error
                        }

                        else -> {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                )
                Spacer(modifier = Modifier.padding(horizontal = Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (latestVersion != null) {
                        Text(
                            text = stringResource(R.string.updater_latest_version, latestVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
