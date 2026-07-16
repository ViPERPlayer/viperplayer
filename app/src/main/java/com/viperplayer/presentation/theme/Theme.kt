package com.viperplayer.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

/**
 * The seed color used to generate a Material 3 scheme when neither Material You nor
 * color-from-album-art is driving the theme (dynamic color OFF, or no custom accent chosen). Keeps
 * the app on-brand instead of the flat default purple.
 */
val DefaultSeedColor: Color = Color(0xFF6750A4)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViPERPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureDark: Boolean = false,
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        // An explicit seed (album art or a user-picked accent) always wins — it generates a full
        // tonal scheme regardless of OS version.
        seedColor != null -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )

        // Material You wallpaper colors, only on Android 12+ where they exist.
        dynamicColor && supportsDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // No dynamic source available (dynamic off, or pre-Android 12 with no accent): fall back to
        // a generated scheme from the brand seed rather than the flat baseline palette.
        else -> rememberDynamicColorScheme(
            seedColor = DefaultSeedColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
    }

    MaterialExpressiveTheme(
        colorScheme = if (darkTheme && pureDark) colorScheme.pureDark() else colorScheme,
        content = content
    )
}

/**
 * Maps every dark-theme surface role to true black for AMOLED "pure black" mode. Covers the whole
 * surface family (containers, dim/bright, elevation-tinted variants) — not just [ColorScheme.surface]
 * and [ColorScheme.background] — so cards, sheets and bars that draw on `surfaceContainer*` don't
 * show near-black gray boxes on an OLED panel. The `on*` roles are preserved so foreground contrast
 * is unaffected.
 */
private fun ColorScheme.pureDark(): ColorScheme {
    return this.copy(
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black
    )
}
