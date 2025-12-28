package com.viperplayer.presentation.common

import androidx.navigation.NavBackStackEntry

/**
 * Represents the layout visibility state based on the current navigation destination
 * and player state.
 */
data class LayoutVisibilityState(
    val showBottomNavBar: Boolean = true,
    val showMiniPlayer: Boolean = true,
)

/**
 * Determines layout visibility rules based on navigation state.
 * - Settings screens hide the bottom nav bar
 * - Full player screen (NowPlaying) hides both nav bar and mini player
 * - Other screens show both (if there's content to play)
 */
fun determineLayoutVisibility(
    currentDestination: NavBackStackEntry?,
    hasPlayingContent: Boolean,
): LayoutVisibilityState {
    val routeName = currentDestination?.destination?.route

    // Determine if we should show the bottom nav bar
    val showBottomNavBar = when {
        routeName == null -> true
        routeName.contains("Settings") -> false
        routeName.contains("NowPlaying") -> false
        else -> true
    }

    // Determine if we should show the mini player
    // (not shown on full player screen, and only if there's content)
    val showMiniPlayer = when {
        !hasPlayingContent -> false
        routeName == null -> false
        routeName.contains("NowPlaying") -> false
        else -> true
    }

    return LayoutVisibilityState(
        showBottomNavBar = showBottomNavBar,
        showMiniPlayer = true,
    )
}

