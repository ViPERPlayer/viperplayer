package com.viperplayer.presentation.plugins

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.domain.plugin.PluginUpdate
import com.viperplayer.domain.plugin.PluginUpdateProgress
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.OutlinedPill
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/** Alpha applied to a disabled plugin card so it reads as "off" (mockup 5f). */
private const val DisabledCardAlpha = 0.72f

@OptIn(ExperimentalMaterial3Api::class)
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
    var showChangelog by remember { mutableStateOf<PluginUpdate?>(null) }
    val updatesBadgeDescription = stringResource(R.string.plugins_updates_badge_cd)

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

    // Surface update download/install outcomes as a Toast.
    LaunchedEffect(Unit) {
        viewModel.updateEvents.collect { event ->
            val message = when (event) {
                is PluginUpdateProgress.Succeeded ->
                    context.getString(R.string.plugins_update_succeeded)
                is PluginUpdateProgress.Failed ->
                    context.getString(R.string.plugins_update_failed, event.message)
                else -> return@collect
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_plugins)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !uiState.isCheckingUpdates,
                    ) {
                        if (uiState.isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            // A small error-tinted dot signals the global "updates available"
                            // indicator, overlaid on the top-right of the system-update icon.
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = stringResource(R.string.plugins_check_updates),
                                )
                                if (uiState.hasUpdates) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-3).dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                            .semantics {
                                                contentDescription = updatesBadgeDescription
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = rootPadding.calculateTopPadding())
        ) {

            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (uiState.discoveredPlugins.isEmpty() && !uiState.isRefreshing) {
                PluginsEmptyState(
                    rootPadding = rootPadding,
                    onDownload = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = "https://github.com/viperplayer/plugins".toUri()
                        }
                        context.startActivity(intent)
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = rootPadding.bottom() + PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            update = uiState.availableUpdates[plugin.id],
                            updateProgress = uiState.updateProgress[plugin.id],
                            onInstallUpdate = { viewModel.installUpdate(plugin.id) },
                            onDismissUpdate = { viewModel.dismissUpdate(plugin.id) },
                            onShowChangelog = { showChangelog = uiState.availableUpdates[plugin.id] },
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

    // Changelog dialog for an available update.
    showChangelog?.let { update ->
        AlertDialog(
            onDismissRequest = { showChangelog = null },
            title = { Text(text = stringResource(R.string.plugins_update_changelog)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.plugins_update_versions,
                            update.installedVersionName,
                            update.availableVersionName,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!update.changelog.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = update.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.installUpdate(update.pluginId)
                    showChangelog = null
                }) {
                    Text(stringResource(R.string.plugins_update_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangelog = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

/** Centered empty state shown when no plugins are discovered. */
@Composable
private fun PluginsEmptyState(
    rootPadding: PaddingValues,
    onDownload: () -> Unit,
) {
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
                imageVector = Icons.Rounded.Extension,
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
            FilledPill(
                text = stringResource(R.string.plugins_download),
                onClick = onDownload,
                leadingIcon = Icons.Rounded.Download,
            )
        }
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
    update: PluginUpdate? = null,
    updateProgress: PluginUpdateProgress? = null,
    onInstallUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onShowChangelog: () -> Unit = {},
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
    SurfaceCard(
        modifier = modifier.combinedClickable(
            onClick = { },
            onLongClick = onLongPress,
        ),
    ) {
        // Whole-card dimming when the plugin is turned off (mockup 5f).
        val contentAlpha = if (isEnabled) 1f else DisabledCardAlpha
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIconTile(id = plugin.id, name = plugin.name, connected = isConnected)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plugin.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isConnected) {
                        ConnectedPill()
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pluginMetaLine(plugin = plugin, isEnabled = isEnabled),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Pending user action (permission, login, verification...) — tap to resolve.
                pendingAction?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    PluginActionChip(
                        action = action,
                        onClick = { onResolveAction(action) },
                    )
                }

                // Capability chips for a connected plugin.
                connectedPlugin?.let { connected ->
                    val hasAnyCapability = connected.capabilities.canSearch ||
                        connected.capabilities.hasLibrary ||
                        connected.capabilities.hasPlaylists
                    if (hasAnyCapability) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }

            // Trailing controls cluster: sync spinner, settings shortcut, and the enable switch
            // (or its toggle spinner) that also anchors the long-press dropdown menu.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Library sync in progress for this plugin.
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }

                // Settings shortcut when the plugin exposes a settings activity.
                if (plugin.settingsActivity != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.plugins_settings_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                                    imageVector = Icons.Rounded.Sync,
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
                                    imageVector = Icons.Rounded.CloudUpload,
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
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plugins_uninstall)) },
                        onClick = onUninstall,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null
                            )
                        }
                    )
                    }
                }
            }
        }
        // "Update available" banner + install/dismiss + progress, only when an update is offered.
        if (update != null) {
            PluginUpdateSection(
                update = update,
                progress = updateProgress,
                onInstall = onInstallUpdate,
                onDismiss = onDismissUpdate,
                onShowChangelog = onShowChangelog,
            )
        }
    }
}

