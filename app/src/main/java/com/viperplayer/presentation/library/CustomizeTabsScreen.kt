package com.viperplayer.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Stateful "Customize tabs" screen. Collects the editor rows from [CustomizeTabsViewModel] and forwards
 * reorder / toggle / reset events. All rendering lives in the stateless [CustomizeTabsScreenContent].
 */
@Composable
fun CustomizeTabsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomizeTabsViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()

    CustomizeTabsScreenContent(
        tabs = tabs,
        rootPadding = rootPadding,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onMoveTab = viewModel::moveTab,
        onToggleTab = viewModel::setTabVisible,
        onReset = viewModel::resetToDefault,
    )
}

/**
 * Stateless "Customize tabs" editor: a reorderable list of every library tab with a visibility toggle
 * each. Drag the [Modifier.draggableHandle] on a row to move it (each hover-swap updates a transient
 * working copy so the move animates live); the net move is committed once on drop via [onMoveTab]. The
 * per-row [Switch] forwards to [onToggleTab]. Kept Hilt-free so it can be unit-tested directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeTabsScreenContent(
    tabs: List<CustomizableTab>,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    onToggleTab: (LibraryTab, Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_customize_tabs_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.library_customize_tabs_reset))
                    }
                },
            )
        },
    ) { contentPadding ->
        // Working copy so a drag reorders live without waiting for the ViewModel round-trip. Re-seeded
        // whenever the source list changes (a committed reorder or a toggle coming back).
        var localTabs by remember(tabs) { mutableStateOf(tabs) }

        // The index (into [localTabs]) where the current drag began; the net move start→end is what we
        // commit on drop, matching the ViewModel's atomic moveTab(from, to) contract.
        var dragStartIndex by remember { mutableStateOf<Int?>(null) }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            if (from.index in localTabs.indices && to.index in localTabs.indices) {
                localTabs = localTabs.toMutableList().apply { add(to.index, removeAt(from.index)) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(rootPadding),
        ) {
            Text(
                text = stringResource(R.string.library_customize_tabs_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("customizeTabsList"),
            ) {
                itemsIndexed(localTabs, key = { _, item -> item.tab.name }) { index, item ->
                    ReorderableItem(reorderableState, key = item.tab.name) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        val label = stringResource(item.tab.labelRes)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .shadow(elevation)
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp)
                                .testTag("customizeTabRow_${item.tab.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(
                                    R.string.library_customize_tabs_reorder, label
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .testTag("customizeTabDragHandle_${item.tab.name}")
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragStartIndex = localTabs.indexOfFirst {
                                                it.tab == item.tab
                                            }.takeIf { it >= 0 }
                                        },
                                        onDragStopped = {
                                            val from = dragStartIndex
                                            val to = localTabs.indexOfFirst { it.tab == item.tab }
                                            if (from != null && to >= 0 && from != to) {
                                                onMoveTab(from, to)
                                            }
                                            dragStartIndex = null
                                        },
                                    ),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            val toggleDescription = stringResource(
                                if (item.visible) R.string.library_customize_tabs_hide
                                else R.string.library_customize_tabs_show,
                                label,
                            )
                            Switch(
                                checked = item.visible,
                                onCheckedChange = { onToggleTab(item.tab, it) },
                                modifier = Modifier
                                    .testTag("customizeTabSwitch_${item.tab.name}")
                                    .semantics { contentDescription = toggleDescription },
                            )
                        }
                    }
                }
            }
        }
    }
}
