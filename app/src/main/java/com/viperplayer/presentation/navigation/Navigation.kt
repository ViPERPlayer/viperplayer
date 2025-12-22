package com.viperplayer.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.home.HomeScreen
import com.viperplayer.presentation.library.LibraryScreen
import com.viperplayer.presentation.plugins.PluginsScreen
import com.viperplayer.presentation.search.SearchScreen
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
object Plugins

@Serializable
object NowPlaying

@Serializable
data class AlbumDetail(val albumId: String)

@Serializable
data class ArtistDetail(val artistId: String)

@Serializable
data class PlaylistDetail(val playlistId: String)

@Composable
fun ViperNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId))
                }
            )
        }
        
        composable<Search> {
            SearchScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId))
                }
            )
        }
        
        composable<Library> {
            LibraryScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(PlaylistDetail(playlistId))
                }
            )
        }
        
        composable<Plugins> {
            PluginsScreen()
        }
        
        composable<AlbumDetail> {
            AlbumDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArtist = { artistId ->
                    navController.navigate(ArtistDetail(artistId))
                }
            )
        }
        
        composable<ArtistDetail> {
            ArtistDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(AlbumDetail(albumId))
                }
            )
        }
        
        composable<PlaylistDetail> {
            PlaylistDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

