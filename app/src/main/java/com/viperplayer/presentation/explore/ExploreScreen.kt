package com.viperplayer.presentation.explore

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.revealOnAppear
import com.viperplayer.presentation.home.MediaItemCard
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/**
 * The Explore (discovery) surface. Renders charts / new-release-style carousels, mood & genre chips
 * and browse categories aggregated across enabled plugins. It is a pure renderer: every plugin call
 * and all state assembly live in [ExploreViewModel]; taps are forwarded up as navigation events.
 */
@Composable
fun ExploreScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    ExploreScreenContent(
        uiState = uiState,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onMoodSelected = viewModel::onMoodSelected,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToPlaylist = onNavigateToPlaylist,
        onPlaySongFromSection = viewModel::playSongFromSection,
    )
}

@Composable
internal fun ExploreScreenContent(
    uiState: ExploreUiState,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onMoodSelected: (ExploreMood) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onPlaySongFromSection: (Song, String) -> Unit,
) {
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when (uiState) {
            // Loading / error sit directly in the scaffold slot (no PullToRefreshBox), so they must
            // apply the top-bar inset (contentPadding) themselves, plus the system/nav rootPadding —
            // matching the Content branch, which puts contentPadding on its box and rootPadding on
            // the scroll. Chaining the two here isn't double-padding: they come from different insets.
            ExploreUiState.Loading -> Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(rootPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            is ExploreUiState.Error -> ExploreMessage(
                title = stringResource(R.string.explore_error_title),
                body = uiState.message,
                contentPadding = contentPadding,
                rootPadding = rootPadding,
            )

            is ExploreUiState.Content -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                if (uiState.content.isEmpty) {
                    ExploreMessage(
                        title = if (uiState.hasPlugins) {
                            stringResource(R.string.explore_empty_title)
                        } else {
                            stringResource(R.string.explore_no_plugins_title)
                        },
                        body = if (uiState.hasPlugins) {
                            stringResource(R.string.explore_empty_body)
                        } else {
                            stringResource(R.string.explore_no_plugins_body)
                        },
                        // Already inside the PullToRefreshBox that applies contentPadding, so only
                        // add rootPadding here to avoid double-padding the top-bar inset.
                        contentPadding = PaddingValues(0.dp),
                        rootPadding = rootPadding,
                        testTag = "exploreEmpty",
                    )
                } else {
                    ExploreList(
                        content = uiState.content,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        rootPadding = rootPadding,
                        onMoodSelected = onMoodSelected,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onPlaySongFromSection = onPlaySongFromSection,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreList(
    content: ExploreContent,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    rootPadding: PaddingValues,
    onMoodSelected: (ExploreMood) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onPlaySongFromSection: (Song, String) -> Unit,
) {
    val onItemClick: (MediaItem, String) -> Unit = { item, sectionId ->
        when (item) {
            is Album -> onNavigateToAlbum(item)
            is Artist -> onNavigateToArtist(item)
            is Playlist -> onNavigateToPlaylist(item)
            is Song -> onPlaySongFromSection(item, sectionId)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rootPadding,
    ) {
        // Mood / genre chips gathered from plugin section filters.
        if (content.moods.isNotEmpty()) {
            item(key = "explore-moods-header") {
                ExploreHeader(stringResource(R.string.explore_moods))
            }
            item(key = "explore-moods") {
                LazyRow(
                    modifier = Modifier.testTag("exploreMoods"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(content.moods, key = { "${it.pluginId}:${it.sectionId}:${it.key}" }) { mood ->
                        FilterChip(
                            selected = mood.selected,
                            onClick = { onMoodSelected(mood) },
                            label = { Text(mood.label) },
                        )
                    }
                }
            }
        }

        // Browse categories (e.g. charts / new-release entry points) exposed by plugins.
        if (content.categories.isNotEmpty()) {
            item(key = "explore-categories-header") {
                ExploreHeader(stringResource(R.string.explore_categories))
            }
            item(key = "explore-categories") {
                LazyRow(
                    modifier = Modifier.testTag("exploreCategories"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        content.categories,
                        key = { index, category -> "${category.pluginId}:${category.id}-$index" },
                    ) { index, category ->
                        ExploreCategoryCard(
                            category = category,
                            modifier = Modifier.revealOnAppear(index),
                        )
                    }
                }
            }
        }

        // Discovery carousels — one per plugin home section.
        content.sections.forEach { section ->
            item(key = "${section.id}-header") {
                ExploreHeader(
                    title = section.title.ifBlank { section.pluginName },
                    subtitle = section.subtitle,
                )
            }
            item(key = section.id) {
                LazyRow(
                    modifier = Modifier.testTag("section:${section.id}"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        section.items,
                        key = { index, item -> "${item.id}-$index" },
                    ) { index, item ->
                        val active = (item as? Song)?.id == currentSongId && currentSongId != null
                        MediaItemCard(
                            item = item,
                            modifier = Modifier.revealOnAppear(index),
                            isActive = active,
                            isPlaying = active && isPlaying,
                            onClick = { onItemClick(item, section.id) },
                        )
                    }
                }
            }
        }

        item(key = "explore-bottom-spacer") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExploreHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExploreCategoryCard(category: BrowseCategory, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(160.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            AsyncImage(
                model = category.imageUrl,
                contentDescription = category.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ExploreMessage(
    title: String,
    body: String,
    contentPadding: PaddingValues,
    rootPadding: PaddingValues,
    testTag: String? = null,
) {
    Box(
        modifier = Modifier
            .padding(contentPadding)
            .padding(rootPadding)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = (if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ============================================================================
// Previews
// ============================================================================

@Preview(name = "Explore - Content", showBackground = true)
@Composable
private fun PreviewExploreContent() {
    ViPERPlayerTheme {
        ExploreScreenContent(
            uiState = ExploreUiState.Content(
                content = ExploreContent(
                    moods = listOf(
                        ExploreMood("Chill", "demo", "s1", "chill"),
                        ExploreMood("Focus", "demo", "s1", "focus"),
                    ),
                    categories = listOf(
                        BrowseCategory(id = "charts", pluginId = "demo", name = "Charts"),
                        BrowseCategory(id = "new", pluginId = "demo", name = "New Releases"),
                    ),
                    sections = listOf(
                        ExploreSection(
                            id = "demo:top",
                            pluginId = "demo",
                            pluginName = "Demo",
                            title = "Top Albums",
                            subtitle = "From Demo",
                            items = listOf(
                                Album(id = MediaId("demo", "a1"), name = "Album One"),
                                Album(id = MediaId("demo", "a2"), name = "Album Two"),
                            ),
                        ),
                    ),
                ),
                hasPlugins = true,
            ),
            currentSongId = null,
            isPlaying = false,
            rootPadding = PaddingValues(0.dp),
            onNavigateBack = {},
            onRefresh = {},
            onMoodSelected = {},
            onNavigateToAlbum = {},
            onNavigateToArtist = {},
            onNavigateToPlaylist = {},
            onPlaySongFromSection = { _, _ -> },
        )
    }
}

@Preview(name = "Explore - Empty (no plugins)", showBackground = true)
@Composable
private fun PreviewExploreEmpty() {
    ViPERPlayerTheme {
        ExploreScreenContent(
            uiState = ExploreUiState.Content(content = ExploreContent(), hasPlugins = false),
            currentSongId = null,
            isPlaying = false,
            rootPadding = PaddingValues(0.dp),
            onNavigateBack = {},
            onRefresh = {},
            onMoodSelected = {},
            onNavigateToAlbum = {},
            onNavigateToArtist = {},
            onNavigateToPlaylist = {},
            onPlaySongFromSection = { _, _ -> },
        )
    }
}
