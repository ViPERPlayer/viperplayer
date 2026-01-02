package com.viperplayer.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.home.HomeScreen
import com.viperplayer.presentation.library.LibraryScreen
import com.viperplayer.presentation.plugins.PluginsScreen
import com.viperplayer.presentation.search.SearchScreen
import com.viperplayer.presentation.settings.SettingsScreen
import com.viperplayer.presentation.viper.ViperScreen
import kotlinx.serialization.Serializable

/**
 * Navigation destinations using type-safe navigation.
 */
@Serializable
object Home

@Serializable
object Search

@Serializable
object Library

@Serializable
object Viper

@Serializable
object Plugins

@Serializable
object Settings

@Serializable
data class AlbumDetail(
    val pluginId: String,
    val sourceId: String
)

@Serializable
data class ArtistDetail(
    val pluginId: String,
    val sourceId: String
)

@Serializable
data class PlaylistDetail(
    val pluginId: String,
    val sourceId: String
)

@Composable
fun ViperNavHost(
    navController: NavHostController,
    rootPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                }
            )
        }
        
        composable<Search> {
            SearchScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                }
            )
        }
        
        composable<Library> {
            LibraryScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                }
            )
        }

        composable<Viper> {
            ViperScreen(
                rootPadding = rootPadding
            )
        }
        
        composable<Plugins> {
            PluginsScreen(
                rootPadding = rootPadding
            )
        }
        
        composable<Settings> {
            SettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<AlbumDetail> {
            AlbumDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId.pluginId, artistId.sourceId))
                }
            )
        }
        
        composable<ArtistDetail> {
            ArtistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId.pluginId, albumId.sourceId))
                }
            )
        }
        
        composable<PlaylistDetail> {
            PlaylistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

