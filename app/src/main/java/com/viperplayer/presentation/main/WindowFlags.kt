package com.viperplayer.presentation.main

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Applies the two window-flag-backed settings the UI layer owns:
 *  - [blockScreenshots] → [WindowManager.LayoutParams.FLAG_SECURE]: prevents screenshots and screen
 *    recording of the app window while enabled.
 *  - [keepScreenOn] (typically `setting && playerExpanded`) → [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]:
 *    keeps the display awake while the full-screen player is open.
 *
 * Business logic (reading the settings, computing when the player is expanded) lives in the
 * state-holder; this composable only mirrors the two booleans onto the Activity window flags and
 * clears them on dispose so a flag never lingers after the screen leaves composition.
 */
@Composable
fun PlayerWindowFlags(blockScreenshots: Boolean, keepScreenOn: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(blockScreenshots) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            if (blockScreenshots) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose {
            (view.context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(keepScreenOn) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            (view.context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
