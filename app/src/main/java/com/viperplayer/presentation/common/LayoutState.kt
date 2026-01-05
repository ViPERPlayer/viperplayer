package com.viperplayer.presentation.common

import androidx.navigation.NavBackStackEntry
import com.viperplayer.presentation.navigation.Home
import com.viperplayer.presentation.navigation.Library
import com.viperplayer.presentation.navigation.Search
import com.viperplayer.presentation.navigation.Viper
import timber.log.Timber

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
    val route = currentDestination?.destination?.route

    Timber.d("Current route: $route")

    // Determine if we should show the bottom nav bar
    val showBottomNavBar = when (route) {
        null -> true
        Home::class.java.name -> true
        Search::class.java.name -> true
        Library::class.java.name -> true
        Viper::class.java.name -> true
        else -> false
    }

    // Determine if we should show the mini player
    // (not shown on full player screen, and only if there's content)
    val showMiniPlayer = when {
        !hasPlayingContent -> false
        else -> true
    }

    return LayoutVisibilityState(
        showBottomNavBar = showBottomNavBar,
        showMiniPlayer = showMiniPlayer,
    )
}

