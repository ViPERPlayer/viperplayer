package com.viperplayer.presentation.settings.player

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import kotlin.math.roundToInt

@Composable
fun PlayerSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAudioQualityDialog by remember { mutableStateOf(false) }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_player_audio)) },
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
                SettingsCategory(stringResource(R.string.player_audio_quality))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.player_audio_quality),
                    description = getAudioQualityDescription(uiState.audioQuality),
                    icon = Icons.Default.GraphicEq,
                    onClick = { showAudioQualityDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsCategory(stringResource(R.string.player_category_playback))
            }
            item {
                HistoryDurationSliderItem(
                    currentDuration = uiState.historyDuration,
                    icon = Icons.Default.History,
                    onDurationChanged = viewModel::setHistoryDuration
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_skip_silence),
                    description = stringResource(R.string.player_skip_silence_desc),
                    icon = Icons.Default.SkipNext,
                    checked = uiState.skipSilence,
                    onCheckedChange = viewModel::setSkipSilence
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_bypass_dsp),
                    description = stringResource(R.string.player_bypass_dsp_desc),
                    icon = Icons.Default.HighQuality,
                    checked = uiState.dspBypass,
                    onCheckedChange = viewModel::setDspBypass
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_auto_load_more),
                    description = stringResource(R.string.player_auto_load_more_desc),
                    icon = Icons.Default.Add,
                    checked = uiState.autoLoadMore,
                    onCheckedChange = viewModel::setAutoLoadMore
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsCategory(stringResource(R.string.player_category_normalization))
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
                    Column {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.player_replaygain),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.player_replaygain_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.replayGainEnabled,
                                    onCheckedChange = viewModel::setReplayGainEnabled
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )

                        if (uiState.replayGainEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_replaygain_preamp),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(
                                                "%.1f",
                                                uiState.replayGainPreampDb
                                            ),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "dB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = { viewModel.setReplayGainPreampDb(0f) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.value_slider_reset_to_default),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = uiState.replayGainPreampDb,
                                    onValueChange = { viewModel.setReplayGainPreampDb(it) },
                                    valueRange = -12f..6f,
                                    steps = 179
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.player_replaygain_album_mode),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = stringResource(R.string.player_replaygain_album_mode_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.replayGainAlbumMode,
                                        onCheckedChange = viewModel::setReplayGainAlbumMode
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioQualityDialog) {
        AudioQualityDialog(
            currentQuality = uiState.audioQuality,
            onQualitySelected = { quality ->
                viewModel.setAudioQuality(quality)
                showAudioQualityDialog = false
            },
            onDismiss = { showAudioQualityDialog = false }
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

@Composable
private fun SettingsSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
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
private fun HistoryDurationSliderItem(
    currentDuration: HistoryDuration,
    icon: ImageVector,
    onDurationChanged: (HistoryDuration) -> Unit,
    modifier: Modifier = Modifier
) {
    // Map HistoryDuration enum to slider index (0-5)
    val durations = HistoryDuration.values()
    val currentIndex = durations.indexOf(currentDuration).coerceIn(0, durations.size - 1).toFloat()
    val maxIndex = (durations.size - 1).toFloat()

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
                        text = stringResource(R.string.player_history_duration),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = getHistoryDurationDescription(currentDuration),
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
                    value = currentIndex,
                    onValueChange = { value ->
                        // Round to nearest integer index
                        val roundedIndex = value.roundToInt().coerceIn(0, durations.size - 1)
                        onDurationChanged(durations[roundedIndex])
                    },
                    valueRange = 0f..maxIndex,
                    steps = durations.size - 2, // 5 steps for 6 values (0, 1, 2, 3, 4, 5)
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
                        text = stringResource(R.string.history_7_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.history_forever),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioQualityDialog(
    currentQuality: AudioQuality,
    onQualitySelected: (AudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_audio_quality)) },
        text = {
            Column {
                AudioQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == currentQuality,
                            onClick = { onQualitySelected(quality) }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = getAudioQualityTitle(quality),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getAudioQualityDescription(quality),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

private fun getAudioQualityTitle(quality: AudioQuality): String {
    return when (quality) {
        AudioQuality.LOW -> "Low"
        AudioQuality.MEDIUM -> "Medium"
        AudioQuality.HIGH -> "High"
        AudioQuality.LOSSLESS -> "Lossless"
    }
}

private fun getAudioQualityDescription(quality: AudioQuality): String {
    return when (quality) {
        AudioQuality.LOW -> "128 kbps"
        AudioQuality.MEDIUM -> "256 kbps"
        AudioQuality.HIGH -> "320 kbps"
        AudioQuality.LOSSLESS -> "FLAC/ALAC"
    }
}

private fun getHistoryDurationDescription(duration: HistoryDuration): String {
    return when (duration) {
        HistoryDuration.DAYS_7 -> "7 days"
        HistoryDuration.DAYS_30 -> "30 days"
        HistoryDuration.DAYS_90 -> "90 days"
        HistoryDuration.DAYS_180 -> "6 months"
        HistoryDuration.DAYS_365 -> "1 year"
        HistoryDuration.FOREVER -> "Forever"
    }
}
