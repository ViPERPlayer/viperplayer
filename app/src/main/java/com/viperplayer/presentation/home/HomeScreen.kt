package com.viperplayer.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.theme.ViPERPlayerTheme

@Composable
fun HomeScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.onTimeChanged()
            }
        }

        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        viewModel.onTimeChanged()

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        rootPadding = rootPadding,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToPlaylist = onNavigateToPlaylist,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToAnalytics = onNavigateToAnalytics,
        onRefresh = viewModel::refresh,
        onPlaySongFromQuickPicks = viewModel::playSongFromQuickPicks,
        onPlaySongFromSection = viewModel::playSongFromSection
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySongFromQuickPicks: (Song) -> Unit,
    onPlaySongFromSection: (Song, String) -> Unit
) {
    var titleOverflowed by remember { mutableStateOf(false) }
    val title = uiState.userName.let { userName ->
        if (userName != null && !titleOverflowed) {
            val resId = when (uiState.greetingType) {
                GreetingType.MORNING -> R.string.greeting_good_morning_personalized
                GreetingType.AFTERNOON -> R.string.greeting_good_afternoon_personalized
                GreetingType.EVENING -> R.string.greeting_good_evening_personalized
                GreetingType.NIGHT -> R.string.greeting_good_night_personalized
            }
            stringResource(resId, userName)
        } else {
            val resId = when (uiState.greetingType) {
                GreetingType.MORNING -> R.string.greeting_good_morning
                GreetingType.AFTERNOON -> R.string.greeting_good_afternoon
                GreetingType.EVENING -> R.string.greeting_good_evening
                GreetingType.NIGHT -> R.string.greeting_good_night
            }
            stringResource(resId)
        }
    }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        onTextLayout = { textLayoutResult ->
                            if (textLayoutResult.hasVisualOverflow && !titleOverflowed) {
                                titleOverflowed = true
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "History",
                        )
                    }
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(
                            imageVector = Icons.Rounded.QueryStats,
                            contentDescription = "Stats",
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        }
    ) { contentPadding ->
        val isRefreshing = (uiState as? HomeUiState.Content)?.isRefreshing ?: false

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = rootPadding
            ) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        }
                    }

                    is HomeUiState.Error -> {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is HomeUiState.Content -> {
                        // Empty state (no plugins)
                        if (state.connectedPlugins.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No plugins connected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Go to Plugins tab to connect a music source",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Browse Categories
                        if (state.categories.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Browse",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.categories) { category ->
                                        CategoryCard(
                                            category = category,
                                            onClick = { /* TODO: Navigate to category */ }
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Picks
                        state.quickPicks?.let { quickPicks ->
                            if (quickPicks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Quick Picks",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(quickPicks) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is Album -> onNavigateToAlbum(item)
                                                        is Artist -> onNavigateToArtist(item)
                                                        is Playlist -> onNavigateToPlaylist(item)
                                                        is Song -> onPlaySongFromQuickPicks(item)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Custom Sections
                        state.sections.forEach { section ->
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(section.items) { item ->
                                        MediaItemCard(
                                            item = item,
                                            onClick = {
                                                when (item) {
                                                    is Album -> onNavigateToAlbum(item)
                                                    is Artist -> onNavigateToArtist(item)
                                                    is Playlist -> onNavigateToPlaylist(item)
                                                    is Song -> onPlaySongFromSection(
                                                        item,
                                                        section.id
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: BrowseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            AsyncImage(
                model = category.imageUrl,
                contentDescription = category.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun MediaItemCard(
    item: com.viperplayer.domain.model.MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (item) {
        is Song -> item.title
        is Album -> item.name
        is Artist -> item.name
        is Playlist -> item.name
    }

    val subtitle = when (item) {
        is Song -> item.artistNames
        is Album -> item.artistName
        is Artist -> null
        is Playlist -> item.description
    }

    val artworkUrl = when (item) {
        is Song -> item.artworkUrl
        is Album -> item.artworkUrl
        is Artist -> item.imageUrl
        is Playlist -> item.artworkUrl
    }

    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// Previews
// ============================================================================

// Preview for Loading state
@Preview(name = "Loading State - Morning", showBackground = true)
@Composable
fun PreviewHomeScreenLoadingMorning() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Loading(
                greetingType = GreetingType.MORNING,
                userName = null
            )
        )
    }
}

@Preview(name = "Loading State - Afternoon with User", showBackground = true)
@Composable
fun PreviewHomeScreenLoadingAfternoonWithUser() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Loading(
                greetingType = GreetingType.AFTERNOON,
                userName = "John"
            )
        )
    }
}

// Preview for Error state
@Preview(name = "Error State - Morning", showBackground = true)
@Composable
fun PreviewHomeScreenErrorMorning() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Error(
                greetingType = GreetingType.MORNING,
                userName = null,
                message = "Failed to load content. Please check your internet connection."
            )
        )
    }
}

@Preview(name = "Error State - Evening with User", showBackground = true)
@Composable
fun PreviewHomeScreenErrorEveningWithUser() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Error(
                greetingType = GreetingType.EVENING,
                userName = "Sarah",
                message = "Unable to connect to music services."
            )
        )
    }
}

// Preview for Content state - Empty (no plugins)
@Preview(name = "Content State - Empty (No Plugins)", showBackground = true)
@Composable
fun PreviewHomeScreenContentEmpty() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.AFTERNOON,
                userName = "Alex",
                categories = emptyList(),
                quickPicks = null,
                sections = emptyList(),
                connectedPlugins = emptyList(),
                isRefreshing = false
            )
        )
    }
}

