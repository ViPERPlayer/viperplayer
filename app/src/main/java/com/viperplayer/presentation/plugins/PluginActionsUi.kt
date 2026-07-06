package com.viperplayer.presentation.plugins

import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.domain.model.PluginActionType
import com.viperplayer.domain.model.PluginPendingAction
import timber.log.Timber

/**
 * Returns a callback that resolves a [PluginPendingAction]:
 * 1. embedded plugin + permission → request the permission directly in the host;
 * 2. action activity → launch it in the plugin's package;
 * 3. otherwise → open the plugin's settings activity.
 *
 * [onResolutionAttempted] fires when a same-process resolution finishes (permission dialog result);
 * activity hops are picked up by the caller's on-resume refresh instead.
 */
@Composable
fun rememberPluginActionResolver(onResolutionAttempted: () -> Unit): (PluginPendingAction) -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResolutionAttempted() }

    return remember(context) {
        resolver@{ action ->
            if (action.permission != null && action.pluginId == context.packageName) {
                permissionLauncher.launch(action.permission)
                return@resolver
            }
            val target = action.activity ?: action.settingsActivity
            if (target == null) {
                Timber.w("No resolution target for plugin action ${action.key}")
                return@resolver
            }
            runCatching {
                context.startActivity(Intent().apply { component = ComponentName(action.pluginId, target) })
            }.onFailure { Timber.e(it, "Failed to launch $target for ${action.pluginId}") }
        }
    }
}

/** Icon for an action type, used consistently across every surface. */
fun pluginActionIcon(type: PluginActionType): ImageVector = when (type) {
    PluginActionType.PERMISSION -> Icons.Rounded.Lock
    PluginActionType.LOGIN -> Icons.Rounded.AccountCircle
    PluginActionType.VERIFICATION -> Icons.Rounded.VerifiedUser
    PluginActionType.SETUP -> Icons.Rounded.Build
    PluginActionType.UNKNOWN -> Icons.Rounded.Extension
}

/** Bottom sheet listing every pending action with a Fix button. Opened from the Home bell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginActionsSheet(
    actions: List<PluginPendingAction>,
    onResolve: (PluginPendingAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.plugin_actions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (actions.isEmpty()) {
                Text(
                    text = stringResource(R.string.plugin_actions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
            actions.forEach { action ->
                PluginActionRow(action = action, onResolve = { onResolve(action) })
            }
        }
        Spacer(modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun PluginActionRow(
    action: PluginPendingAction,
    onResolve: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = pluginActionIcon(action.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = action.description?.let { "${action.pluginName} • $it" } ?: action.pluginName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onResolve) {
                Text(stringResource(R.string.plugin_actions_fix))
            }
        }
    }
}

/** Dismissable Home banner shown while any plugin has a pending action. */
@Composable
fun PluginActionsBanner(
    actions: List<PluginPendingAction>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = actions.firstOrNull() ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = pluginActionIcon(first.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (actions.size == 1) {
                    stringResource(R.string.plugin_actions_banner_single, first.pluginName, first.title)
                } else {
                    stringResource(R.string.plugin_actions_banner_multiple, actions.map { it.pluginId }.distinct().size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.plugin_actions_dismiss),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Small "Action needed" chip for the Plugins screen's plugin card. */
@Composable
fun PluginActionChip(
    action: PluginPendingAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = pluginActionIcon(action.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${stringResource(R.string.plugin_action_needed)} • ${action.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
