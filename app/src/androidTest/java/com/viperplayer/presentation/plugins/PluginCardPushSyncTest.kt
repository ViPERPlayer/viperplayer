package com.viperplayer.presentation.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the per-plugin "Push changes to account" (two-way sync) toggle in the plugin
 * card overflow menu. Verifies the toggle appears only when the plugin advertises library-write, and
 * that toggling forwards the new enabled state to the ViewModel-facing callback.
 */
class PluginCardPushSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun info(id: String = "testsource") =
        PluginInfo(id = id, name = "a plugin", version = "1.0", apiVersion = 1)

    private fun plugin(canWrite: Boolean) =
        Plugin(info = info(), capabilities = PluginCapabilities(hasLibraryWrite = canWrite))

    @Test
    fun pushSyncToggle_hiddenWhenPluginCannotWrite() {
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = plugin(canWrite = false),
                showMenu = true,
                canPushSync = false,
                pushSyncEnabled = false,
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Push changes to account").assertDoesNotExist()
    }

    @Test
    fun pushSyncToggle_shownAndReflectsState() {
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = plugin(canWrite = true),
                showMenu = true,
                canPushSync = true,
                pushSyncEnabled = true,
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Push changes to account").assertIsDisplayed()
    }

    @Test
    fun togglingPushSync_forwardsNewState() {
        var lastToggle: Boolean? = null
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = plugin(canWrite = true),
                showMenu = true,
                canPushSync = true,
                pushSyncEnabled = enabled,
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onTogglePushSync = { newValue ->
                    lastToggle = newValue
                    enabled = newValue
                },
                onShowInfo = {},
                onUninstall = {},
            )
        }

        composeRule.onNodeWithText("Push changes to account").performClick()
        assertEquals(true, lastToggle)
    }
}
