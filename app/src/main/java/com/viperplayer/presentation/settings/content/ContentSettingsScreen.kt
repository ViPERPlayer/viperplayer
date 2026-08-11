package com.viperplayer.presentation.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.domain.rec.FailureReason
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom

@Composable
fun ContentSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContentSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_content)) },
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
            item {
                SettingsCategory(stringResource(R.string.content_category_filters))
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Explicit,
                    title = stringResource(R.string.content_show_explicit),
                    description = stringResource(R.string.content_show_explicit_desc),
                    checked = uiState.showExplicitContent,
                    onCheckedChange = viewModel::setShowExplicitContent
                )
            }

            item {
                SettingsCategory(stringResource(R.string.content_category_home))
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Shuffle,
                    title = stringResource(R.string.content_randomize_home_order),
                    description = stringResource(R.string.content_randomize_home_order_desc),
                    checked = uiState.randomizeHomeOrder,
                    onCheckedChange = viewModel::setRandomizeHomeOrder
                )
            }

            item {
                SettingsCategory(stringResource(R.string.content_category_offline))
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.content_offline_mode),
                    description = stringResource(R.string.content_offline_mode_desc),
                    checked = uiState.offlineMode,
                    onCheckedChange = viewModel::setOfflineMode
                )
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Downloading,
                    title = stringResource(R.string.content_auto_download_on_like),
                    description = stringResource(R.string.content_auto_download_on_like_desc),
                    checked = uiState.autoDownloadOnLike,
                    onCheckedChange = viewModel::setAutoDownloadOnLike
                )
            }

            item {
                SettingsCategory(stringResource(R.string.content_category_recommendations))
            }
            item {
                RecommendationsRow(
                    enabled = uiState.recommendationsEnabled,
                    status = uiState.recommendationsStatus,
                    onCheckedChange = viewModel::setRecommendationsEnabled,
                    onRetry = viewModel::retryRecommendationsDownload
                )
            }
            // Anonymized-taste discovery contribution (privacy opt-in). Only meaningful once smart
            // recommendations are on (the shared taste model produces the vector), so it's gated on it.
            if (uiState.recommendationsEnabled) {
                item {
                    SettingsSwitchRow(
                        icon = Icons.Default.Insights,
                        title = stringResource(R.string.content_contribute_taste),
                        description = stringResource(R.string.content_contribute_taste_desc),
                        checked = uiState.contributeAnonymizedTaste,
                        onCheckedChange = viewModel::setContributeAnonymizedTaste
                    )
                }
            }
        }
    }
}

/**
 * The smart-recommendations toggle with a live status line (off / waiting-for-Wi-Fi / downloading %
 * / ready / failed-with-retry / app-outdated), driven by [status]. All copy is resolved from
 * resources here; the ViewModel only supplies the string-free [RecommendationsUiStatus].
 */
@Composable
private fun RecommendationsRow(
    enabled: Boolean,
    status: RecommendationsUiStatus,
    onCheckedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
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
        Column {
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.content_smart_recommendations),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.content_smart_recommendations_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onCheckedChange
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
            RecommendationsStatusLine(status = status, onRetry = onRetry)
        }
    }
}

/** The status line + optional retry action under the recommendations toggle. */
@Composable
private fun RecommendationsStatusLine(
    status: RecommendationsUiStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = statusText(status) ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (status is RecommendationsUiStatus.Failed || status is RecommendationsUiStatus.AppOutdated) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.weight(1f, fill = false)
        )
        if (status is RecommendationsUiStatus.Failed) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.rec_action_retry))
            }
        }
    }
}

/** Resolves the localized status line for [status], or null when nothing should be shown. */
@Composable
private fun statusText(status: RecommendationsUiStatus): String? = when (status) {
    RecommendationsUiStatus.Off -> null
    RecommendationsUiStatus.Ready -> stringResource(R.string.rec_status_ready)
    is RecommendationsUiStatus.Indexing ->
        stringResource(R.string.rec_status_indexing, status.processed, status.total)
    RecommendationsUiStatus.WaitingForWifi -> stringResource(R.string.rec_status_waiting_wifi)
    is RecommendationsUiStatus.Downloading ->
        status.percent?.let { stringResource(R.string.rec_status_downloading, it) }
            ?: stringResource(R.string.rec_status_downloading_indeterminate)
    RecommendationsUiStatus.AppOutdated -> stringResource(R.string.rec_status_app_outdated)
    is RecommendationsUiStatus.Failed -> when (status.reason) {
        FailureReason.BACKEND_NOT_CONFIGURED -> stringResource(R.string.rec_error_backend_not_configured)
        FailureReason.MANIFEST_UNAVAILABLE -> stringResource(R.string.rec_error_manifest_unavailable)
        FailureReason.CHECKSUM_MISMATCH -> stringResource(R.string.rec_error_checksum)
        FailureReason.NETWORK_OR_IO -> stringResource(R.string.rec_error_network)
        FailureReason.NEEDS_UNMETERED_NETWORK -> stringResource(R.string.rec_error_needs_wifi)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
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
