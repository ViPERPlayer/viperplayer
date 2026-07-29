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
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.player.PlayerScreen
import com.viperplayer.presentation.analytics.AnalyticsScreen
import com.viperplayer.presentation.detail.AlbumDetailScreen
import com.viperplayer.presentation.detail.AlbumDetailViewModel
import com.viperplayer.presentation.detail.CategoryContentsScreen
import com.viperplayer.presentation.detail.CategoryContentsViewModel
import com.viperplayer.presentation.detail.ArtistDetailScreen
import com.viperplayer.presentation.detail.ArtistDetailViewModel
import com.viperplayer.presentation.detail.GenreDetailScreen
import com.viperplayer.presentation.detail.GenreDetailViewModel
import com.viperplayer.presentation.detail.PlaylistDetailScreen
import com.viperplayer.presentation.detail.PlaylistDetailViewModel
import com.viperplayer.presentation.detail.SongInfoScreen
import com.viperplayer.presentation.detail.SongInfoViewModel
import com.viperplayer.presentation.detail.TagDetailsScreen
import com.viperplayer.presentation.detail.TagDetailsViewModel
import com.viperplayer.presentation.rec.DiscoverScreen
import com.viperplayer.presentation.rec.ForYouScreen
import com.viperplayer.presentation.rec.SimilarSongsScreen
import com.viperplayer.presentation.rec.SimilarSongsViewModel
import com.viperplayer.follows.ui.FollowingScreen
import com.viperplayer.presentation.downloads.DownloadsScreen
import com.viperplayer.presentation.history.HistoryScreen
import com.viperplayer.presentation.home.HomeScreen
import com.viperplayer.presentation.social.FindPeopleScreen
import com.viperplayer.presentation.social.FollowingUsersScreen
import com.viperplayer.presentation.social.FriendActivityScreen
import com.viperplayer.presentation.social.HostSessionScreen
import com.viperplayer.presentation.social.JoinSessionScreen
import com.viperplayer.presentation.social.JoinSessionViewModel
import com.viperplayer.presentation.social.SharedWithYouScreen
import com.viperplayer.presentation.you.YouScreen
import com.viperplayer.presentation.library.CustomizeTabsScreen
import com.viperplayer.presentation.library.LibraryScreen
import com.viperplayer.presentation.listeningstats.ListeningStatsScreen
import com.viperplayer.presentation.listeningstats.WrappedScreen
import com.viperplayer.presentation.plugins.PluginsScreen
import com.viperplayer.presentation.search.SearchScreen
import com.viperplayer.presentation.settings.SettingsScreen
import com.viperplayer.presentation.settings.about.AboutSettingsScreen
import com.viperplayer.presentation.settings.appearance.AppearanceSettingsScreen
import com.viperplayer.presentation.settings.content.ContentSettingsScreen
import com.viperplayer.presentation.account.AccountScreen
import com.viperplayer.presentation.account.RegisterScreen
import com.viperplayer.presentation.account.SignInScreen
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

/** The signed-in account detail (reached from the You hub's "Manage account"). */
@Serializable
object Account : NavKey

/** The "You" hub: account, sync, social, plugins, and setup in one place (from the Home avatar). */
@Serializable
object You : NavKey

/** Sign-in form. */
@Serializable
object SignIn : NavKey

/** Register form. */
@Serializable
object Register : NavKey

/** Friend-activity feed (also the Home bell's notifications destination). Placeholder in Phase 1. */
@Serializable
object FriendActivity : NavKey

/** Shared-with-you inbox. Placeholder in Phase 1. */
@Serializable
object SharedWithYou : NavKey

/** "Find people": search public users by @handle and follow them (social S1). */
@Serializable
object FindPeople : NavKey

/**
 * The users the signed-in account follows (social S1). Named distinctly from [Following] (which is the
 * ARTISTS list) to avoid a collision — this is the followed-*people* graph.
 */
@Serializable
object FollowingUsers : NavKey

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

/** Library tabs customization: reorder + show/hide the Library's tabs. */
@Serializable
object CustomizeTabs : NavKey

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

/** A local library genre's song list (reached from the Library's Genres tab). Keyed by the genre row id. */
@Serializable
data class GenreDetail(
    val genreId: Long,
    val initialName: String = "",
) : NavKey

/**
 * A browse category's contents (reached from Home's "Browse categories" tiles). Carries the owning
 * [pluginId] + the plugin's own [categoryId] so [PluginRepository.getCategoryContents] can fetch the
 * page, plus the [name] to title the screen before anything loads.
 */
@Serializable
data class CategoryDetail(
    val pluginId: String,
    val categoryId: String,
    val name: String = "",
) : NavKey

@Serializable
data class SongInfo(
    val mediaId: MediaId,
    val initialTitle: String = "",
    val initialArtist: String = "",
    val initialArtworkUrl: String? = null,
) : NavKey

