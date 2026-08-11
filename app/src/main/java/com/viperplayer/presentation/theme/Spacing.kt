package com.viperplayer.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale — every padding, gap and inset should come from here rather than from a
 * `dp` literal at the call site.
 *
 * The scale is a 4dp grid, which is what the codebase was already converging on: before this existed,
 * 16 / 8 / 12 / 4 / 24 / 20 accounted for 957 of the 1,539 `dp` literals in the presentation layer.
 * The remaining literals were drifting off that grid in ways nobody chose — 13 next to 14, 18 next to
 * 19, 21 next to 22, 25 next to 26 — differences no one can see and no one intended.
 *
 * Sizes that are not spacing (icon dimensions, artwork heights, minimum touch targets, corner radii)
 * deliberately stay as literals at their call sites: they are properties of a specific component, not
 * steps on a shared rhythm, and forcing them through this scale would make it meaningless.
 */
object Spacing {

    /** 4dp — the grid unit. Tight gaps inside a single component (icon to its label). */
    val xs = 4.dp

    /** 8dp — related elements within a group (rows of a list item, chips in a row). */
    val sm = 8.dp

    /** 12dp — between grouped elements that still read as one block. */
    val md = 12.dp

    /** 16dp — the default. Screen edge insets and the gap between distinct elements. */
    val lg = 16.dp

    /** 20dp — a slightly airier separation than [lg], used inside roomier cards and sheets. */
    val xl = 20.dp

    /** 24dp — between sections of a screen. */
    val xxl = 24.dp

    /** 32dp — the widest step: around a screen's hero content, or above a primary action. */
    val xxxl = 32.dp
}
