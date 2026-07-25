package com.viperplayer.presentation.settings.content

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
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
        }
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
