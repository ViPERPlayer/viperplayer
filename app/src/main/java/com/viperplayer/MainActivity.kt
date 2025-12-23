package com.viperplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.viperplayer.presentation.navigation.*
import com.viperplayer.presentation.player.MiniPlayer
import com.viperplayer.ui.theme.ViPERPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ViPERPlayerTheme {
                ViperPlayerMainScreen()
            }
        }
    }
}

data class BottomNavItem(
    val route: Any,
    val title: String,
    val icon: ImageVector
)

/**
 * Routes that should display the bottom navigation bar.
 * Full-screen experiences like NowPlaying and Settings are excluded.
 */
private val bottomBarRoutes = setOf(
    Home::class.qualifiedName,
    Search::class.qualifiedName,
    Library::class.qualifiedName,
    Plugins::class.qualifiedName
)

/**
 * Determines if the bottom navigation bar should be shown for the current destination.
 */
private fun shouldShowBottomBar(destination: NavDestination?): Boolean {
    return destination?.hierarchy?.any { dest ->
        dest.route?.let { route ->
            // Extract the base route class name (before any parameters)
            val baseRoute = route.substringBefore("?").substringBefore("/")
            bottomBarRoutes.any { it.contains(baseRoute) }
        } == true
    } == true
}

/**
 * Determines if the mini player should be shown for the current destination.
 * Hidden only on the full player screen (NowPlaying).
 */
private fun shouldShowMiniPlayer(destination: NavDestination?): Boolean {
    return destination?.hierarchy?.any { dest ->
        dest.route?.contains("NowPlaying") == true
    } != true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViperPlayerMainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine visibility based on current destination
    val showBottomBar = shouldShowBottomBar(currentDestination)
    val showMiniPlayer = shouldShowMiniPlayer(currentDestination)

    val bottomNavItems = listOf(
        BottomNavItem(Home, "Home", Icons.Default.Home),
        BottomNavItem(Search, "Search", Icons.Default.Search),
        BottomNavItem(Library, "Library", Icons.Default.LibraryMusic),
        BottomNavItem(Plugins, "Plugins", Icons.Default.Extension)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Stack mini player and bottom nav bar
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        onExpandClick = {
                            navController.navigate(NowPlaying)
                        }
                    )
                }

                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val isSelected = when (item.route) {
                                Home -> currentDestination?.hierarchy?.any {
                                    it.route?.contains("Home") == true
                                } == true
                                Search -> currentDestination?.hierarchy?.any {
                                    it.route?.contains("Search") == true
                                } == true
                                Library -> currentDestination?.hierarchy?.any {
                                    it.route?.contains("Library") == true
                                } == true
                                Plugins -> currentDestination?.hierarchy?.any {
                                    it.route?.contains("Plugins") == true
                                } == true
                                else -> false
                            }

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationRoute!!) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Pass innerPadding to NavHost, which will automatically pass it to all destinations
        ViperNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

