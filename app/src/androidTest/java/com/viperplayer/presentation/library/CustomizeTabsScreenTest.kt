package com.viperplayer.presentation.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import com.viperplayer.domain.model.reconcileTabsConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the stateless [CustomizeTabsScreenContent]: it renders every tab with a
 * visibility [androidx.compose.material3.Switch], and toggling / reordering forwards the event. The
 * screen is stateless, so the test owns a [LibraryTabsConfig] and mutates it in response to the
 * callbacks (as [CustomizeTabsViewModel] would), then asserts the updated state — verifying the UI both
 * reflects and drives the config.
 */
class CustomizeTabsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Host the stateless content over a mutable [LibraryTabsConfig], reconciled into rows on each
     * recomposition. Returns an accessor for the live config so a test can assert the committed edits.
     */
    private fun setContentWithState(
        initial: LibraryTabsConfig = LibraryTabsConfig.default(LibraryTab.ALL_IDS),
    ): () -> LibraryTabsConfig {
        var config by mutableStateOf(initial)
        composeRule.setContent {
            val rows = resolveVisibleTabsRows(config)
            CustomizeTabsScreenContent(
                tabs = rows,
                rootPadding = PaddingValues(),
                onNavigateBack = {},
                onMoveTab = { from, to ->
                    config = LibraryTabsConfig(
                        rows.toMutableList().apply { add(to, removeAt(from)) }
                            .map { LibraryTabSetting(it.tab.name, it.visible) }
                    )
                },
                onToggleTab = { tab, visible ->
                    config = LibraryTabsConfig(
                        rows.map {
                            LibraryTabSetting(it.tab.name, if (it.tab == tab) visible else it.visible)
                        }
                    )
                },
                onReset = { config = LibraryTabsConfig.default(LibraryTab.ALL_IDS) },
            )
        }
        return { config }
    }

    /** Reconcile a config into editor rows the same way the ViewModel does. */
    private fun resolveVisibleTabsRows(config: LibraryTabsConfig): List<CustomizableTab> =
        reconcileTabsConfig(LibraryTab.ALL_IDS, config)
            .mapNotNull { s -> LibraryTab.fromId(s.id)?.let { CustomizableTab(it, s.visible) } }

    @Test
    fun rendersAllTabsWithSwitches() {
        setContentWithState()

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").assertIsDisplayed()
        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("Playlists").assertIsDisplayed()

        // Default config → every switch on.
        composeRule.onNodeWithTag("customizeTabSwitch_SONGS").assertIsOn()
        composeRule.onNodeWithTag("customizeTabSwitch_ARTISTS").assertIsOn()
    }

    @Test
    fun togglingSwitch_hidesTab_updatesState() {
        val config = setContentWithState()

        composeRule.onNodeWithTag("customizeTabSwitch_ARTISTS").performClick()

        // The ARTISTS entry is now hidden in the committed config.
        val artists = config().tabs.first { it.id == "ARTISTS" }
        assertEquals(false, artists.visible)
        composeRule.onNodeWithTag("customizeTabSwitch_ARTISTS").assertIsOff()
    }

    @Test
    fun reset_restoresAllVisible() {
        // Start from a config with ARTISTS hidden, then reset → all visible again.
        val config = setContentWithState(
            LibraryTabsConfig(
                LibraryTab.entries.map { LibraryTabSetting(it.name, it != LibraryTab.ARTISTS) }
            )
        )
        composeRule.onNodeWithTag("customizeTabSwitch_ARTISTS").assertIsOff()

        composeRule.onNodeWithText("Reset to default").performClick()

        assertEquals(true, config().tabs.all { it.visible })
        composeRule.onNodeWithTag("customizeTabSwitch_ARTISTS").assertIsOn()
    }
}