// Preview for Content state - With Categories
@Preview(name = "Content State - With Categories", showBackground = true)
@Composable
fun PreviewHomeScreenContentWithCategories() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.MORNING,
                userName = "Emma",
                categories = getSampleCategories(),
                quickPicks = null,
                sections = emptyList(),
                connectedPlugins = listOf(getSamplePlugin()),
                isRefreshing = false
            )
        )
    }
}

// Preview for Content state - With Quick Picks
@Preview(name = "Content State - With Quick Picks", showBackground = true)
@Composable
fun PreviewHomeScreenContentWithQuickPicks() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.AFTERNOON,
                userName = null,
                categories = getSampleCategories(),
                quickPicks = getSampleAlbums(),
                sections = emptyList(),
                connectedPlugins = listOf(getSamplePlugin()),
                isRefreshing = false
            )
        )
    }
}

// Preview for Content state - With Sections
@Preview(name = "Content State - With Sections", showBackground = true)
@Composable
fun PreviewHomeScreenContentWithSections() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.EVENING,
                userName = "Michael",
                categories = getSampleCategories(),
                quickPicks = null,
                sections = listOf(
                    HomeSection(
                        id = "recently_played",
                        title = "Recently Played",
                        items = getSampleAlbums()
                    ),
                    HomeSection(
                        id = "recommended",
                        title = "Recommended for You",
                        items = getSamplePlaylists()
                    )
                ),
                connectedPlugins = listOf(getSamplePlugin()),
                isRefreshing = false
            )
        )
    }
}

// Preview for Content state - Full Content
@Preview(name = "Content State - Full Content", showBackground = true)
@Composable
fun PreviewHomeScreenContentFull() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.NIGHT,
                userName = "David",
                categories = getSampleCategories(),
                quickPicks = getSampleAlbums().take(3),
                sections = listOf(
                    HomeSection(
                        id = "recently_played",
                        title = "Recently Played",
                        items = getSampleAlbums()
                    ),
                    HomeSection(
                        id = "top_artists",
                        title = "Top Artists",
                        items = getSampleArtists()
                    ),
                    HomeSection(
                        id = "your_playlists",
                        title = "Your Playlists",
                        items = getSamplePlaylists()
                    )
                ),
                connectedPlugins = listOf(getSamplePlugin()),
                isRefreshing = false
            )
        )
    }
}

// Preview for Content state - Refreshing
@Preview(name = "Content State - Refreshing", showBackground = true)
@Composable
fun PreviewHomeScreenContentRefreshing() {
    ViPERPlayerTheme {
        HomeScreenPreview(
            uiState = HomeUiState.Content(
                greetingType = GreetingType.MORNING,
                userName = "Lisa",
                categories = getSampleCategories(),
                quickPicks = getSampleAlbums().take(3),
                sections = listOf(
                    HomeSection(
                        id = "recently_played",
                        title = "Recently Played",
                        items = getSampleAlbums()
                    )
                ),
                connectedPlugins = listOf(getSamplePlugin()),
                isRefreshing = true
            )
        )
    }
}

// Preview wrapper that provides the UI state
@Composable
private fun HomeScreenPreview(uiState: HomeUiState) {
    HomeScreenContent(
        uiState = uiState,
        rootPadding = PaddingValues(0.dp),
        onNavigateToAlbum = {},
        onNavigateToArtist = {},
        onNavigateToPlaylist = {},
        onNavigateToSettings = {},
        onNavigateToHistory = {},
        onNavigateToAnalytics = {},
        onRefresh = {},
        onPlaySongFromQuickPicks = {},
        onPlaySongFromSection = { _, _ -> }
    )
}

