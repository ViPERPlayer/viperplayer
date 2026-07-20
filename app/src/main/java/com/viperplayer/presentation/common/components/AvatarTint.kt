package com.viperplayer.presentation.common.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Deterministic per-identity avatar tints.
 *
 * Maps a stable [key] (a friend / sharer / participant id) onto one of a small, fixed palette of
 * Material 3 container/on-container colour pairs, so the same person always reads as the same tone and
 * a cluster of avatars reads as distinct people rather than one repeated colour. Pure — no Compose
 * runtime, no `MaterialTheme`, no side effects — so it's unit-testable in isolation; callers pass in
 * the resolved [ColorScheme] (e.g. `MaterialTheme.colorScheme`).
 *
 * The palette is index-stable: entries are only ever appended, never reordered, so an id keeps its
 * tone across releases (the mapping is `abs(key.hashCode()) % size`).
 */
fun avatarTonesFor(key: String, scheme: ColorScheme): Pair<Color, Color> {
    val palette = listOf(
        scheme.errorContainer to scheme.onErrorContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
    )
    return palette[avatarToneIndex(key, palette.size)]
}

/**
 * The pure palette-index selection behind [avatarTonesFor]: deterministic `abs(hashCode) % size`,
 * factored out so the mapping is unit-testable without constructing a [ColorScheme]. Always returns an
 * index in `0 until paletteSize` (mod-then-abs handles `Int.MIN_VALUE`, whose plain `abs` overflows).
 */
internal fun avatarToneIndex(key: String, paletteSize: Int): Int =
    abs(key.hashCode() % paletteSize)
