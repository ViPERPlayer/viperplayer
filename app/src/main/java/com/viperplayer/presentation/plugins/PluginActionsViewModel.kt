package com.viperplayer.presentation.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes the pending user actions reported by connected plugins. Shared by every surface that
 * shows them (Home bell + banner, Plugins screen badges, error CTAs).
 */
@HiltViewModel
class PluginActionsViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
) : ViewModel() {

    val pendingActions: StateFlow<List<PluginPendingAction>> = pluginRepository.pendingActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-query all plugins — call after a resolution attempt or when returning to the app. */
    fun refresh() {
        viewModelScope.launch { pluginRepository.refreshPluginStatuses() }
    }
}
