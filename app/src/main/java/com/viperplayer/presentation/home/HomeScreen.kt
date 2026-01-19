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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.common.ViperScaffold

@Composable
fun HomeScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (MediaId) -> Unit = {},
    onNavigateToArtist: (MediaId) -> Unit = {},
    onNavigateToPlaylist: (MediaId) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
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
        
        // Material3 PullToRefreshBox (available in newer M3 versions)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(contentPadding).fillMaxSize()
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
                            CircularProgressIndicator()
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
                                    modifier = Modifier.padding(24.dp),
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

                    // Home Content from Plugins
                    state.homeContent.forEach { (pluginId, content) ->
                        // Quick Picks
                        content.quickPicks?.let { quickPicks ->
                            if (quickPicks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Quick Picks",
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
                                        items(quickPicks) { item ->
                                            MediaItemCard(
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is Album -> onNavigateToAlbum(item.id)
                                                        is com.viperplayer.domain.model.Artist -> onNavigateToArtist(item.id)
                                                        is com.viperplayer.domain.model.Playlist -> onNavigateToPlaylist(item.id)
                                                        else -> {}
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Custom Sections
                        content.sections.forEach { section ->
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
                                                    is Album -> onNavigateToAlbum(item.id)
                                                    is com.viperplayer.domain.model.Artist -> onNavigateToArtist(item.id)
                                                    is com.viperplayer.domain.model.Playlist -> onNavigateToPlaylist(item.id)
                                                    else -> {}
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
        is com.viperplayer.domain.model.Song -> item.title
        is Album -> item.name
        is com.viperplayer.domain.model.Artist -> item.name
        is com.viperplayer.domain.model.Playlist -> item.name
    }

    val subtitle = when (item) {
        is com.viperplayer.domain.model.Song -> item.artistNames
        is Album -> item.artistName
        is com.viperplayer.domain.model.Artist -> null
        is com.viperplayer.domain.model.Playlist -> item.description
    }

    val artworkUrl = when (item) {
        is com.viperplayer.domain.model.Song -> item.artworkUrl
        is Album -> item.artworkUrl
        is com.viperplayer.domain.model.Artist -> item.imageUrl
        is com.viperplayer.domain.model.Playlist -> item.artworkUrl
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