/**
 * "More like this" — the ranked similar songs for a seed track, from the on-device audio-embedding kNN
 * (with a plugin related-songs fallback). Carries the seed's [seedTitle]/[seedArtworkUrl] so the header
 * renders before results load.
 */
@Serializable
data class SimilarSongs(
    val seedMediaId: MediaId,
    val seedTitle: String = "",
    val seedArtworkUrl: String? = null,
) : NavKey

/** "For You" — a taste-based mix built from the local library (reached from the You hub). */
@Serializable
object ForYou : NavKey

/** "Discover" — server-backed recommendations of songs the user doesn't own (reached from the You hub). */
@Serializable
object Discover : NavKey

/** Full tag / metadata detail viewer for a local file (reached from [SongInfo]). Local songs only. */
@Serializable
data class TagDetails(
    val mediaId: MediaId,
    val initialTitle: String = "",
) : NavKey

/**
 * "Join a Jam" — the guest join flow. [prefillCode] seeds the manual code field (e.g. when arriving
 * from a friend's "Join" affordance); null means an empty field (the normal scan/type entry).
 */
@Serializable
data class JoinSession(val prefillCode: String? = null) : NavKey

/** "Host a Jam" sheet — start hosting a listen-together session (reached from [JoinSession]). */
@Serializable
object HostSession : NavKey

/** The full player. Only ever hosted inside the player bottom sheet's own nested nav stack. */
@Serializable
object Player : NavKey

/**
 * Maps a media item's "Details" action to its detail destination and pushes it via [navigate].
 * Defined once here so all option-sheet hosts (Album / Artist / Category / Genre / Playlist /
 * Library / Search screens) share a single mapping instead of duplicating the `when` per screen:
 *  - [Song] → [SongInfo] (the song-info / tag detail screen)
 *  - [Album] → [AlbumDetail], [Artist] → [ArtistDetail], [Playlist] → [PlaylistDetail]
 * mirroring the existing onNavigateToAlbum/Artist/Playlist keys those screens already use.
 */
/**
 * Pushes the "More like this" (similar songs) destination for [song] via [navigate]. Defined here so
 * every options-sheet host maps the action identically instead of duplicating the [SimilarSongs] key.
 */
fun navigateToSimilarSongs(navigate: (NavKey) -> Unit, song: Song) {
    navigate(SimilarSongs(song.id, song.title, song.artworkUrl))
}

