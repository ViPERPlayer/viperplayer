package com.viperplayer.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.presentation.analytics.AnalyticsScreen
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.AlbumDetailViewModel
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailViewModel
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailViewModel
import com.viperplayer.presentation.history.HistoryScreen
import com.viperplayer.presentation.home.HomeScreen
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
object Home : NavKey

@Serializable
object Search : NavKey

@Serializable
object Library : NavKey

@Serializable
object Viper : NavKey

@Serializable
object Plugins : NavKey

@Serializable
object Settings : NavKey

@Serializable
object SettingsAppearance : NavKey

@Serializable
object SettingsPlayer : NavKey

@Serializable
object SettingsContent : NavKey

@Serializable
object SettingsStorage : NavKey

@Serializable
object SettingsAbout : NavKey

@Serializable
object SettingsUpdater : NavKey

@Serializable
object History : NavKey

@Serializable
object Analytics : NavKey

@Serializable
data class AlbumDetail(
    val initialAlbum: Album
) : NavKey

@Serializable
data class ArtistDetail(
    val initialArtist: Artist
) : NavKey

@Serializable
data class PlaylistDetail(
    val initialPlaylist: Playlist
) : NavKey

@Composable
fun ViperNavDisplay(
    navigationState: NavigationState,
    navigator: Navigator,
    rootPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val entryProvider = entryProvider {
        entry<Home> {
            HomeScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist))
                },
                onNavigateToSettings = { navigator.navigate(Settings) },
                onNavigateToHistory = { navigator.navigate(History) },
                onNavigateToAnalytics = { navigator.navigate(Analytics) }
            )
        }

        entry<Search> {
            SearchScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist))
                }
            )
        }

        entry<Library> {
            LibraryScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist))
                }
            )
        }

        entry<Viper> {
            ViperScreen(
                rootPadding = rootPadding
            )
        }

        entry<Plugins> {
            PluginsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Settings> {
            SettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToAppearance = { navigator.navigate(SettingsAppearance) },
                onNavigateToPlayer = { navigator.navigate(SettingsPlayer) },
                onNavigateToContent = { navigator.navigate(SettingsContent) },
                onNavigateToStorage = { navigator.navigate(SettingsStorage) },
                onNavigateToPlugins = { navigator.navigate(Plugins) },
                onNavigateToAbout = { navigator.navigate(SettingsAbout) },
                onNavigateToUpdater = { navigator.navigate(SettingsUpdater) }
            )
        }

        entry<SettingsAppearance> {
            AppearanceSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsPlayer> {
            PlayerSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsContent> {
            ContentSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsStorage> {
            StorageSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsAbout> {
            AboutSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsUpdater> {
            UpdaterSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<History> {
            HistoryScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Analytics> {
            AnalyticsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<AlbumDetail> { key ->
            val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            AlbumDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                viewModel = viewModel
            )
        }

        entry<ArtistDetail> { key ->
            val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            ArtistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                viewModel = viewModel
            )
        }

        entry<PlaylistDetail> { key ->
            val viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            PlaylistDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist))
                },
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album))
                },
                viewModel = viewModel
            )
        }
    }

    val entries = navigationState.toEntries(entryProvider)

    NavDisplay(
        entries = entries,
        onBack = { navigator.goBack() },
        modifier = modifier
    )
}