/** Plugin id of the built-in on-device "Local" source, rendered with a device glyph (mockup 5f). */
private const val LocalPluginId = "local"

/**
 * The 48dp rounded tile leading a [PluginCard]. A connected plugin gets a diagonal two-tone
 * [Brush.linearGradient] fill (mockup 5f), keyed by well-known plugin id to distinct colorScheme
 * tone pairs where we can detect one cheaply, falling back to a tasteful role-based gradient. The
 * built-in "Local" plugin shows a device glyph; every other plugin shows its monogram initial. A
 * disabled/unrecognised tile stays a muted flat surface with the initial.
 */
@Composable
private fun PluginIconTile(id: String, name: String, connected: Boolean) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()
    val scheme = MaterialTheme.colorScheme

    // Diagonal (top-start → bottom-end) gradient tone pair for connected tiles, mapped from
    // well-known plugin ids to distinct colorScheme roles; otherwise a role-based default.
    val gradientTones: Pair<Color, Color>? =
        if (connected) {
            when {
                id == LocalPluginId ->
                    scheme.secondaryContainer to scheme.surfaceContainerHighest
                id.contains("testsource", ignoreCase = true) ->
                    scheme.primaryContainer to scheme.surfaceContainerHigh
                id.contains("thirdsource", ignoreCase = true) || id.contains("music", ignoreCase = true) ->
                    scheme.tertiaryContainer to scheme.surfaceContainerHigh
                else ->
                    scheme.primaryContainer to scheme.surfaceContainerHigh
            }
        } else {
            null
        }

    val contentColor = if (connected) scheme.onPrimaryContainer else scheme.onSurfaceVariant

    val tileModifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(14.dp))
        .let { base ->
            if (gradientTones != null) {
                base.background(
                    Brush.linearGradient(
                        colors = listOf(gradientTones.first, gradientTones.second),
                    )
                )
            } else {
                base.background(scheme.surfaceContainerHigh)
            }
        }

    Box(
        modifier = tileModifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Built-in on-device source: a representative device glyph instead of an initial.
            id == LocalPluginId -> {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Named plugin: keep the monogram initial (never fall back to the generic glyph).
            initial != null -> {
                Text(
                    text = initial.toString(),
                    color = contentColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            // Truly nameless plugin: the generic extension glyph as a last resort.
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** The all-caps "CONNECTED" pill next to a connected plugin's name (mockup 5f). */
@Composable
private fun ConnectedPill() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.plugins_status_connected),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Builds the single meta line under a plugin's name — "by Author · vX.Y · API vN", omitting parts
 * that are absent — and appends a "disabled" hint when the plugin is turned off (mockup 5f).
 */
@Composable
private fun pluginMetaLine(plugin: PluginInfo, isEnabled: Boolean): String {
    val separator = stringResource(R.string.plugins_meta_separator)
    val parts = buildList {
        if (!plugin.author.isNullOrBlank()) {
            add(stringResource(R.string.plugins_by, plugin.author.orEmpty()))
        }
        if (plugin.version.isNotBlank()) {
            add(stringResource(R.string.plugins_version_short, plugin.version))
        }
        plugin.apiVersion?.let { add(stringResource(R.string.plugins_api_short, it)) }
        if (!isEnabled) add(stringResource(R.string.plugins_meta_disabled))
    }
    return parts.joinToString(separator)
}

/**
 * The per-plugin "Update available" section shown at the bottom of a [PluginCard]. Render-only: it
 * shows the version transition, optional changelog access, an Update / Dismiss control, and — while
 * a download/install is in flight — a progress bar and status text. All logic lives in the ViewModel
 * and [com.viperplayer.data.plugin.update.PluginUpdateManager].
 */
@Composable
private fun PluginUpdateSection(
    update: PluginUpdate,
    progress: PluginUpdateProgress?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onShowChangelog: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.plugins_update_available),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.plugins_update_versions,
                            update.installedVersionName,
                            update.availableVersionName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            when (progress) {
                is PluginUpdateProgress.Downloading -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress.fraction != null) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.plugins_update_downloading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                is PluginUpdateProgress.Installing,
                is PluginUpdateProgress.AwaitingUserConfirmation -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (progress is PluginUpdateProgress.AwaitingUserConfirmation) {
                                R.string.plugins_update_confirm
                            } else {
                                R.string.plugins_update_installing
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                else -> {
                    // Idle: offer the actions.
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!update.changelog.isNullOrBlank()) {
                            // Plain primary text link ("What's new"): minimal button chrome so it
                            // reads as a link, sitting on the LEFT of the update row (mockup 5f).
                            TextButton(
                                onClick = onShowChangelog,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.plugins_update_changelog),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedPill(
                            text = stringResource(R.string.plugins_update_dismiss),
                            onClick = onDismiss,
                        )
                        FilledPill(
                            text = stringResource(R.string.plugins_update_action),
                            onClick = onInstall,
                        )
                    }
                }
            }
        }
    }
}

/** A small tonal capability chip (Search / Library / Playlists) on a connected plugin card. */
@Composable
fun CapabilityChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