fun navigateToMediaItemDetails(navigate: (NavKey) -> Unit, item: MediaItem) {
    when (item) {
        is Song -> navigate(
            SongInfo(item.id, item.title, item.artistNames.orEmpty(), item.artworkUrl)
        )
        is Album -> navigate(AlbumDetail(item.id, item.name, item.artworkUrl))
        is Artist -> navigate(ArtistDetail(item.id, item.name, item.imageUrl))
        is Playlist -> navigate(PlaylistDetail(item.id, item.name, item.artworkUrl))
    }
}

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
                onNavigateToCategory = { category ->
                    navigator.navigate(CategoryDetail(category.pluginId, category.id, category.name))
                },
                onNavigateToYou = { navigator.navigate(You) },
                onNavigateToNotifications = { navigator.navigate(FriendActivity) },
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
                },
                onViewDetails = { item -> navigateToMediaItemDetails(navigator::navigate, item) },
                onMoreLikeThis = { song -> navigateToSimilarSongs(navigator::navigate, song) },
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
                onNavigateToGenre = { genre ->
                    navigator.navigate(GenreDetail(genre.id, genre.name))
                },
                onNavigateToDownloads = { navigator.navigate(Downloads) },
                onNavigateToFollowing = { navigator.navigate(Following) },
                onNavigateToCustomizeTabs = { navigator.navigate(CustomizeTabs) },
                onNavigateToSearch = { navigator.navigate(Search) },
                onViewDetails = { item -> navigateToMediaItemDetails(navigator::navigate, item) },
                onMoreLikeThis = { song -> navigateToSimilarSongs(navigator::navigate, song) },
            )
        }

        entry<GenreDetail> { key ->
            val viewModel = hiltViewModel<GenreDetailViewModel, GenreDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            GenreDetailScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onViewDetails = { item -> navigateToMediaItemDetails(navigator::navigate, item) },
                onMoreLikeThis = { song -> navigateToSimilarSongs(navigator::navigate, song) },
                viewModel = viewModel,
            )
        }

        entry<CategoryDetail> { key ->
            val viewModel = hiltViewModel<CategoryContentsViewModel, CategoryContentsViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            CategoryContentsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToAlbum = { album ->
                    navigator.navigate(AlbumDetail(album.id, album.name, album.artworkUrl))
                },
                onNavigateToArtist = { artist ->
                    navigator.navigate(ArtistDetail(artist.id, artist.name, artist.imageUrl))
                },
                onNavigateToPlaylist = { playlist ->
                    navigator.navigate(PlaylistDetail(playlist.id, playlist.name, playlist.artworkUrl))
                },
                onViewDetails = { item -> navigateToMediaItemDetails(navigator::navigate, item) },
                onMoreLikeThis = { song -> navigateToSimilarSongs(navigator::navigate, song) },
                viewModel = viewModel,
            )
        }

        entry<CustomizeTabs> {
            CustomizeTabsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
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
                onNavigateToAbout = { navigator.navigate(SettingsAbout) },
                onNavigateToUpdater = { navigator.navigate(SettingsUpdater) },
                onNavigateToAlarms = { navigator.navigate(SettingsAlarms) },
            )
        }

        entry<You> {
            YouScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToSettings = { navigator.navigate(Settings) },
                onNavigateToAccount = { navigator.navigate(Account) },
                onNavigateToSignIn = { navigator.navigate(SignIn) },
                onNavigateToRegister = { navigator.navigate(Register) },
                onNavigateToFriendActivity = { navigator.navigate(FriendActivity) },
                onNavigateToSharedWithYou = { navigator.navigate(SharedWithYou) },
                onNavigateToFollowingUsers = { navigator.navigate(FollowingUsers) },
                onNavigateToJoinSession = { navigator.navigate(JoinSession()) },
                onNavigateToPlugins = { navigator.navigate(Plugins) },
                onNavigateToHistory = { navigator.navigate(History) },
                onNavigateToListeningStats = { navigator.navigate(ListeningStats) },
                onNavigateToForYou = { navigator.navigate(ForYou) },
                onNavigateToDiscover = { navigator.navigate(Discover) },
                onNavigateToDownloads = { navigator.navigate(Downloads) },
                onNavigateToLastfm = { navigator.navigate(SettingsLastfm) },
                onNavigateToAlarms = { navigator.navigate(SettingsAlarms) },
            )
        }

        entry<SignIn> {
            SignInScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToRegister = { navigator.navigate(Register) },
                onAuthenticated = { navigator.goBack() },
            )
        }

        entry<Register> {
            RegisterScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToSignIn = { navigator.navigate(SignIn) },
                onAuthenticated = { navigator.goBack() },
            )
        }

        entry<FriendActivity> {
            FriendActivityScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onJoin = { code -> navigator.navigate(JoinSession(prefillCode = code)) },
                onFindPeople = { navigator.navigate(FindPeople) },
            )
        }

        entry<SharedWithYou> {
            SharedWithYouScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onPreview = { mediaId -> navigator.navigate(PlaylistDetail(playlistId = mediaId)) },
            )
        }

        entry<FindPeople> {
            FindPeopleScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
            )
        }

        entry<FollowingUsers> {
            FollowingUsersScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToFindPeople = { navigator.navigate(FindPeople) },
            )
        }

        entry<SettingsLastfm> {
            LastfmSettingsScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Account> {
            AccountScreen(
                rootPadding = rootPadding,
                onNavigateBack = { navigator.goBack() },
                onNavigateToSignIn = { navigator.navigate(SignIn) },
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
                onNavigateBack = { navigator.goBack() },
                onNavigateToDownloads = { navigator.navigate(Downloads) }
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
 * Registers the media-detail destinations (album / artist / playlist / song-info / join-session /
 * host-session) shared by the main nav display and the player sheet's nested nav. [navigate] pushes a
 * destination and [goBack] pops the current one — each host supplies its own back stack.
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
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
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
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
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
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
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

    entry<JoinSession> { key ->
        val viewModel = hiltViewModel<JoinSessionViewModel, JoinSessionViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) }
        )
        JoinSessionScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onNavigateToHost = { navigate(HostSession) },
            viewModel = viewModel,
        )
    }

    entry<HostSession> {
        HostSessionScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onNavigateToJoin = { navigate(JoinSession()) },
        )
    }

    entry<SimilarSongs> { key ->
        val viewModel = hiltViewModel<SimilarSongsViewModel, SimilarSongsViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) }
        )
        SimilarSongsScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
            viewModel = viewModel,
        )
    }

    entry<ForYou> {
        ForYouScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
        )
    }

    entry<Discover> {
        DiscoverScreen(
            rootPadding = rootPadding,
            onNavigateBack = goBack,
            onViewDetails = { item -> navigateToMediaItemDetails(navigate, item) },
            onMoreLikeThis = { song -> navigateToSimilarSongs(navigate, song) },
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
    // Single-top guard: a rapid double-tap must not push the same destination twice onto the sheet's
    // nested stack.
    val navigate: (NavKey) -> Unit = { if (NavigationLogic.shouldPush(backStack, it)) backStack.add(it) }
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
                onNavigateToPlaylist = { playlistId, name, artworkUrl ->
                    navigate(PlaylistDetail(playlistId, name, artworkUrl))
                },
                onNavigateToSimilarSongs = { song -> navigateToSimilarSongs(navigate, song) },
                onNavigateToJoinSession = { navigate(JoinSession()) },
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
