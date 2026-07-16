package com.viperplayer.presentation.viper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.viperplayer.domain.model.IirEqualizerState
import com.viperplayer.presentation.viper.effect.IirEqualizerEffect
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the equalizer's "Import AutoEq profile" entry point. Verifies the action is
 * reachable once the effect card is expanded. (The file picker itself is a system contract and is
 * not launched here.)
 */
class IirEqualizerImportTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importAutoEqAction_isShownWhenEqualizerExpanded() {
        composeRule.setContent {
            IirEqualizerEffect(
                state = IirEqualizerState(enabled = true, bandCount = 10),
                onEnabledChange = {},
                onBandCountChange = {},
                onPresetChange = {},
                onBandGainChange = { _, _ -> },
                onImportAutoEq = {},
                onReset = {},
            )
        }

        // The effect card starts collapsed; expand it by clicking the title.
        composeRule.onNodeWithText("IIR Equalizer").performClick()

        composeRule.onNodeWithText("Import AutoEq profile").assertIsDisplayed()
    }
}
