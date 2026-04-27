package com.viperplayer.presentation.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom

/**
 * Main settings screen with navigation to subsections.
 */
@Composable
fun SettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToContent: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdater: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
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
                SettingsCategory("Settings")
            }
            item {
                SettingsSectionItem(
                    title = "Appearance",
                    description = "Theme, colors, and display",
                    icon = Icons.Default.Palette,
                    onClick = onNavigateToAppearance
                )
            }
            item {
                SettingsSectionItem(
                    title = "Player & Audio",
                    description = "Audio quality, playback, and normalization",
                    icon = Icons.AutoMirrored.Default.VolumeUp,
                    onClick = onNavigateToPlayer
                )
            }
            item {
                SettingsSectionItem(
                    title = "Content",
                    description = "Content filters and preferences",
                    icon = Icons.Default.MusicNote,
                    onClick = onNavigateToContent
                )
            }
            item {
                SettingsSectionItem(
                    title = "Storage",
                    description = "Cache and download management",
                    icon = Icons.Default.Storage,
                    onClick = onNavigateToStorage
                )
            }

            item {
                SettingsSectionItem(
                    title = "Plugins",
                    description = "Manage music source plugins",
                    icon = Icons.Default.Extension,
                    onClick = onNavigateToPlugins
                )
            }
            item {
                SettingsSectionItem(
                    title = "Scan Local Files",
                    description = "Scan device for local audio files",
                    icon = Icons.Default.Refresh,
                    onClick = viewModel::scanLocalFiles
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsCategory("System")
            }
            item {
                SettingsSectionItem(
                    title = "Updater",
                    description = "Check for app updates",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onNavigateToUpdater
                )
            }
            item {
                SettingsSectionItem(
                    title = "About",
                    description = "App information and credits",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToAbout
                )
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

@Composable
private fun SettingsSectionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
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
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }
}
