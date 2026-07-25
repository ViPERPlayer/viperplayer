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
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SEEK_INCREMENT_MAX_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MIN_SECONDS
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import java.util.Locale
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
                CrossfadeSliderItem(
                    seconds = uiState.crossfadeDurationSeconds,
                    icon = Icons.Default.GraphicEq,
                    onSecondsChanged = viewModel::setCrossfadeDurationSeconds
                )
            }
            item {
                SeekIncrementSliderItem(
                    seconds = uiState.seekIncrementSeconds,
                    icon = Icons.Default.Forward10,
                    onSecondsChanged = viewModel::setSeekIncrementSeconds
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_skip_on_error),
                    description = stringResource(R.string.player_skip_on_error_desc),
                    icon = Icons.Default.ErrorOutline,
                    checked = uiState.skipOnError,
                    onCheckedChange = viewModel::setSkipOnError
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_prevent_duplicate_queue),
                    description = stringResource(R.string.player_prevent_duplicate_queue_desc),
                    icon = Icons.AutoMirrored.Default.PlaylistAddCheck,
                    checked = uiState.preventDuplicateQueue,
                    onCheckedChange = viewModel::setPreventDuplicateQueue
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_stop_on_task_removed),
                    description = stringResource(R.string.player_stop_on_task_removed_desc),
                    icon = Icons.Default.Stop,
                    checked = uiState.stopOnTaskRemoved,
                    onCheckedChange = viewModel::setStopOnTaskRemoved
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_pause_when_muted),
                    description = stringResource(R.string.player_pause_when_muted_desc),
                    icon = Icons.AutoMirrored.Default.VolumeOff,
                    checked = uiState.pauseWhenMuted,
                    onCheckedChange = viewModel::setPauseWhenMuted
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_resume_on_bluetooth),
                    description = stringResource(R.string.player_resume_on_bluetooth_desc),
                    icon = Icons.Default.Bluetooth,
                    checked = uiState.resumeOnBluetooth,
                    onCheckedChange = viewModel::setResumeOnBluetooth
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsCategory(stringResource(R.string.player_category_privacy))
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_keep_screen_on),
                    description = stringResource(R.string.player_keep_screen_on_desc),
                    icon = Icons.Default.ScreenLockPortrait,
                    checked = uiState.keepScreenOnPlayer,
                    onCheckedChange = viewModel::setKeepScreenOnPlayer
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.player_block_screenshots),
                    description = stringResource(R.string.player_block_screenshots_desc),
                    icon = Icons.Default.Screenshot,
                    checked = uiState.blockScreenshots,
                    onCheckedChange = viewModel::setBlockScreenshots
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

                            ReplayGainControls(
                                uiState = uiState,
                                onPreampChange = viewModel::setReplayGainPreampDb,
                                onUntaggedPreampChange = viewModel::setReplayGainUntaggedPreampDb,
                                onModeChange = viewModel::setReplayGainMode,
                                onDrcChange = viewModel::setReplayGainDrcEnabled,
                                onPostAmpChange = viewModel::setReplayGainPostAmpDb
                            )
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

/**
 * The expandable ReplayGain controls: tagged preamp, gain-mode selector (track/album/smart),
 * untagged preamp, DRC clip-guard toggle, and post-amp. Stateless — renders [uiState] and forwards
 * events — so it can be exercised directly in a Compose UI test.
 */
