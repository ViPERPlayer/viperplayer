package com.viperplayer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.reconcileTabsConfig
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * ViewModel for the "Customize tabs" screen.
 *
 * The editable rows live in an authoritative in-VM [MutableStateFlow] ([_tabs]) that every edit mutates
 * SYNCHRONOUSLY (see [editAndPersist]) and then persists as a side effect. This is deliberate:
 * deriving the rows directly from DataStore and reading them back per edit races — DataStore's
 * write→re-emit has latency, so two edits fired inside that window would both read the same pre-edit
 * value and the second would overwrite the first (lost update). Keeping the working state in-VM means a
 * rapid second edit always reads the post-first-edit state, so nothing is lost.
 *
 * On (re)entry the working state is seeded from the persisted [LibraryTabsConfig], reconciled against the
 * tabs the app ships (every shipped tab exactly once, hidden ones included) via the pure
 * [reconcileTabsConfig]. Because this Hilt ViewModel is recreated per navigation to the screen, that seed
 * IS the re-entry reconciliation.
 */
@HiltViewModel
class CustomizeTabsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * The authoritative editor rows, in the persisted order, reconciled against the tabs the app ships.
     * Every edit mutates this synchronously; the screen renders from it so a toggle/reorder reflects
     * instantly, and it is the source persisted back to [SettingsRepository]. Seeded in [init].
     */
    private val _tabs = MutableStateFlow(LibraryTabsConfig.EMPTY.toCustomizableTabs())
    val tabs: StateFlow<List<CustomizableTab>> = _tabs.asStateFlow()

    init {
        // Seed the working state from the persisted config once, reconciled against the shipped tabs.
        // first() gives the current stored value; since the ViewModel is recreated on each navigation to
        // the screen, this doubles as the on-(re)entry reconciliation.
        viewModelScope.launch {
            val config = settingsRepository.libraryTabsConfig.first()
            _tabs.value = config.toCustomizableTabs()
        }
    }

    /** Move the tab at [from] to [to] and persist the new order (visibility flags unchanged). */
    fun moveTab(from: Int, to: Int) {
        editAndPersist { current ->
            if (from !in current.indices || to !in current.indices || from == to) return
            current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /**
     * Set [tab]'s visibility and persist. Guards the all-hidden case: turning off the last visible tab
     * is a no-op (the Library must keep at least one tab), so the UI's toggle simply won't take.
     */
    fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        editAndPersist { current ->
            if (!visible && current.count { it.visible } <= 1 &&
                current.any { it.tab == tab && it.visible }
            ) {
                return
            }
            current.map { if (it.tab == tab) it.copy(visible = visible) else it }
        }
    }

    /** Reset to the default config: every tab in its natural order, all visible. */
    fun resetToDefault() {
        editAndPersist { LibraryTabsConfig.default(LibraryTab.ALL_IDS).toCustomizableTabs() }
    }

    /**
     * Apply [transform] to the authoritative working state SYNCHRONOUSLY (so a rapid second edit reads the
     * post-first-edit rows, never a stale DataStore value), then persist the new rows as a side effect.
     *
     * The read-modify-write runs inline on the caller's thread — edit methods are always invoked from the
     * UI callback path (main thread), so there is no interleaving to lose an update to. [transform] may
     * `return` from the calling edit method to reject the edit as a no-op (e.g. the all-hidden guard); the
     * working state is then left unchanged and nothing is persisted.
     */
    private inline fun editAndPersist(transform: (List<CustomizableTab>) -> List<CustomizableTab>) {
        val updated = transform(_tabs.value)
        _tabs.value = updated
        persist(updated)
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
