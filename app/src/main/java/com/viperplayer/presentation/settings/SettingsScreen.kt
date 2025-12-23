package com.viperplayer.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen demonstrating how to use the layout system.
 *
 * Note: This screen hides the bottom navigation bar but still shows the mini player
 * if there's playing content. The bottomPadding is used to ensure content scrolls
 * properly without being hidden behind the mini player.
 */
@Composable
fun SettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(rootPadding)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            Box(modifier = Modifier.size(48.dp)) // Spacer for alignment
        }

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
//            contentPadding = layoutState.bottomPadding // Apply layout-aware padding here
        ) {
            item {
                SettingsCategory("Playback")
            }
            item {
                SettingsItem(
                    title = "Quality",
                    description = "Select audio quality",
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    title = "Volume Normalization",
                    description = "Normalize volume across songs",
                    onClick = { }
                )
            }

            item {
                SettingsCategory("Library")
            }
            item {
                SettingsItem(
                    title = "Auto Cache",
                    description = "Automatically cache songs",
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    title = "Sync Playlists",
                    description = "Sync with cloud",
                    onClick = { }
                )
            }

            item {
                SettingsCategory("Display")
            }
            item {
                SettingsItem(
                    title = "Theme",
                    description = "Light / Dark / System",
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    title = "Show Lyrics",
                    description = "Display song lyrics",
                    onClick = { }
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

