package com.viperplayer.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.viperplayer.presentation.analytics.AnalyticsScreen
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.history.HistoryScreen
import com.viperplayer.presentation.home.HomeScreen
import com.viperplayer.presentation.ktx.navigateSafe
import com.viperplayer.presentation.ktx.popBackStackSafe
import com.viperplayer.presentation.library.LibraryScreen
import com.viperplayer.presentation.plugins.PluginsScreen
import com.viperplayer.presentation.search.SearchScreen
import com.viperplayer.presentation.settings.SettingsScreen
import com.viperplayer.presentation.settings.about.AboutSettingsScreen
import com.viperplayer.presentation.settings.appearance.AppearanceSettingsScreen
import com.viperplayer.presentation.settings.content.ContentSettingsScreen
import com.viperplayer.presentation.settings.player.PlayerSettingsScreen
import com.viperplayer.presentation.settings.storage.StorageSettingsScreen
import com.viperplayer.presentation.settings.updater.UpdaterSettingsScreen
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
object History

@Serializable
object Analytics

@Serializable
object SettingsAppearance

@Serializable
object SettingsPlayer

@Serializable
object SettingsContent

@Serializable
object SettingsStorage

@Serializable
object SettingsAbout

@Serializable
object SettingsUpdater

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
                    navController.navigateSafe(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigateSafe(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigateSafe(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                },
                onNavigateToSettings = { navController.navigateSafe(Settings) },
                onNavigateToHistory = { navController.navigateSafe(History) },
                onNavigateToAnalytics = { navController.navigateSafe(Analytics) }
            )
        }
        
        composable<Search> {
            SearchScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { albumId ->
                    navController.navigateSafe(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigateSafe(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigateSafe(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                }
            )
        }
        
        composable<Library> {
            LibraryScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { albumId ->
                    navController.navigateSafe(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigateSafe(ArtistDetail(artistId.pluginId, artistId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigateSafe(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
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
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<Settings> {
            SettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToAppearance = { navController.navigateSafe(SettingsAppearance) },
                onNavigateToPlayer = { navController.navigateSafe(SettingsPlayer) },
                onNavigateToContent = { navController.navigateSafe(SettingsContent) },
                onNavigateToStorage = { navController.navigateSafe(SettingsStorage) },
                onNavigateToPlugins = { navController.navigateSafe(Plugins) },
                onNavigateToAbout = { navController.navigateSafe(SettingsAbout) },
                onNavigateToUpdater = { navController.navigateSafe(SettingsUpdater) }
            )
        }
        
        composable<SettingsAppearance> {
            AppearanceSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<SettingsPlayer> {
            PlayerSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<SettingsContent> {
            ContentSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<SettingsStorage> {
            StorageSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<SettingsAbout> {
            AboutSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<SettingsUpdater> {
            UpdaterSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<History> {
            HistoryScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        
        composable<Analytics> {
            AnalyticsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }

        composable<AlbumDetail> {
            AlbumDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToArtist = { artistId ->
                    navController.navigateSafe(ArtistDetail(artistId.pluginId, artistId.sourceId))
                }
            )
        }
        
        composable<ArtistDetail> {
            ArtistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToAlbum = { albumId ->
                    navController.navigateSafe(AlbumDetail(albumId.pluginId, albumId.sourceId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigateSafe(PlaylistDetail(playlistId.pluginId, playlistId.sourceId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigateSafe(ArtistDetail(artistId.pluginId, artistId.sourceId))
                }
            )
        }
        
        composable<PlaylistDetail> {
            PlaylistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
    }
}

