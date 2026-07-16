package com.viperplayer.presentation.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Keeps the status-bar and navigation-bar icon contrast in sync with the app's *resolved* theme.
 *
 * [android.app.Activity]'s `enableEdgeToEdge()` only picks bar-icon appearance once, from the OS
 * dark-mode flag. When the user forces Light or Dark in-app against the opposite system setting, the
 * icons would otherwise be the wrong polarity (e.g. light icons on a light bar → invisible). This
 * runs on every recomposition where [darkTheme] changes and flips the appearance flags to match:
 * light (dark-on-light) icons under a light theme, dark (light-on-dark) icons under a dark theme.
 */
@Composable
fun ThemedSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        // "Light bars" == dark icons, which we want under a LIGHT theme (and vice-versa).
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