@Composable
internal fun ReplayGainControls(
    uiState: PlayerSettingsUiState,
    onPreampChange: (Float) -> Unit,
    onUntaggedPreampChange: (Float) -> Unit,
    onModeChange: (ReplayGainMode) -> Unit,
    onDrcChange: (Boolean) -> Unit,
    onPostAmpChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        DbSliderRow(
            label = stringResource(R.string.player_replaygain_preamp),
            valueDb = uiState.replayGainPreampDb,
            valueRange = -12f..6f,
            steps = 179,
            onValueChange = onPreampChange,
            onReset = { onPreampChange(0f) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.player_replaygain_mode),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = gainModeDescription(uiState.replayGainMode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        GainModeSelector(
            selected = uiState.replayGainMode,
            onSelected = onModeChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.player_replaygain_untagged_preamp_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DbSliderRow(
            label = stringResource(R.string.player_replaygain_untagged_preamp),
            valueDb = uiState.replayGainUntaggedPreampDb,
            valueRange = -12f..6f,
            steps = 179,
            onValueChange = onUntaggedPreampChange,
            onReset = { onUntaggedPreampChange(0f) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_replaygain_drc),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.player_replaygain_drc_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = uiState.replayGainDrcEnabled,
                onCheckedChange = onDrcChange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.player_replaygain_post_amp_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DbSliderRow(
            label = stringResource(R.string.player_replaygain_post_amp),
            valueDb = uiState.replayGainPostAmpDb,
            valueRange = -12f..12f,
            steps = 239,
            onValueChange = onPostAmpChange,
            onReset = { onPostAmpChange(0f) }
        )
    }
}

@Composable
private fun GainModeSelector(
    selected: ReplayGainMode,
    onSelected: (ReplayGainMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ReplayGainMode.TRACK to stringResource(R.string.player_replaygain_mode_track),
        ReplayGainMode.ALBUM to stringResource(R.string.player_replaygain_mode_album),
        ReplayGainMode.SMART to stringResource(R.string.player_replaygain_mode_smart)
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun DbSliderRow(
    label: String,
    valueDb: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", valueDb),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "dB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onReset,
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
            value = valueDb,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun gainModeDescription(mode: ReplayGainMode): String = when (mode) {
    ReplayGainMode.TRACK -> stringResource(R.string.player_replaygain_mode_track_desc)
    ReplayGainMode.ALBUM -> stringResource(R.string.player_replaygain_mode_album_desc)
    ReplayGainMode.SMART -> stringResource(R.string.player_replaygain_mode_smart_desc)
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
private fun CrossfadeSliderItem(
    seconds: Int,
    icon: ImageVector,
    onSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val valueText = if (seconds <= 0) {
        stringResource(R.string.player_crossfade_off)
    } else {
        stringResource(R.string.player_crossfade_seconds, seconds)
    }

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
                        text = stringResource(R.string.player_crossfade),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.player_crossfade_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
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
                    value = seconds.toFloat(),
                    onValueChange = { value -> onSecondsChanged(value.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11, // 12 steps for 13 integer values (0..12)
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
    }
}

@Composable
private fun SeekIncrementSliderItem(
    seconds: Int,
    icon: ImageVector,
    onSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = seconds.coerceIn(SEEK_INCREMENT_MIN_SECONDS, SEEK_INCREMENT_MAX_SECONDS)
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
                        text = stringResource(R.string.player_seek_increment),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.player_seek_increment_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.player_seek_increment_seconds, clamped),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
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
                    value = clamped.toFloat(),
                    onValueChange = { value ->
                        onSecondsChanged(
                            value.roundToInt()
                                .coerceIn(SEEK_INCREMENT_MIN_SECONDS, SEEK_INCREMENT_MAX_SECONDS)
                        )
                    },
                    // 5..60 in 5s steps: 11 discrete stops (5,10,...,60) = 12 values, 11 steps.
                    valueRange = SEEK_INCREMENT_MIN_SECONDS.toFloat()..SEEK_INCREMENT_MAX_SECONDS.toFloat(),
                    steps = ((SEEK_INCREMENT_MAX_SECONDS - SEEK_INCREMENT_MIN_SECONDS) / 5) - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
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

@Composable
private fun getAudioQualityTitle(quality: AudioQuality): String {
    return when (quality) {
        AudioQuality.LOW -> stringResource(R.string.player_audio_quality_low)
        AudioQuality.MEDIUM -> stringResource(R.string.player_audio_quality_medium)
        AudioQuality.HIGH -> stringResource(R.string.player_audio_quality_high)
        AudioQuality.LOSSLESS -> stringResource(R.string.player_audio_quality_lossless)
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

@Composable
private fun getHistoryDurationDescription(duration: HistoryDuration): String {
    return when (duration) {
        HistoryDuration.DAYS_7 -> stringResource(R.string.player_history_duration_7_days)
        HistoryDuration.DAYS_30 -> stringResource(R.string.player_history_duration_30_days)
        HistoryDuration.DAYS_90 -> stringResource(R.string.player_history_duration_90_days)
        HistoryDuration.DAYS_180 -> stringResource(R.string.player_history_duration_180_days)
        HistoryDuration.DAYS_365 -> stringResource(R.string.player_history_duration_365_days)
        HistoryDuration.FOREVER -> stringResource(R.string.player_history_duration_forever)
    }
}
