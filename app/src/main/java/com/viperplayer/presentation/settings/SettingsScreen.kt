package com.viperplayer.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.common.components.SurfaceCardRow
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.theme.Spacing

/**
 * App-preferences settings (slimmed). Account, plugins, listening stats, Last.fm and social moved to
 * the You hub — their routes still exist and are reached from there. Rendered as grouped
 * [SurfaceCard]s.
 */
@Composable
fun SettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToContent: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpdater: () -> Unit,
    onNavigateToAlarms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
            contentPadding = PaddingValues(horizontal = Spacing.lg) + rootPadding.bottom(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "preferences-card") {
                SurfaceCard(modifier = Modifier.padding(top = Spacing.sm)) {
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = stringResource(R.string.settings_appearance_desc),
                        leadingIcon = Icons.Rounded.Palette,
                        onClick = onNavigateToAppearance,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_lyrics),
                        subtitle = stringResource(R.string.settings_lyrics_desc),
                        leadingIcon = Icons.Rounded.Lyrics,
                        onClick = onNavigateToLyrics,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_player_audio),
                        subtitle = stringResource(R.string.settings_player_audio_desc),
                        leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                        onClick = onNavigateToPlayer,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_content),
                        subtitle = stringResource(R.string.settings_content_desc),
                        leadingIcon = Icons.Rounded.MusicNote,
                        onClick = onNavigateToContent,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_alarms),
                        subtitle = stringResource(R.string.settings_alarms_desc),
                        leadingIcon = Icons.Rounded.Alarm,
                        onClick = onNavigateToAlarms,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.storage_title),
                        subtitle = stringResource(R.string.settings_storage_desc),
                        leadingIcon = Icons.Rounded.Storage,
                        onClick = onNavigateToStorage,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_scan_local),
                        subtitle = stringResource(R.string.settings_scan_local_desc),
                        leadingIcon = Icons.Rounded.Refresh,
                        onClick = viewModel::scanLocalFiles,
                    )
                }
            }

            item(key = "system-card") {
                SurfaceCard(modifier = Modifier.padding(top = Spacing.xs)) {
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_updater),
                        subtitle = stringResource(R.string.settings_updater_desc),
                        leadingIcon = Icons.Rounded.SystemUpdate,
                        onClick = onNavigateToUpdater,
                    )
                    InsetDivider()
                    SurfaceCardRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_desc),
                        leadingIcon = Icons.Rounded.Info,
                        onClick = onNavigateToAbout,
                    )
                }
            }

            item(key = "moved-hint") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_moved_to_you_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
