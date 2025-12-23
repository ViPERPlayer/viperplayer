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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.*
import com.viperplayer.presentation.common.MiniPlayer
import com.viperplayer.presentation.common.determineLayoutVisibility
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.navigation.*
import com.viperplayer.presentation.player.PlayerViewModel
import com.viperplayer.ui.theme.ViPERPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

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

enum class SubcomposeSlot {
    MiniPlayer,
    Content
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViperPlayerMainScreen() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val playerState by playerViewModel.playerState.collectAsState()

    val bottomNavItems = listOf(
        BottomNavItem(Home, "Home", Icons.Default.Home),
        BottomNavItem(Search, "Search", Icons.Default.Search),
        BottomNavItem(Library, "Library", Icons.Default.LibraryMusic),
        BottomNavItem(Plugins, "Plugins", Icons.Default.Extension)
    )
    
    // Determine layout visibility based on current destination and player state
    val layoutState = determineLayoutVisibility(
        currentDestination = navBackStackEntry,
        hasPlayingContent = playerState.hasContent,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (layoutState.showBottomNavBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val isSelected = when (item.route) {
                            Home -> navBackStackEntry?.destination?.route?.contains("Home") == true
                            Search -> navBackStackEntry?.destination?.route?.contains("Search") == true
                            Library -> navBackStackEntry?.destination?.route?.contains("Library") == true
                            Plugins -> navBackStackEntry?.destination?.route?.contains("Plugins") == true
                            else -> {
                                Timber.d("Unknown route: ${navBackStackEntry?.destination?.route}")
                                false
                            }
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
    ) { innerPadding ->
        SubcomposeLayout(
            modifier = Modifier.fillMaxSize()
        ) { constraints ->
            // Step 1: Measure mini player to get its height
            val miniPlayerMeasurables = subcompose(SubcomposeSlot.MiniPlayer) {
                if (layoutState.showMiniPlayer) {
                    MiniPlayer(
                        playerState = playerState,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onMiniPlayerClick = { navController.navigate(NowPlaying) }
                    )
                }
            }

            // Measure the mini player with available constraints
            val miniPlayerPlaceable = miniPlayerMeasurables.firstOrNull()?.measure(
                constraints.copy(minHeight = 0)
            )

            val miniPlayerHeight = miniPlayerPlaceable?.height ?: 0

            val miniPlayerY = constraints.maxHeight - miniPlayerHeight - innerPadding.calculateBottomPadding().roundToPx()

            // Update the inner padding to account for the mini player
            val rootPadding = innerPadding + PaddingValues(bottom = miniPlayerHeight.toDp())

            // Step 2: Measure content with constraints reduced by mini player height
            val contentMeasurables = subcompose(SubcomposeSlot.Content) {
                ViperNavHost(
                    navController = navController,
                    rootPadding = rootPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val contentPlaceable = contentMeasurables.firstOrNull()?.measure(
                constraints
            )

            // Step 3: Layout everything in one pass
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Place content at top
                contentPlaceable?.place(0, 0)

                // Place mini player at bottom (overlaying the content)
                miniPlayerPlaceable?.place(0, miniPlayerY)
            }
        }
    }
}
