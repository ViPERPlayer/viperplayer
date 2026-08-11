package com.viperplayer.presentation.plugins

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.plugin.PluginUpdate
import com.viperplayer.domain.plugin.PluginUpdateProgress
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the "Update available" section of a [PluginCard]: it appears only when an
 * update is offered, shows the version transition, and its Update / Dismiss controls forward to the
 * ViewModel-facing callbacks. While a download is in flight it shows progress instead of buttons.
 */
class PluginUpdateCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun info(id: String = "testsource") =
        PluginInfo(id = id, name = "a plugin", version = "1.0", apiVersion = 1)

    private fun connected() =
        Plugin(info = info(), capabilities = PluginCapabilities())

    private fun update(changelog: String? = "Bug fixes") = PluginUpdate(
        pluginId = "testsource",
        installedVersionName = "1.0",
        availableVersionCode = 3,
        availableVersionName = "1.3.0",
        downloadUrl = "https://example.com/example.apk",
        changelog = changelog,
        sha256 = null,
    )

    @Test
    fun updateSection_hiddenWhenNoUpdate() {
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = null,
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Update available").assertDoesNotExist()
    }

    @Test
    fun updateSection_shownWithVersionTransition() {
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = update(),
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Update available").assertIsDisplayed()
        composeRule.onNodeWithText("1.0 → 1.3.0").assertIsDisplayed()
    }

    @Test
    fun updateButton_forwardsInstall() {
        var installed = false
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = update(),
                onInstallUpdate = { installed = true },
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Update").performClick()
        assertTrue(installed)
    }

    @Test
    fun dismissButton_forwardsDismiss() {
        var dismissed = false
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = update(),
                onDismissUpdate = { dismissed = true },
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun changelogButton_forwardsShowChangelog() {
        var shown = false
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = update(changelog = "New stuff"),
                onShowChangelog = { shown = true },
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("What's new").performClick()
        assertTrue(shown)
    }

    @Test
    fun downloadingProgress_hidesActionButtons() {
        composeRule.setContent {
            PluginCard(
                plugin = info(),
                isEnabled = true,
                isConnected = true,
                isToggling = false,
                connectedPlugin = connected(),
                showMenu = false,
                update = update(),
                updateProgress = PluginUpdateProgress.Downloading("testsource", fraction = 0.5f),
                onToggle = {},
                onLongPress = {},
                onDismissMenu = {},
                onShowInfo = {},
                onUninstall = {},
            )
        }
        composeRule.onNodeWithText("Downloading…").assertIsDisplayed()
        // While downloading, the idle actions are replaced by the progress row.
        composeRule.onNodeWithText("Update").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").assertDoesNotExist()
    }
}
