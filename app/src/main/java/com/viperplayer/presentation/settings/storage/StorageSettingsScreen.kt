package com.viperplayer.presentation.settings.storage

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.theme.Spacing

@Composable
fun StorageSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StorageSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showClearSongCacheDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val backupSuccessMsg = stringResource(R.string.storage_backup_success)
    val backupFailureMsg = stringResource(R.string.storage_backup_failure)
    val restoreSuccessMsg = stringResource(R.string.storage_restore_success)
    val restoreFailureMsg = stringResource(R.string.storage_restore_failure)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSizes()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                BackupEvent.ExportSuccess -> backupSuccessMsg
                BackupEvent.ExportFailure -> backupFailureMsg
                BackupEvent.RestoreSuccess -> restoreSuccessMsg
                BackupEvent.RestoreFailure -> restoreFailureMsg
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title)) },
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
                SettingsCategory(stringResource(R.string.storage_category_downloads))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_downloaded_songs),
                    description = formatBytes(uiState.downloadedSongsSize),
                    icon = Icons.Default.Download,
                    onClick = onNavigateToDownloads
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_clear_all_downloads),
                    description = stringResource(R.string.storage_clear_all_downloads_desc),
                    icon = Icons.Default.Delete,
                    onClick = { showClearDownloadsDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            item {
                SettingsCategory(stringResource(R.string.storage_category_song_cache))
            }
            item {
                CacheSizeSliderItem(
                    title = stringResource(R.string.storage_max_song_cache_size),
                    currentSize = uiState.maxSongCacheSize,
                    minSize = 50L * 1024 * 1024, // 50 MB
                    maxSize = 5000L * 1024 * 1024, // 5 GB
                    icon = Icons.Default.MusicNote,
                    onSizeChanged = viewModel::setMaxSongCacheSize
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_clear_song_cache),
                    description = formatBytes(uiState.songCacheSize),
                    icon = Icons.Default.Delete,
                    onClick = { showClearSongCacheDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            item {
                SettingsCategory(stringResource(R.string.storage_category_image_cache))
            }
            item {
                CacheSizeSliderItem(
                    title = stringResource(R.string.storage_max_image_cache_size),
                    currentSize = uiState.maxImageCacheSize,
                    minSize = 50L * 1024 * 1024, // 50 MB
                    maxSize = 2000L * 1024 * 1024, // 2 GB
                    icon = Icons.Default.Image,
                    onSizeChanged = viewModel::setMaxImageCacheSize
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_clear_image_cache),
                    description = formatBytes(uiState.imageCacheSize),
                    icon = Icons.Default.Delete,
                    onClick = { showClearImageCacheDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            item {
                SettingsCategory(stringResource(R.string.storage_category_backup))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_backup),
                    description = stringResource(R.string.storage_backup_desc),
                    icon = Icons.Default.Save,
                    onClick = { exportLauncher.launch("viper-player-backup.json") }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_restore),
                    description = stringResource(R.string.storage_restore_desc),
                    icon = Icons.Default.Restore,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
            }
        }
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource(R.string.storage_clear_all_downloads)) },
            text = { Text(stringResource(R.string.storage_clear_downloads_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDownloads()
                        showClearDownloadsDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearSongCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearSongCacheDialog = false },
            title = { Text(stringResource(R.string.storage_clear_song_cache)) },
            text = { Text(stringResource(R.string.storage_clear_song_cache_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSongCache()
                        showClearSongCacheDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSongCacheDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheDialog = false },
            title = { Text(stringResource(R.string.storage_clear_image_cache)) },
            text = { Text(stringResource(R.string.storage_clear_image_cache_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearImageCache()
                        showClearImageCacheDialog = false
                    },
                    enabled = !uiState.isClearing
                ) {
                    if (uiState.isClearing) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
    icon: ImageVector,
    onSizeChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert bytes to slider value (0.0 to 1.0)
    val sliderValue = ((currentSize - minSize).toFloat() / (maxSize - minSize).toFloat())
        .coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
                modifier = Modifier.padding(horizontal = Spacing.lg),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
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
