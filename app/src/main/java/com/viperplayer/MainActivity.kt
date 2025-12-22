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
import androidx.navigation.compose.*
import com.viperplayer.presentation.navigation.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViperPlayerMainScreen() {
    val navController = rememberNavController()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val bottomNavItems = listOf(
        BottomNavItem(Home, "Home", Icons.Default.Home),
        BottomNavItem(Search, "Search", Icons.Default.Search),
        BottomNavItem(Library, "Library", Icons.Default.LibraryMusic),
        BottomNavItem(Plugins, "Plugins", Icons.Default.Extension)
    )
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
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
    ) { innerPadding ->
        ViperNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
