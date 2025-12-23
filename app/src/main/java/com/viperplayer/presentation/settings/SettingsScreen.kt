package com.viperplayer.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen with its own Scaffold and top bar.
 * This screen doesn't show the bottom navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Appearance Section
            item {
                SettingsSectionHeader("Appearance")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = "System default",
                    onClick = { /* TODO: Open theme picker */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Automatic",
                    onClick = { /* TODO: Open dark mode options */ }
                )
            }

            // Playback Section
            item {
                SettingsSectionHeader("Playback")
            }

            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Audio Quality",
                    subtitle = "High",
                    onClick = { /* TODO: Open quality settings */ }
                )
            }

            item {
                var gaplessEnabled by remember { mutableStateOf(true) }
                SettingsSwitchItem(
                    icon = Icons.Default.Queue,
                    title = "Gapless Playback",
                    subtitle = "Seamless transitions between tracks",
                    checked = gaplessEnabled,
                    onCheckedChange = { gaplessEnabled = it }
                )
            }

            item {
                var crossfadeEnabled by remember { mutableStateOf(false) }
                SettingsSwitchItem(
                    icon = Icons.Default.Tune,
                    title = "Crossfade",
                    subtitle = "Fade between songs",
                    checked = crossfadeEnabled,
                    onCheckedChange = { crossfadeEnabled = it }
                )
            }

            // Library Section
            item {
                SettingsSectionHeader("Library")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = "Music Folders",
                    subtitle = "Manage scanned folders",
                    onClick = { /* TODO: Open folder picker */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Rescan Library",
                    subtitle = "Search for new music",
                    onClick = { /* TODO: Trigger rescan */ }
                )
            }

            // Plugins Section
            item {
                SettingsSectionHeader("Plugins")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Extension,
                    title = "Plugin Sources",
                    subtitle = "Manage plugin providers",
                    onClick = { /* TODO: Open plugin sources */ }
                )
            }

            // About Section
            item {
                SettingsSectionHeader("About")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { /* TODO: Show version info */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Licenses",
                    subtitle = "Open source licenses",
                    onClick = { /* TODO: Show licenses */ }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

