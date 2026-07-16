package com.viperplayer.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.viperplayer.alarm.ui.AlarmsScreen
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.player.PlayerScreen
import com.viperplayer.presentation.analytics.AnalyticsScreen
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.AlbumDetailViewModel
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailViewModel
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailViewModel
import com.viperplayer.presentation.detail.SongInfoScreen
import com.viperplayer.presentation.detail.SongInfoViewModel
import com.viperplayer.presentation.detail.TagDetailsScreen
import com.viperplayer.presentation.detail.TagDetailsViewModel
import com.viperplayer.follows.ui.FollowingScreen
import com.viperplayer.presentation.downloads.DownloadsScreen
import com.viperplayer.presentation.history.HistoryScreen
import com.viperplayer.presentation.home.HomeScreen
import com.viperplayer.presentation.social.JoinSessionScreen
import com.viperplayer.presentation.library.LibraryScreen
import com.viperplayer.presentation.listeningstats.ListeningStatsScreen
import com.viperplayer.presentation.listeningstats.WrappedScreen
import com.viperplayer.presentation.plugins.PluginsScreen
import com.viperplayer.presentation.search.SearchScreen
import com.viperplayer.presentation.settings.SettingsScreen
import com.viperplayer.presentation.settings.about.AboutSettingsScreen
import com.viperplayer.presentation.settings.appearance.AppearanceSettingsScreen
import com.viperplayer.presentation.settings.content.ContentSettingsScreen
import com.viperplayer.presentation.settings.lastfm.LastfmSettingsScreen
import com.viperplayer.presentation.settings.lyrics.LyricsSettingsScreen
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
object SettingsLyrics : NavKey

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
object SettingsAlarms : NavKey

@Serializable
object SettingsLastfm : NavKey

@Serializable
object History : NavKey

@Serializable
object Analytics : NavKey

@Serializable
object ListeningStats : NavKey

@Serializable
object Wrapped : NavKey

@Serializable
object Downloads : NavKey

/** Followed / subscribed artists list. */
@Serializable
object Following : NavKey

@Serializable
data class AlbumDetail(
    val albumId: MediaId,
    val initialName: String = "",
    val initialArtworkUrl: String? = null,
) : NavKey

@Serializable
data class ArtistDetail(
    val artistId: MediaId,
    val initialName: String = "",
    val initialImageUrl: String? = null,
) : NavKey

@Serializable
data class PlaylistDetail(
    val playlistId: MediaId,
    val initialName: String = "",
    val initialArtworkUrl: String? = null,
) : NavKey

@Serializable
data class SongInfo(
    val mediaId: MediaId,
    val initialTitle: String = "",
    val initialArtist: String = "",
    val initialArtworkUrl: String? = null,
) : NavKey

/** Full tag / metadata detail viewer for a local file (reached from [SongInfo]). Local songs only. */
@Serializable
data class TagDetails(
    val mediaId: MediaId,
    val initialTitle: String = "",
) : NavKey

@Serializable
object JoinSession : NavKey

/** The full player. Only ever hosted inside the player bottom sheet's own nested nav stack. */
@Serializable
object Player : NavKey

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
                    navigator.navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist.id, playlist.name, playlist.artworkUrl))
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
                    navigator.navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist.id, playlist.name, playlist.artworkUrl))
                }
            )
        }

        entry<Library> {
            LibraryScreen(
                rootPadding = rootPadding,
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist.id, playlist.name, playlist.artworkUrl))
                },
                onNavigateToDownloads = { navigator.navigate(Downloads) },
                onNavigateToFollowing = { navigator.navigate(Following) }
            )
        }

        entry<Following> {
            FollowingScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
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
                onNavigateToLyrics = { navigator.navigate(SettingsLyrics) },
                onNavigateToPlayer = { navigator.navigate(SettingsPlayer) },
                onNavigateToContent = { navigator.navigate(SettingsContent) },
                onNavigateToStorage = { navigator.navigate(SettingsStorage) },
                onNavigateToPlugins = { navigator.navigate(Plugins) },
                onNavigateToAbout = { navigator.navigate(SettingsAbout) },
                onNavigateToUpdater = { navigator.navigate(SettingsUpdater) },
                onNavigateToAlarms = { navigator.navigate(SettingsAlarms) },
                onNavigateToListeningStats = { navigator.navigate(ListeningStats) },
                onNavigateToLastfm = { navigator.navigate(SettingsLastfm) }
            )
        }

        entry<SettingsLastfm> {
            LastfmSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsAlarms> {
            AlarmsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsAppearance> {
            AppearanceSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<SettingsLyrics> {
            LyricsSettingsScreen(
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

        entry<ListeningStats> {
            ListeningStatsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToWrapped = { navigator.navigate(Wrapped) }
            )
        }

        entry<Wrapped> {
            WrappedScreen(
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Downloads> {
            DownloadsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        mediaDetailEntries(
            rootPadding = rootPadding,
            navigate = navigator::navigate,
            goBack = navigator::goBack,
        )
    }

    val entries = navigationState.toEntries(entryProvider)

    NavDisplay(
        entries = entries,
        onBack = { navigator.goBack() },
        modifier = modifier,
        transitionSpec = {
            val topLevelOrder = listOf(Home, Search, Library, Viper)
            val initialIndex = topLevelOrder.indexOf(initialState.key)
            val targetIndex = topLevelOrder.indexOf(targetState.key)

            if (initialIndex != -1 && targetIndex != -1) {
                // Lateral switch between top-level tabs: a gentle directional slide + crossfade, with
                // far less travel than a hierarchical push so switching tabs doesn't feel "deep".
                val goingRight = targetIndex > initialIndex
                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { w ->
                    if (goingRight) w / 6 else -w / 6
                }) togetherWith (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { w ->
                    if (goingRight) -w / 6 else w / 6
                })
            } else {
                // Hierarchical push (e.g. into a detail screen): full slide in from the Start.
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                        fadeIn(tween(300)) togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                        fadeOut(tween(300))
            }
        },
        popTransitionSpec = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300)
            ) + fadeIn(tween(300)) togetherWith
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300)
                    ) + fadeOut(tween(300))
        },
        predictivePopTransitionSpec = {
            // A subtle predictive back animation that slides the exiting screen
            // out slowly alongside the gesture, without the extreme scaling.
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300)
            ) + fadeIn(tween(300)) togetherWith
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300)
                    ) + fadeOut(tween(300))
        }
    )
}

