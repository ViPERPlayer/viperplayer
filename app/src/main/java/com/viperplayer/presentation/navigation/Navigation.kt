package com.viperplayer.presentation.navigation

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.BundleCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
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
import kotlin.reflect.typeOf

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
    val initialAlbum: Album
)

@Serializable
data class ArtistDetail(
    val initialArtist: Artist
)

@Serializable
data class PlaylistDetail(
    val initialPlaylist: Playlist
)

val AlbumNavType = object : NavType<Album>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): Album? =
        BundleCompat.getParcelable(bundle, key, Album::class.java)

    override fun parseValue(value: String): Album =
        Album(MediaId.fromString(Uri.decode(value)), "")

    override fun serializeAsValue(value: Album): String =
        Uri.encode(value.id.toString())

    override fun put(bundle: Bundle, key: String, value: Album) {
        bundle.putParcelable(key, value)
    }
}

val ArtistNavType = object : NavType<Artist>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): Artist? =
        BundleCompat.getParcelable(bundle, key, Artist::class.java)

    override fun parseValue(value: String): Artist =
        Artist(MediaId.fromString(Uri.decode(value)), "")

    override fun serializeAsValue(value: Artist): String =
        Uri.encode(value.id.toString())

    override fun put(bundle: Bundle, key: String, value: Artist) {
        bundle.putParcelable(key, value)
    }
}

val PlaylistNavType = object : NavType<Playlist>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): Playlist? =
        BundleCompat.getParcelable(bundle, key, Playlist::class.java)

    override fun parseValue(value: String): Playlist =
        Playlist(MediaId.fromString(Uri.decode(value)), "")

    override fun serializeAsValue(value: Playlist): String =
        Uri.encode(value.id.toString())

    override fun put(bundle: Bundle, key: String, value: Playlist) {
        bundle.putParcelable(key, value)
    }
}

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
                onNavigateToAlbum = { album ->
                    navController.navigateSafe(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navController.navigateSafe(PlaylistDetail(playlist))
                },
                onNavigateToSettings = { navController.navigateSafe(Settings) },
                onNavigateToHistory = { navController.navigateSafe(History) },
                onNavigateToAnalytics = { navController.navigateSafe(Analytics) }
            )
        }
        
        composable<Search> {
            SearchScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navController.navigateSafe(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navController.navigateSafe(PlaylistDetail(playlist))
                }
            )
        }
        
        composable<Library> {
            LibraryScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navController.navigateSafe(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navController.navigateSafe(PlaylistDetail(playlist))
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

        composable<AlbumDetail>(
            typeMap = mapOf(typeOf<Album>() to AlbumNavType)
        ) {
            AlbumDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                }
            )
        }
        
        composable<ArtistDetail>(
            typeMap = mapOf(typeOf<Artist>() to ArtistNavType)
        ) {
            ArtistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToAlbum = { album ->
                    navController.navigateSafe(AlbumDetail(album))
                },
                onNavigateToPlaylist = { playlist ->
                    navController.navigateSafe(PlaylistDetail(playlist))
                },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                }
            )
        }
        
        composable<PlaylistDetail>(
            typeMap = mapOf(typeOf<Playlist>() to PlaylistNavType)
        ) {
            PlaylistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navController.popBackStackSafe() },
                onNavigateToArtist = { artist ->
                    navController.navigateSafe(ArtistDetail(artist))
                },
                onNavigateToAlbum = { album ->
                    navController.navigateSafe(AlbumDetail(album))
                }
            )
        }
    }
}

