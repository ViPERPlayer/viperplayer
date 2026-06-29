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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.presentation.common.ViperScaffold

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
                title = { Text("Updater") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
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
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Check for updates"
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
            contentPadding = contentPadding
        ) {
            item {
                SettingsCategory("Update Status")
            }
            item {
                UpdateStatusCard(updateState = uiState.updateState)
            }

            when (val state = uiState.updateState) {
                is UpdateState.UpdateAvailable -> {
                    if (state.changelog.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        item {
                            SettingsCategory("Changelog")
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        Button(
                            onClick = { viewModel.downloadUpdate() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            enabled = uiState.updateState !is UpdateState.Downloading
                        ) {
                            if (uiState.updateState is UpdateState.Downloading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                Text("Downloading...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                Text("Download Update")
                            }
                        }
                    }
                }

                is UpdateState.Error -> {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val (icon, title, subtitle, latestVersion) = when (updateState) {
                    is UpdateState.Checking -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            "Checking for updates...",
                            "Please wait",
                            null
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Quadruple(
                            Icons.Default.CheckCircle,
                            "App is Up-to-Date",
                            "Current version: ${updateState.currentVersion}",
                            null
                        )
                    }

                    is UpdateState.UpdateAvailable -> {
                        Quadruple(
                            Icons.Default.Download,
                            "Update Available",
                            "Current version: ${updateState.currentVersion}",
                            updateState.latestVersion
                        )
                    }

                    is UpdateState.Downloading -> {
                        Quadruple(
                            Icons.Default.Download,
                            "Downloading Update",
                            "Current version: ${updateState.currentVersion}",
                            updateState.latestVersion
                        )
                    }

                    is UpdateState.Error -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            "Error",
                            "Current version: ${updateState.currentVersion}",
                            null
                        )
                    }

                    is UpdateState.Idle -> {
                        Quadruple(
                            Icons.Default.Refresh,
                            "Not Checked",
                            "Current version: Unknown",
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
                Spacer(modifier = Modifier.padding(horizontal = 12.dp))
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
                            text = "Latest version: $latestVersion",
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