/**
 * Registers the media-detail destinations (album / artist / playlist / song-info / join-session)
 * shared by the main nav display and the player sheet's nested nav. [navigate] pushes a destination
 * and [goBack] pops the current one — each host supplies its own back stack.
 */
fun EntryProviderScope<NavKey>.mediaDetailEntries(
    rootPadding: PaddingValues,
    navigate: (NavKey) -> Unit,
    goBack: () -> Unit,
) {
    entry<AlbumDetail> { key ->
        val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) }
        )
        AlbumDetailScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onNavigateToArtist = { artist ->
                navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
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
            onNavigateBack = goBack,
            onNavigateToAlbum = { album ->
                navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
            },
            onNavigateToPlaylist = { playlist ->
                navigate(PlaylistDetail(playlist.id, playlist.name, playlist.artworkUrl))
            },
            onNavigateToArtist = { artist ->
                navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
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
            onNavigateBack = goBack,
            onNavigateToArtist = { artist ->
                navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
            },
            onNavigateToAlbum = { album ->
                navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
            },
            viewModel = viewModel
        )
    }

    entry<SongInfo> { key ->
        val viewModel = hiltViewModel<SongInfoViewModel, SongInfoViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) }
        )
        SongInfoScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onNavigateToArtist = { artist ->
                navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
            },
            onNavigateToAlbum = { album ->
                navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
            },
            onNavigateToTagDetails = { mediaId, title ->
                navigate(TagDetails(mediaId = mediaId, initialTitle = title))
            },
            viewModel = viewModel
        )
    }

    entry<TagDetails> { key ->
        val viewModel = hiltViewModel<TagDetailsViewModel, TagDetailsViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) }
        )
        TagDetailsScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            viewModel = viewModel,
        )
    }

    entry<JoinSession> {
        JoinSessionScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
        )
    }
}

/**
 * The player bottom sheet's content: a self-contained nav stack rooted at [Player]. Tapping an
 * artist / album / song-info pushes onto THIS stack, so the detail screen renders inside the sheet
 * (over the player) rather than dismissing it — no more disappear/reappear. System back pops the
 * nested stack; once at the root, [NavDisplay] stops consuming back and the enclosing
 * `ModalBottomSheet` dismisses natively (slide-down). [onDismiss] collapses the sheet.
 */
@Composable
fun PlayerBottomSheetNavHost(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Player)
    val navigate: (NavKey) -> Unit = { backStack.add(it) }
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    val entryProvider = entryProvider {
        entry<Player> {
            PlayerScreen(
                onNavigateToArtist = { artist ->
                    navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
                },
                onNavigateToAlbum = { album ->
                    navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
                },
                onNavigateToSongInfo = { song ->
                    navigate(
                        SongInfo(
                            mediaId = song.id,
                            initialTitle = song.title,
                            initialArtist = song.artistNames.orEmpty(),
                            initialArtworkUrl = song.artworkUrl,
                        )
                    )
                },
                onNavigateToJoinSession = { navigate(JoinSession) },
                onCollapse = onDismiss,
            )
        }
        mediaDetailEntries(rootPadding = PaddingValues(), navigate = navigate, goBack = goBack)
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        ),
        entryProvider = entryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = goBack,
        modifier = modifier,
        transitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                    fadeIn(tween(300)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
                    fadeOut(tween(300))
        },
        popTransitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                    fadeIn(tween(300)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                    fadeOut(tween(300))
        },
        predictivePopTransitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                    fadeIn(tween(300)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
                    fadeOut(tween(300))
        },
    )
}
