package com.viperplayer.presentation.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // This is a top level route, just switch to it.
            state.topLevelRoute = route
        } else {
            val currentStack = state.backStacks[state.topLevelRoute]
                ?: error("Stack for ${state.topLevelRoute} not found")
            // Single-top guard: a rapid double-tap must not push the same destination twice.
            if (NavigationLogic.shouldPush(currentStack, route)) {
                currentStack.add(route)
            }
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack. Checking
        // `currentRoute == topLevelRoute` is sufficient: navigating to a top-level route only switches
        // `topLevelRoute` (it is never push()ed into a stack), so a top-level route can only ever be a
        // stack's base element — reaching it as `last()` already implies the stack size is 1.
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
