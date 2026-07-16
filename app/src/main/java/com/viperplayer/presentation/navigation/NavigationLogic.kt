package com.viperplayer.presentation.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Pure, Android-free navigation-stack decisions, factored out of [Navigator] so the single-top
 * guard is unit-testable without Compose or navigation runtime state.
 */
object NavigationLogic {

    /**
     * Single-top guard: whether [route] should actually be pushed onto [stack].
     *
     * Returns false when [route] already sits on top of the stack, so a rapid double-tap on a
     * result can't push the same detail destination twice. Data-class keys (album/artist/playlist
     * detail with the same id + args) compare equal, so re-tapping the exact same item is a no-op
     * while navigating to a *different* item of the same type still pushes.
     */
    fun shouldPush(stack: List<NavKey>, route: NavKey): Boolean =
        stack.lastOrNull() != route
}
