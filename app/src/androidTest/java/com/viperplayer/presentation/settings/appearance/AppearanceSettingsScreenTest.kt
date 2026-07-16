package com.viperplayer.presentation.settings.appearance

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the new appearance controls (accent picker + custom color entry). Drives the
 * stateless [AppearanceSettingsContent] directly (no Hilt) and verifies picking swatches changes
 * state via the callbacks.
 */
class AppearanceSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        state: AppearanceSettingsUiState,
        onAccentColor: (Int?) -> Unit = {}
    ) {
        composeRule.setContent {
            AppearanceSettingsContent(
                state = state,
                rootPadding = PaddingValues(0.dp),
                onNavigateBack = {},
                onDynamicThemeMode = {},
                onThemeMode = {},
                onPureBlack = {},
                onAccentColor = onAccentColor
            )
        }
    }

    @Test
    fun rendersAccentColorSection() {
        content(AppearanceSettingsUiState(dynamicThemeMode = DynamicThemeMode.OFF))
        composeRule.onNodeWithText("Accent Color").assertIsDisplayed()
    }

    @Test
    fun defaultSwatchClears_accentToNull() {
        var picked: Int? = 0x123456
        content(
            AppearanceSettingsUiState(
                dynamicThemeMode = DynamicThemeMode.OFF,
                accentColor = AccentPresets[1].color.toArgb()
            ),
            onAccentColor = { picked = it }
        )
        composeRule.onNodeWithContentDescription("Default").performClick()
        assertEquals(null, picked)
    }

    @Test
    fun pickingPresetSwatch_firesCallbackWithThatArgb() {
        var picked: Int? = null
        val green = AccentPresets[1] // first selectable preset (index 0 is the Default dot)
        content(
            AppearanceSettingsUiState(dynamicThemeMode = DynamicThemeMode.OFF),
            onAccentColor = { picked = it }
        )
        composeRule.onNodeWithContentDescription(green.name).performClick()
        assertEquals(green.color.toArgb(), picked)
    }

    @Test
    fun customEntryOpensDialog() {
        content(AppearanceSettingsUiState(dynamicThemeMode = DynamicThemeMode.OFF))
        composeRule.onNodeWithContentDescription("custom_accent").performClick()
        // The custom color dialog should appear with its Apply action.
        composeRule.onNodeWithText("Apply").assertIsDisplayed()
    }

    @Test
    fun themeAndPureBlackControlsStillRender() {
        content(
            AppearanceSettingsUiState(
                themeMode = ThemeMode.SYSTEM,
                dynamicThemeMode = DynamicThemeMode.OFF
            )
        )
        composeRule.onNodeWithText("Theme Type").assertIsDisplayed()
        composeRule.onNodeWithText("Pure Black").assertIsDisplayed()
    }
}
