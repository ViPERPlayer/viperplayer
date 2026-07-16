package com.viperplayer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.reconcileTabsConfig
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One row in the "Customize tabs" editor: a [tab] plus whether it is currently [visible]. Hidden tabs
 * are shown too (so they can be re-enabled) — the flag drives the toggle, not the row's presence.
 */
data class CustomizableTab(
    val tab: LibraryTab,
    val visible: Boolean,
)

/**
 * ViewModel for the "Customize tabs" screen. It reconciles the persisted [LibraryTabsConfig] into the
 * full ordered list of [CustomizableTab] (every shipped tab exactly once, hidden ones included) and
 * persists edits back. All reordering / visibility logic that matters is the pure
 * [reconcileTabsConfig]; this class just adapts it to the UI and writes to [SettingsRepository].
 */
@HiltViewModel
class CustomizeTabsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * The editor rows, in the persisted order, reconciled against the tabs the app ships. Any id that no
     * longer resolves to a [LibraryTab] is dropped (defensive — [reconcileTabsConfig] already keeps only
     * known ids).
     */
    val tabs: StateFlow<List<CustomizableTab>> = settingsRepository.libraryTabsConfig
        .map { config -> config.toCustomizableTabs() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryTabsConfig.EMPTY.toCustomizableTabs(),
        )

    /** Move the tab at [from] to [to] and persist the new order (visibility flags unchanged). */
    fun moveTab(from: Int, to: Int) {
        val current = tabs.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        persist(reordered)
    }

    /**
     * Set [tab]'s visibility and persist. Guards the all-hidden case: turning off the last visible tab
     * is a no-op (the Library must keep at least one tab), so the UI's toggle simply won't take.
     */
    fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        val current = tabs.value
        if (!visible && current.count { it.visible } <= 1 && current.any { it.tab == tab && it.visible }) {
            return
        }
        val updated = current.map { if (it.tab == tab) it.copy(visible = visible) else it }
        persist(updated)
    }

    /** Reset to the default config: every tab in its natural order, all visible. */
    fun resetToDefault() {
        viewModelScope.launch {
            settingsRepository.setLibraryTabsConfig(LibraryTabsConfig.default(LibraryTab.ALL_IDS))
        }
    }

    private fun persist(items: List<CustomizableTab>) {
        val config = LibraryTabsConfig(items.map { LibraryTabSetting(it.tab.name, it.visible) })
        viewModelScope.launch {
            settingsRepository.setLibraryTabsConfig(config)
        }
    }

    private fun LibraryTabsConfig.toCustomizableTabs(): List<CustomizableTab> =
        reconcileTabsConfig(LibraryTab.ALL_IDS, this).mapNotNull { setting ->
            LibraryTab.fromId(setting.id)?.let { CustomizableTab(it, setting.visible) }
        }
}
