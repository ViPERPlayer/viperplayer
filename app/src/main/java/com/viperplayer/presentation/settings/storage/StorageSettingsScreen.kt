package com.viperplayer.presentation.settings.storage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom

@Composable
fun StorageSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StorageSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showClearSongCacheDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.refreshSizes()
    }
    
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
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
                SettingsCategory("Downloads")
            }
            item {
                SettingsItem(
                    title = "Downloaded Songs",
                    description = formatBytes(uiState.downloadedSongsSize),
                    icon = Icons.Default.Download,
                    onClick = { }
                )
            }
            item {
                SettingsItem(
                    title = "Clear All Downloads",
                    description = "Remove all downloaded songs",
                    icon = Icons.Default.Delete,
                    onClick = { showClearDownloadsDialog = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                SettingsCategory("Song Cache")
            }
            item {
                CacheSizeSliderItem(
                    title = "Max Song Cache Size",
                    currentSize = uiState.maxSongCacheSize,
                    minSize = 50L * 1024 * 1024, // 50 MB
                    maxSize = 5000L * 1024 * 1024, // 5 GB
                    icon = Icons.Default.MusicNote,
                    onSizeChanged = viewModel::setMaxSongCacheSize
                )
            }
            item {
                SettingsItem(
                    title = "Clear Song Cache",
                    description = formatBytes(uiState.songCacheSize),
                    icon = Icons.Default.Delete,
                    onClick = { showClearSongCacheDialog = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                SettingsCategory("Image Cache")
            }
            item {
                CacheSizeSliderItem(
                    title = "Max Image Cache Size",
                    currentSize = uiState.maxImageCacheSize,
                    minSize = 50L * 1024 * 1024, // 50 MB
                    maxSize = 2000L * 1024 * 1024, // 2 GB
                    icon = Icons.Default.Image,
                    onSizeChanged = viewModel::setMaxImageCacheSize
                )
            }
            item {
                SettingsItem(
                    title = "Clear Image Cache",
                    description = formatBytes(uiState.imageCacheSize),
                    icon = Icons.Default.Delete,
                    onClick = { showClearImageCacheDialog = true }
                )
            }
        }
    }
    
    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text("Clear All Downloads") },
            text = { Text("Are you sure you want to delete all downloaded songs? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDownloads()
                        showClearDownloadsDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Clear")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showClearSongCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearSongCacheDialog = false },
            title = { Text("Clear Song Cache") },
            text = { Text("Are you sure you want to clear the song cache? This will remove temporary audio files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSongCache()
                        showClearSongCacheDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Clear")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSongCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showClearImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheDialog = false },
            title = { Text("Clear Image Cache") },
            text = { Text("Are you sure you want to clear the image cache? This will remove cached artwork.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearImageCache()
                        showClearImageCacheDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Clear")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheDialog = false }) {
                    Text("Cancel")
                }
            }
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

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun CacheSizeSliderItem(
    title: String,
    currentSize: Long,
    minSize: Long,
    maxSize: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSizeChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert bytes to slider value (0.0 to 1.0)
    val sliderValue = ((currentSize - minSize).toFloat() / (maxSize - minSize).toFloat())
        .coerceIn(0f, 1f)
    
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
                        text = formatBytes(currentSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        val newSize = minSize + ((maxSize - minSize) * value).toLong()
                        onSizeChanged(newSize)
                    },
                    valueRange = 0f..1f,
                    steps = 99, // 100 steps for smooth adjustment
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBytes(minSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatBytes(maxSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
