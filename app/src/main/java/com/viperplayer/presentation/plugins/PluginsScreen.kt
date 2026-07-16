package com.viperplayer.presentation.plugins

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.ktx.with

@Composable
fun PluginsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuPluginId by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf<PluginInfo?>(null) }

    // Surface library-sync outcomes as a Toast.
    LaunchedEffect(Unit) {
        viewModel.syncEvents.collect { event ->
            val message = when (event) {
                is LibrarySyncEvent.Success ->
                    if (event.result.isEmpty) {
                        context.getString(R.string.plugins_sync_empty)
                    } else {
                        context.getString(
                            R.string.plugins_sync_result,
                            event.result.songs,
                            event.result.albums,
                            event.result.artists,
                            event.result.playlists,
                        )
                    }
                is LibrarySyncEvent.Failure -> context.getString(R.string.plugins_sync_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Pending user actions per plugin (permission, login, verification...).
    val actionsViewModel: PluginActionsViewModel = hiltViewModel()
    val pendingActions by actionsViewModel.pendingActions.collectAsStateWithLifecycle()
    val pendingByPlugin = remember(pendingActions) { pendingActions.groupBy { it.pluginId } }
    val resolveAction = rememberPluginActionResolver { actionsViewModel.refresh() }
    LifecycleResumeEffect(Unit) {
        actionsViewModel.refresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_plugins)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(rootPadding.with(bottom = 0.dp))
        ) {

            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (uiState.discoveredPlugins.isEmpty() && !uiState.isRefreshing) {
                Box(
                    modifier = Modifier
                        .padding(rootPadding.bottom())
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.plugins_none_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.plugins_none_found_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = "https://github.com/viperplayer/plugins".toUri()
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.plugins_download))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = rootPadding.bottom() + PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.discoveredPlugins) { plugin ->
                        val isEnabled = viewModel.isEnabled(plugin.id)
                        val isConnected = viewModel.isConnected(plugin.id)
                        val connectedPlugin = viewModel.getConnectedPlugin(plugin.id)
                        val isToggling = uiState.togglingPluginId == plugin.id

                        // Discovery only knows the package label + APK version; the connected
                        // plugin's manifest carries the authoritative name, protocol version,
                        // author and settings activity. Prefer it when connected.
                        val displayInfo = connectedPlugin?.info?.let { connected ->
                            plugin.copy(
                                name = connected.name,
                                apiVersion = connected.apiVersion,
                                author = connected.author ?: plugin.author,
                                description = connected.description ?: plugin.description,
                                settingsActivity = connected.settingsActivity,
                            )
                        } ?: plugin

                        PluginCard(
                            plugin = displayInfo,
                            isEnabled = isEnabled,
                            isConnected = isConnected,
                            isToggling = isToggling,
                            connectedPlugin = connectedPlugin,
                            pendingAction = pendingByPlugin[plugin.id]?.firstOrNull(),
                            onResolveAction = resolveAction,
                            showMenu = menuPluginId == plugin.id,
                            canSyncLibrary = viewModel.hasLibrary(plugin.id),
                            isSyncing = plugin.id in syncing,
                            canPushSync = viewModel.hasLibraryWrite(plugin.id),
                            pushSyncEnabled = plugin.id in uiState.pushSyncEnabled,
                            onToggle = { viewModel.togglePlugin(plugin.id) },
                            onLongPress = { menuPluginId = plugin.id },
                            onDismissMenu = { menuPluginId = null },
                            onSyncLibrary = {
                                menuPluginId = null
                                viewModel.syncLibrary(plugin.id)
                            },
                            onTogglePushSync = { enabled ->
                                viewModel.setPushSyncEnabled(plugin.id, enabled)
                            },
                            onShowInfo = {
                                showInfoDialog = displayInfo
                                menuPluginId = null
                            },
                            onUninstall = {
                                menuPluginId = null
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = "package:${plugin.id}".toUri()
                                    }
                                context.startActivity(intent)
                            },
                            onOpenSettings = {
                                val settingsActivity = displayInfo.settingsActivity
                                if (settingsActivity != null) {
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            displayInfo.id,
                                            if (settingsActivity.startsWith(".")) {
                                                displayInfo.id + settingsActivity
                                            } else {
                                                settingsActivity
                                            }
                                        )
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Plugin info dialog
    showInfoDialog?.let { plugin ->
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(text = plugin.name) },
            text = {
                Column {
                    if (!plugin.author.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.plugins_author, plugin.author.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.plugins_version, plugin.version),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    plugin.apiVersion?.let { apiVersion ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.plugins_api_version, apiVersion),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!plugin.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = plugin.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.plugins_package, plugin.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PluginCard(
    plugin: PluginInfo,
    isEnabled: Boolean,
    isConnected: Boolean,
    isToggling: Boolean,
    connectedPlugin: Plugin?,
    pendingAction: PluginPendingAction? = null,
    onResolveAction: (PluginPendingAction) -> Unit = {},
    showMenu: Boolean,
    canSyncLibrary: Boolean = false,
    isSyncing: Boolean = false,
    canPushSync: Boolean = false,
    pushSyncEnabled: Boolean = false,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onSyncLibrary: () -> Unit = {},
    onTogglePushSync: (Boolean) -> Unit = {},
    onShowInfo: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plugin icon placeholder
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (!plugin.author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.plugins_by, plugin.author.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (!plugin.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    plugin.apiVersion?.let { apiVersion ->
                        Text(
                            text = stringResource(R.string.plugins_api_short, apiVersion),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isConnected) Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (isConnected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.status_connected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Pending user action (permission, login, verification...) — tap to resolve.
                pendingAction?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    PluginActionChip(
                        action = action,
                        onClick = { onResolveAction(action) },
                    )
                }

                // Show capabilities if connected
                connectedPlugin?.let { connected ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (connected.capabilities.canSearch) {
                            CapabilityChip(stringResource(R.string.plugins_capability_search))
                        }
                        if (connected.capabilities.hasLibrary) {
                            CapabilityChip(stringResource(R.string.plugins_capability_library))
                        }
                        if (connected.capabilities.hasPlaylists) {
                            CapabilityChip(stringResource(R.string.plugins_capability_playlists))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Library sync in progress for this plugin.
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Show settings button if plugin has settings activity
            if (plugin.settingsActivity != null) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.plugins_settings_cd),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box {
                if (isToggling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }

                // Dropdown menu for long press
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onDismissMenu
                ) {
                    if (canSyncLibrary) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plugins_sync_library)) },
                            onClick = onSyncLibrary,
                            enabled = !isSyncing,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    if (canPushSync) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plugins_push_sync)) },
                            // The row owns the toggle; the Switch is a non-interactive state indicator
                            // (onCheckedChange = null) so a tap can't flip the preference twice.
                            onClick = { onTogglePushSync(!pushSyncEnabled) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = pushSyncEnabled,
                                    onCheckedChange = null,
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plugins_show_info)) },
                        onClick = onShowInfo,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plugins_uninstall)) },
                        onClick = onUninstall,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityChip(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

