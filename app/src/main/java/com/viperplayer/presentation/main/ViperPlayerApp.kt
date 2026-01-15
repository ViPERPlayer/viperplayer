package com.viperplayer.presentation.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viperplayer.domain.repository.ThemeMode
import com.viperplayer.presentation.common.determineLayoutVisibility
import com.viperplayer.presentation.navigation.Home
import com.viperplayer.presentation.navigation.Library
import com.viperplayer.presentation.navigation.Search
import com.viperplayer.presentation.navigation.Viper
import com.viperplayer.presentation.navigation.ViperNavHost
import com.viperplayer.presentation.player.MiniPlayer
import com.viperplayer.presentation.player.PlayerScreen
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import kotlinx.coroutines.launch

data class BottomNavItem(
    val route: Any,
    val title: String,
    val icon: ImageVector
)

enum class SubcomposeSlot {
    Content,
    MiniPlayer,
    NavigationBar
}

@Composable
fun ViperPlayerApp(
    viewModel: ViperPlayerAppViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDarkTheme = isSystemInDarkTheme()
    
    val darkTheme = when (uiState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }

    ViPERPlayerTheme(
        darkTheme = darkTheme,
        pureDark = uiState.pureBlack && (uiState.themeMode == ThemeMode.DARK || uiState.themeMode == ThemeMode.SYSTEM),
        dynamicColor = uiState.dynamicTheme,
        seedColor = if (uiState.dynamicTheme) uiState.themeColor else null
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val navController = rememberNavController()
            val windowInsets = NavigationBarDefaults.windowInsets
            val bottomInset = windowInsets.getBottom(density)

            val navBackStackEntry by navController.currentBackStackEntryAsState()

            var showPlayerBottomSheet by remember { mutableStateOf(false) }

            val bottomNavItems = listOf(
                BottomNavItem(Home, "Home", Icons.Rounded.Home),
                BottomNavItem(Search, "Search", Icons.Rounded.Search),
                BottomNavItem(Library, "Library", Icons.Rounded.LibraryMusic),
                BottomNavItem(Viper, "ViPER", Icons.Rounded.Equalizer)
            )

            val layoutState = determineLayoutVisibility(
                currentDestination = navBackStackEntry,
                hasPlayingContent = uiState.hasCurrentSong,
            )

            val navBarY = remember { Animatable(-1, Int.VectorConverter) }
            val miniPlayerY = remember { Animatable(-1, Int.VectorConverter) }

            SubcomposeLayout(
                modifier = Modifier.fillMaxSize()
            ) { constraints ->
                val navBarMeasurables = subcompose(SubcomposeSlot.NavigationBar) {
                    Box {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                val isSelected = when (item.route) {
                                    Home -> navBackStackEntry?.destination?.route?.contains("Home") == true
                                    Search -> navBackStackEntry?.destination?.route?.contains("Search") == true
                                    Library -> navBackStackEntry?.destination?.route?.contains("Library") == true
                                    Viper -> navBackStackEntry?.destination?.route?.contains("Viper") == true
                                    else -> false
                                }

                                NavigationBarItem(
                                    icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
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

                val miniPlayerMeasurables = subcompose(SubcomposeSlot.MiniPlayer) {
                    MiniPlayer(
                        onMiniPlayerClick = { showPlayerBottomSheet = true },
                    )
                }

                val layoutBottom = constraints.maxHeight

                val navBarPlaceable = navBarMeasurables.first().measure(
                    constraints.copy(minHeight = 0)
                )

                val navBarHeight = navBarPlaceable.height
                val isNavBarVisible = navBarY.value < layoutBottom

                val navBarTargetY = if (layoutState.showBottomNavBar) {
                    layoutBottom - navBarHeight
                } else {
                    layoutBottom // hidden below screen
                }
                if (navBarY.targetValue != navBarTargetY) {
                    scope.launch {
                        if (navBarY.targetValue == -1) {
                            navBarY.snapTo(navBarTargetY)
                        } else {
                            navBarY.animateTo(navBarTargetY)
                        }
                    }
                }

                val miniPlayerPlaceable = miniPlayerMeasurables.first().measure(
                    constraints.copy(minHeight = 0)
                )

                val miniPlayerHeight = miniPlayerPlaceable.height
                val isMiniPlayerVisible =
                    if (isNavBarVisible) miniPlayerY.value < navBarY.value
                    else miniPlayerY.value < layoutBottom

                val miniPlayerTargetY = if (layoutState.showMiniPlayer) {
                    // Sit above nav bar if visible, otherwise above inset
                    val anchor = if (layoutState.showBottomNavBar) navBarTargetY else layoutBottom - bottomInset
                    anchor - miniPlayerHeight
                } else {
                    layoutBottom // hidden below screen
                }
                if (miniPlayerY.targetValue != miniPlayerTargetY) {
                    scope.launch {
                        if (miniPlayerY.targetValue == -1) {
                            miniPlayerY.snapTo(miniPlayerTargetY)
                        } else {
                            miniPlayerY.animateTo(miniPlayerTargetY)
                        }
                    }
                }

                val topSurfaceForPadding = minOf(
                    miniPlayerY.value,
                    navBarY.value,
                    layoutBottom - bottomInset
                )

                val bottomPadding = (layoutBottom - topSurfaceForPadding).toDp()

                val contentMeasurables = subcompose(SubcomposeSlot.Content) {
                    Box {
                        ViperNavHost(
                            navController = navController,
                            rootPadding = PaddingValues(bottom = bottomPadding),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                val contentPlaceable = contentMeasurables.first().measure(constraints)

                layout(constraints.maxWidth, constraints.maxHeight) {
                    contentPlaceable.place(0, 0)

                    if (isMiniPlayerVisible) {
                        val miniPlayerYValue = if (miniPlayerY.value == -1) {
                            miniPlayerTargetY
                        } else {
                            miniPlayerY.value
                        }

                        miniPlayerPlaceable.place(0, miniPlayerYValue)
                    }

                    if (isNavBarVisible) {
                        val navBarYValue = if (navBarY.value == -1) {
                            navBarTargetY
                        } else {
                            navBarY.value
                        }

                        navBarPlaceable.place(0, navBarYValue)
                    }
                }
            }

            if (showPlayerBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showPlayerBottomSheet = false
                    },
                    sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true
                    ),
                    dragHandle = null,
                    contentWindowInsets = { WindowInsets() }
                ) {
                    PlayerScreen()
                }
            }
        }
    }
}
