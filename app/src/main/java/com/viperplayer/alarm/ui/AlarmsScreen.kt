package com.viperplayer.alarm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.presentation.common.ViperScaffold

/**
 * Alarm list + add/edit entry point. Stateful wrapper: it collects [AlarmsViewModel] state and hosts
 * the editor sheet, delegating all mutations back to the ViewModel. Rendering lives in the stateless
 * [AlarmsScreenContent].
 */
@Composable
fun AlarmsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<Alarm?>(null) }

    // Re-check the exact-alarm permission each time the screen resumes (e.g. returning from the
    // system settings the banner links to).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshExactPermission()
        onPauseOrDispose { }
    }

    AlarmsScreenContent(
        state = uiState,
        rootPadding = rootPadding,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onAddAlarm = { editing = defaultNewAlarm() },
        onEditAlarm = { editing = it },
        onToggleEnabled = viewModel::setEnabled,
        onOpenExactSettings = {
            viewModel.refreshExactPermission()
            openExactAlarmSettings(context)
        },
    )

    editing?.let { alarm ->
        AlarmEditorSheet(
            initial = alarm,
            playlists = uiState.playlists,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveAlarm(it)
                editing = null
            },
            onDelete = if (alarm.id != 0L) {
                {
                    viewModel.deleteAlarm(alarm)
                    editing = null
                }
            } else null,
        )
    }
}

/**
 * Stateless alarm list. Renders the exact-alarm banner, the alarms, and an add FAB; forwards every
 * interaction as an event. Testable without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreenContent(
    state: AlarmsUiState,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onToggleEnabled: (Alarm, Boolean) -> Unit,
    onOpenExactSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarms_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.alarm_add),
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth()
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (!state.canScheduleExact) {
                item {
                    ExactAlarmBanner(onClick = onOpenExactSettings)
                }
            }

            if (state.alarms.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.alarms_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            } else {
                items(state.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onClick = { onEditAlarm(alarm) },
                        onToggle = { enabled -> onToggleEnabled(alarm, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.alarm_exact_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.alarm_exact_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.alarm_exact_permission_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toggleDescription = stringResource(R.string.alarm_enable_cd)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = AlarmFormatting.formatTime(alarm),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    text = AlarmFormatting.repeatSummary(context, alarm.daysOfWeek),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = toggleDescription },
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
    }
}

/** A sensible fresh alarm: 8:00, one-time, shuffle all, full volume, 30s fade. */
private fun defaultNewAlarm(): Alarm = Alarm(hour = 8, minute = 0)

/** Opens the system exact-alarm settings on Android 12+, or the app details as a fallback. */
private fun openExactAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(intent) }
}
