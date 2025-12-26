package com.viperplayer.presentation

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.presentation.common.MiniPlayer
import com.viperplayer.presentation.common.determineLayoutVisibility
import com.viperplayer.presentation.navigation.Home
import com.viperplayer.presentation.navigation.Library
import com.viperplayer.presentation.navigation.NowPlaying
import com.viperplayer.presentation.navigation.Plugins
import com.viperplayer.presentation.navigation.Search
import com.viperplayer.presentation.navigation.ViperNavHost
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViperPlayerAppUiState(
    val color: Color = Color.Unspecified
)

@HiltViewModel
class ViperPlayerAppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ViperPlayerAppUiState())
    val uiState = _uiState.asStateFlow()

    private val _dynamicThemeColor = MutableStateFlow<Color?>(null)
    val dynamicThemeColor = _dynamicThemeColor.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    init {
        observeThemeColor()
    }

    private fun observeThemeColor() {
        viewModelScope.launch {
            playerState.collect { playerState ->
                val themeColor = playerState.currentSong?.artworkUrl?.let { artworkUrl ->
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .build()
                    )
                    result.image?.toBitmap()?.asImageBitmap()?.themeColorOrNull()
                }

                _dynamicThemeColor.update { themeColor }
            }
        }
    }

    fun togglePlayPause() {
        TODO("Not yet implemented")
    }
}

@Composable
fun ViperPlayerApp(
    viewModel: ViperPlayerAppViewModel = hiltViewModel()
) {
    val themeColor by viewModel.dynamicThemeColor.collectAsStateWithLifecycle()

    ViPERPlayerTheme(
        seedColor = themeColor
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
            val playerState by viewModel.playerState.collectAsState()

            val bottomNavItems = listOf(
                BottomNavItem(Home, "Home", Icons.Default.Home),
                BottomNavItem(Search, "Search", Icons.Default.Search),
                BottomNavItem(Library, "Library", Icons.Default.LibraryMusic),
                BottomNavItem(Plugins, "Plugins", Icons.Default.Extension)
            )

            val layoutState = determineLayoutVisibility(
                currentDestination = navBackStackEntry,
                hasPlayingContent = playerState.hasContent,
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
                                    Plugins -> navBackStackEntry?.destination?.route?.contains("Plugins") == true
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
                        playerState = playerState,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onMiniPlayerClick = { navController.navigate(NowPlaying) },
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
        }
    }
}

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
