package com.viperplayer.presentation.settings.lastfm

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.lastfm.LastfmSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for the Last.fm settings/login screen. Drives the stateless body directly (no Hilt)
 * and verifies the logged-out login form, entering credentials, the logged-in state, and the
 * scrobbling toggles.
 */
class LastfmSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        state: LastfmSettingsUiState,
        onLogin: (String, String) -> Unit = { _, _ -> },
        onLogout: () -> Unit = {},
        onScrobblingEnabled: (Boolean) -> Unit = {},
        onNowPlayingEnabled: (Boolean) -> Unit = {},
        onScrobbleOnWifiOnly: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            LastfmSettingsContent(
                state = state,
                rootPadding = PaddingValues(0.dp),
                onNavigateBack = {},
                onLogin = onLogin,
                onLogout = onLogout,
                onScrobblingEnabled = onScrobblingEnabled,
                onNowPlayingEnabled = onNowPlayingEnabled,
                onScrobbleOnWifiOnly = onScrobbleOnWifiOnly,
            )
        }
    }

    @Test
    fun loggedOut_showsLoginForm() {
        content(LastfmSettingsUiState(settings = LastfmSettings(isAuthenticated = false)))
        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
    }

    @Test
    fun enteringCredentialsAndSigningIn_firesLoginWithThoseValues() {
        var captured: Pair<String, String>? = null
        content(
            LastfmSettingsUiState(settings = LastfmSettings(isAuthenticated = false)),
            onLogin = { user, pass -> captured = user to pass },
        )
        composeRule.onNodeWithText("Username").performTextInput("alice")
        composeRule.onNodeWithText("Password").performTextInput("hunter2")
        composeRule.onNodeWithText("Sign in").performClick()
        assertEquals("alice" to "hunter2", captured)
    }

    @Test
    fun loggedIn_showsUsernameAndSignOut() {
        content(
            LastfmSettingsUiState(
                settings = LastfmSettings(username = "alice", isAuthenticated = true)
            )
        )
        composeRule.onNodeWithText("Signed in as alice").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    fun tappingSignOut_firesLogout() {
        var loggedOut = false
        content(
            LastfmSettingsUiState(
                settings = LastfmSettings(username = "alice", isAuthenticated = true)
            ),
            onLogout = { loggedOut = true },
        )
        composeRule.onNodeWithText("Sign out").performClick()
        assertEquals(true, loggedOut)
    }

    @Test
    fun togglingScrobbling_firesCallbackWithNewValue() {
        var newValue: Boolean? = null
        // Scrobbling defaults on; tapping the row should request turning it off.
        content(
            LastfmSettingsUiState(
                settings = LastfmSettings(isAuthenticated = true, scrobblingEnabled = true)
            ),
            onScrobblingEnabled = { newValue = it },
        )
        composeRule.onNodeWithText("Enable scrobbling").performClick()
        assertEquals(false, newValue)
    }

    @Test
    fun notConfigured_showsNotice() {
        content(
            LastfmSettingsUiState(
                settings = LastfmSettings(isAuthenticated = false),
                isApiConfigured = false,
            )
        )
        composeRule.onNodeWithText("Last.fm API key required").assertIsDisplayed()
    }
}
