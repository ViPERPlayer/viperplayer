package com.viperplayer.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.presentation.common.MiniPlayer
import com.viperplayer.presentation.common.determineLayoutVisibility
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.navigation.Home
import com.viperplayer.presentation.navigation.Library
import com.viperplayer.presentation.navigation.NowPlaying
import com.viperplayer.presentation.navigation.Plugins
import com.viperplayer.presentation.navigation.Search
import com.viperplayer.presentation.navigation.ViperNavHost
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ViperPlayerAppViewModel @Inject constructor() : ViewModel() {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

}

@Composable
fun ViperPlayerApp() {
    ViPERPlayerTheme {
        val navController = rememberNavController()
        val viewModel: ViperPlayerAppViewModel = hiltViewModel()

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val playerState by viewModel.playerState.collectAsState()

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
                // Measure mini player to get its height
                val miniPlayerMeasurables = subcompose(SubcomposeSlot.MiniPlayer) {
                    if (layoutState.showMiniPlayer) {
                        MiniPlayer(
                            playerState = playerState,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
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

                // Measure content
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

                // Layout everything in one pass
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Place content at top
                    contentPlaceable?.place(0, 0)

                    // Place mini player at bottom (overlaying the content)
                    miniPlayerPlaceable?.place(0, miniPlayerY)
                }
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