// Helper function to create sample categories
private fun getSampleCategories(): List<BrowseCategory> {
    return listOf(
        BrowseCategory(
            id = "rock",
            pluginId = "sixthsource",
            name = "Rock",
            imageUrl = "https://picsum.photos/seed/rock/400/300"
        ),
        BrowseCategory(
            id = "pop",
            pluginId = "sixthsource",
            name = "Pop",
            imageUrl = "https://picsum.photos/seed/pop/400/300"
        ),
        BrowseCategory(
            id = "jazz",
            pluginId = "sixthsource",
            name = "Jazz",
            imageUrl = "https://picsum.photos/seed/jazz/400/300"
        ),
        BrowseCategory(
            id = "classical",
            pluginId = "sixthsource",
            name = "Classical",
            imageUrl = "https://picsum.photos/seed/classical/400/300"
        )
    )
}

// Helper function to create sample albums
private fun getSampleAlbums(): List<Album> {
    return listOf(
        Album(
            id = MediaId("album", "1"),
            name = "Midnight Dreams",
            artists = listOf(
                Artist(
                    id = MediaId("artist", "1"),
                    name = "The Nocturnes"
                )
            ),
            artworkUrl = "https://picsum.photos/seed/album1/400/400",
            releaseYear = 2023,
            trackCount = 12
        ),
        Album(
            id = MediaId("album", "2"),
            name = "Electric Waves",
            artists = listOf(
                Artist(
                    id = MediaId("artist", "2"),
                    name = "Synth Masters"
                )
            ),
            artworkUrl = "https://picsum.photos/seed/album2/400/400",
            releaseYear = 2024,
            trackCount = 10
        ),
        Album(
            id = MediaId("album", "3"),
            name = "Acoustic Sessions",
            artists = listOf(
                Artist(
                    id = MediaId("artist", "3"),
                    name = "The Unplugged"
                )
            ),
            artworkUrl = "https://picsum.photos/seed/album3/400/400",
            releaseYear = 2022,
            trackCount = 8
        ),
        Album(
            id = MediaId("album", "4"),
            name = "Urban Beats",
            artists = listOf(
                Artist(
                    id = MediaId("artist", "4"),
                    name = "City Sounds"
                )
            ),
            artworkUrl = "https://picsum.photos/seed/album4/400/400",
            releaseYear = 2024,
            trackCount = 15
        ),
        Album(
            id = MediaId("album", "5"),
            name = "Classical Collection",
            artists = listOf(
                Artist(
                    id = MediaId("artist", "5"),
                    name = "Symphony Orchestra"
                )
            ),
            artworkUrl = "https://picsum.photos/seed/album5/400/400",
            releaseYear = 2021,
            trackCount = 20
        )
    )
}

// Helper function to create sample artists
private fun getSampleArtists(): List<Artist> {
    return listOf(
        Artist(
            id = MediaId("artist", "1"),
            name = "The Nocturnes",
            imageUrl = "https://picsum.photos/seed/artist1/400/400"
        ),
        Artist(
            id = MediaId("artist", "2"),
            name = "Synth Masters",
            imageUrl = "https://picsum.photos/seed/artist2/400/400"
        ),
        Artist(
            id = MediaId("artist", "3"),
            name = "The Unplugged",
            imageUrl = "https://picsum.photos/seed/artist3/400/400"
        ),
        Artist(
            id = MediaId("artist", "4"),
            name = "City Sounds",
            imageUrl = "https://picsum.photos/seed/artist4/400/400"
        )
    )
}

// Helper function to create sample playlists
private fun getSamplePlaylists(): List<Playlist> {
    return listOf(
        Playlist(
            id = MediaId("playlist", "1"),
            name = "Chill Vibes",
            description = "Relaxing music for any time",
            artworkUrl = "https://picsum.photos/seed/playlist1/400/400",
            songCount = 50
        ),
        Playlist(
            id = MediaId("playlist", "2"),
            name = "Workout Mix",
            description = "High energy tracks",
            artworkUrl = "https://picsum.photos/seed/playlist2/400/400",
            songCount = 30
        ),
        Playlist(
            id = MediaId("playlist", "3"),
            name = "Focus Flow",
            description = "Music for concentration",
            artworkUrl = "https://picsum.photos/seed/playlist3/400/400",
            songCount = 40
        ),
        Playlist(
            id = MediaId("playlist", "4"),
            name = "Party Hits",
            description = "The best party songs",
            artworkUrl = "https://picsum.photos/seed/playlist4/400/400",
            songCount = 60
        )
    )
}

// Helper function to create sample plugin
private fun getSamplePlugin(): Plugin {
    return Plugin(
        info = PluginInfo(
            id = "sixthsource",
            name = "a streaming service",
            version = "1.0.0",
            apiVersion = 1
        ),
        capabilities = PluginCapabilities(
            canSearch = true,
            canBrowse = true,
            hasLibrary = true,
            hasPlaylists = true
        ),
        isConnected = true
    )
}
