package com.viperplayer.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.viperplayer.presentation.common.revealOnAppear
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.presentation.common.PlayingArtworkOverlay
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.BannerSection
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.CarouselSection
import com.viperplayer.domain.model.FilterState
import com.viperplayer.domain.model.GridSection
import com.viperplayer.domain.model.HeroSection
import com.viperplayer.domain.model.HomeSection
import com.viperplayer.domain.model.ItemShape
import com.viperplayer.domain.model.ListSection
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.presentation.plugins.PluginActionsBanner
import com.viperplayer.presentation.plugins.PluginActionsSheet
import com.viperplayer.presentation.plugins.PluginActionsViewModel
import com.viperplayer.presentation.plugins.rememberPluginActionResolver
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.PersonGlyphAvatar
import com.viperplayer.presentation.theme.ViPERPlayerTheme

@Composable
fun HomeScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToCategory: (BrowseCategory) -> Unit,
    onNavigateToYou: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState by viewModel.topBarState.collectAsStateWithLifecycle()

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

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Pending plugin actions (permission grants, sign-ins, verifications) surfaced via the bell.
    val actionsViewModel: PluginActionsViewModel = hiltViewModel()
    val pendingActions by actionsViewModel.pendingActions.collectAsStateWithLifecycle()
    val resolveAction = rememberPluginActionResolver { actionsViewModel.refresh() }
    // Re-query when returning from a plugin's login/verification/settings activity.
    LifecycleResumeEffect(Unit) {
        actionsViewModel.refresh()
        onPauseOrDispose { }
    }

    HomeScreenContent(
        uiState = uiState,
        topBarState = topBarState,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        pendingActions = pendingActions,
        onResolveAction = resolveAction,
        rootPadding = rootPadding,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToPlaylist = onNavigateToPlaylist,
        onNavigateToCategory = onNavigateToCategory,
        onNavigateToYou = onNavigateToYou,
        onNavigateToNotifications = onNavigateToNotifications,
        onRefresh = viewModel::refresh,
        onPlaySongFromQuickPicks = viewModel::playSongFromQuickPicks,
        onPlaySongFromSection = viewModel::playSongFromSection,
        onFilterSelected = viewModel::onSectionFilterSelected
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    topBarState: HomeTopBarState = HomeTopBarState(),
    currentSongId: MediaId? = null,
    isPlaying: Boolean = false,
    pendingActions: List<PluginPendingAction> = emptyList(),
    onResolveAction: (PluginPendingAction) -> Unit = {},
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToCategory: (BrowseCategory) -> Unit = {},
    onNavigateToYou: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySongFromQuickPicks: (Song) -> Unit,
    onPlaySongFromSection: (Song, String) -> Unit,
    onFilterSelected: (HomeSection, String) -> Unit = { _, _ -> }
) {
    var showActionsSheet by remember { mutableStateOf(false) }
    // Dismissal is keyed on the current action set: a new action re-shows the banner.
    var bannerDismissed by remember(pendingActions.map { it.key }.toSet()) { mutableStateOf(false) }
    val greetingCaption = stringResource(
        when (uiState.greetingType) {
            GreetingType.MORNING -> R.string.greeting_good_morning
            GreetingType.AFTERNOON -> R.string.greeting_good_afternoon
            GreetingType.EVENING -> R.string.greeting_good_evening
            GreetingType.NIGHT -> R.string.greeting_good_night
        }
    )

    ViperScaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // Avatar entry to the You hub: generic glyph signed-out, initials signed-in, with a
                    // live ring when friends are listening.
                    IconButton(onClick = onNavigateToYou) {
                        // Signed-in avatars always carry the gradient identity ring; the "live" label
                        // is still driven by friendsLive. Signed-out (glyph) shows no ring.
                        AvatarRing(
                            size = 44.dp,
                            hasLiveActivity = topBarState.friendsLive,
                            showRing = topBarState.signedIn,
                        ) {
                            if (topBarState.signedIn && topBarState.initials.isNotBlank()) {
                                InitialsAvatar(text = topBarState.initials)
                            } else {
                                PersonGlyphAvatar()
                            }
                        }
                    }
                },
                title = {
                    Column {
                        val userName = uiState.userName
                        if (userName != null) {
                            // Signed in: small greeting caption above the first name.
                            Text(
                                text = greetingCaption,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            // Signed out: the greeting itself is the headline (mockup 2b).
                            Text(
                                text = greetingCaption,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (topBarState.notificationCount > 0) {
                                    Badge { Text(topBarState.notificationCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = stringResource(R.string.plugin_actions_bell_cd),
                            )
                        }
                    }
                },
            )
        }
    ) { contentPadding ->
        when (uiState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(rootPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            is HomeUiState.Error -> {
                Card(
                    modifier = Modifier
                        .padding(rootPadding)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is HomeUiState.Content -> {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = rootPadding
                    ) {
                        // Plugins that need the user to act (sign in, grant a permission...).
                        if (pendingActions.isNotEmpty() && !bannerDismissed) {
                            item(key = "plugin-actions-banner") {
                                PluginActionsBanner(
                                    actions = pendingActions,
                                    onOpen = { showActionsSheet = true },
                                    onDismiss = { bannerDismissed = true },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }

                        // Empty state (no plugins)
                        if (uiState.connectedPlugins.isEmpty()) {
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
                                            text = stringResource(R.string.home_no_plugins_connected),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.home_no_plugins_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Browse Categories
                        if (uiState.categories.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.home_browse),
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
                                    itemsIndexed(uiState.categories, key = { index, category -> "${category.id}-$index" }) { index, category ->
                                        CategoryCard(
                                            category = category,
                                            modifier = Modifier.revealOnAppear(index),
                                            // Opens the category-contents screen (CategoryDetail),
                                            // backed by PluginRepository.getCategoryContents.
                                            onClick = { onNavigateToCategory(category) }
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Picks
                        uiState.quickPicks?.let { quickPicks ->
                            if (quickPicks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = stringResource(R.string.home_quick_picks),
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
                                        itemsIndexed(quickPicks, key = { index, item -> "${item.id}-$index" }) { index, item ->
                                            val active = isCurrent(item, currentSongId)
                                            MediaItemCard(
                                                item = item,
                                                modifier = Modifier.revealOnAppear(index),
                                                isActive = active,
                                                isPlaying = active && isPlaying,
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

                        // Custom Sections — each design (carousel/grid/list/hero/banner) renders distinctly.
                        uiState.sections.forEach { section ->
                            homeSection(
                                section = section,
                                currentSongId = currentSongId,
                                isPlaying = isPlaying,
                                onFilterSelected = onFilterSelected,
                            ) { item ->
                                when (item) {
                                    is Album -> onNavigateToAlbum(item)
                                    is Artist -> onNavigateToArtist(item)
                                    is Playlist -> onNavigateToPlaylist(item)
                                    is Song -> onPlaySongFromSection(item, section.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActionsSheet) {
        PluginActionsSheet(
            actions = pendingActions,
            onResolve = {
                showActionsSheet = false
                onResolveAction(it)
            },
            onDismiss = { showActionsSheet = false },
        )
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

// ============================================================================
// Home section rendering — a distinct design per HomeSection subtype
// ============================================================================

/** True when [item] is the track currently loaded in the player (matched by id). */
private fun isCurrent(item: MediaItem, currentSongId: MediaId?): Boolean =
    currentSongId != null && (item as? Song)?.id == currentSongId

private fun LazyListScope.homeSection(
    section: HomeSection,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onFilterSelected: (HomeSection, String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
) {
    when (section) {
        is CarouselSection -> {
            sectionHeader(section.title, section.subtitle)
            if (section.filters.isNotEmpty()) {
                // A row of genre/category chips above the shelf; tapping one re-filters the section.
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(section.filters, key = { it.key }) { filter ->
                            FilterChip(
                                selected = filter.selected,
                                onClick = { onFilterSelected(section, filter.key) },
                                label = { Text(filter.label) },
                            )
                        }
                    }
                }
            }
            // The shelf area shows a spinner / an isolated error / the items, per the filter state.
            val shelfHeight = if (section.rows > 1) (section.rows * 68).dp else 188.dp
            when (section.filterState) {
                FilterState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(shelfHeight),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }
                }

                FilterState.Error -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(shelfHeight)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_genres_load_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                FilterState.Idle -> if (section.rows > 1) {
                    // Multi-row shelf: a horizontal grid of compact tiles, `rows` items per column.
                    item {
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(section.rows),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(shelfHeight),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItemsIndexed(
                                section.items,
                                key = { index, item -> "${item.id}-$index" },
                            ) { _, item ->
                                val active = isCurrent(item, currentSongId)
                                CompactItemTile(
                                    item = item,
                                    isActive = active,
                                    isPlaying = active && isPlaying,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(section.items, key = { index, item -> "${item.id}-$index" }) { index, item ->
                                val active = isCurrent(item, currentSongId)
                                MediaItemCard(
                                    item = item,
                                    modifier = Modifier.revealOnAppear(index),
                                    isActive = active,
                                    isPlaying = active && isPlaying,
                                    onClick = { onItemClick(item) },
                                    itemShape = section.itemShape,
                                )
                            }
                        }
                    }
                }
            }
        }

        is GridSection -> {
            sectionHeader(section.title, section.subtitle)
            val columns = section.columns.coerceAtLeast(1)
            itemsIndexed(section.items.chunked(columns), key = { index, _ -> "${section.id}-row-$index" }) { index, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .revealOnAppear(index),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        val active = isCurrent(item, currentSongId)
                        Box(modifier = Modifier.weight(1f)) {
                            MediaItemCard(
                                item = item,
                                isActive = active,
                                isPlaying = active && isPlaying,
                                onClick = { onItemClick(item) },
                                itemShape = section.itemShape,
                                fillWidth = true,
                            )
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        is ListSection -> {
            sectionHeader(section.title, section.subtitle)
            itemsIndexed(section.items, key = { index, item -> "${section.id}-${item.id}-$index" }) { index, item ->
                val active = isCurrent(item, currentSongId)
                Box(modifier = Modifier.revealOnAppear(index)) {
                    TrackRow(
                        item = item,
                        isActive = active,
                        isPlaying = active && isPlaying,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }

        is HeroSection -> item {
            Spacer(modifier = Modifier.height(24.dp))
            HeroCard(section = section, onClick = { onItemClick(section.item) })
        }

        is BannerSection -> item {
            Spacer(modifier = Modifier.height(24.dp))
            BannerCard(section = section)
        }
    }
}

private fun LazyListScope.sectionHeader(title: String, subtitle: String?) {
    item {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 8.dp)
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
}

/** A square/circle/wide artwork card used in carousels and grids. */
@Composable
fun MediaItemCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemShape: ItemShape = ItemShape.SQUARE,
    fillWidth: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    val circle = itemShape == ItemShape.CIRCLE
    val artShape = if (circle) CircleShape else RoundedCornerShape(8.dp)
    val aspect = if (itemShape == ItemShape.WIDE) 16f / 9f else 1f
    val artModifier = when {
        fillWidth -> Modifier.fillMaxWidth().aspectRatio(aspect)
        itemShape == ItemShape.WIDE -> Modifier.width(200.dp).aspectRatio(aspect)
        else -> Modifier.size(140.dp)
    }
    val textAlign = if (circle) TextAlign.Center else null
    val textWidth = if (circle) Modifier.fillMaxWidth() else Modifier

    Column(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier.width(140.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(modifier = artModifier.clip(artShape), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = item.displayArtworkUrl(),
                contentDescription = item.displayTitle(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.displayTitle(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = textWidth,
        )
        item.displaySubtitle()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = textWidth,
            )
        }
    }
}

/** A compact horizontal row (thumbnail + title/subtitle) used by [ListSection]. */
@Composable
private fun TrackRow(
    item: MediaItem,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(if (item is Artist) CircleShape else RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.displayArtworkUrl(),
                contentDescription = item.displayTitle(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.displaySubtitle()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A fixed-width horizontal tile (small art + title) for multi-row carousel shelves. */
@Composable
private fun CompactItemTile(
    item: MediaItem,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(if (item is Artist) CircleShape else RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.displayArtworkUrl(),
                contentDescription = item.displayTitle(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.displaySubtitle()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A large featured item with a backdrop and a darkening scrim, used by [HeroSection]. */
@Composable
private fun HeroCard(section: HeroSection, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = section.backgroundImageUrl ?: section.item.displayArtworkUrl(),
                contentDescription = section.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                if (section.title.isNotBlank()) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = section.item.displayTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                (section.description ?: section.subtitle ?: section.item.displaySubtitle())?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A promotional banner (image or tinted) with title/text, used by [BannerSection]. */
@Composable
private fun BannerCard(section: BannerSection) {
    val hasImage = !section.imageUrl.isNullOrBlank()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (hasImage) CardDefaults.cardColors()
        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasImage) {
                AsyncImage(
                    model = section.imageUrl,
                    contentDescription = section.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
                        )
                )
            }
            val onColor = if (hasImage) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            ) {
                if (section.title.isNotBlank()) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = onColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                (section.text ?: section.subtitle)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onColor.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun MediaItem.displayTitle(): String = when (this) {
    is Song -> title
    is Album -> name
    is Artist -> name
    is Playlist -> name
}

@Composable
private fun MediaItem.displaySubtitle(): String? = when (this) {
    is Song -> artistNames
    is Album -> artistName ?: stringResource(R.string.unknown_artist)
    is Artist -> null
    is Playlist -> description
}

private fun MediaItem.displayArtworkUrl(): String? = when (this) {
    is Song -> artworkUrl
    is Album -> artworkUrl
    is Artist -> imageUrl
    is Playlist -> artworkUrl
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
            text = album.artistName ?: stringResource(R.string.unknown_artist),
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
                    CarouselSection(
                        id = "recently_played",
                        title = "Recently Played",
                        items = getSampleAlbums()
                    ),
                    CarouselSection(
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
                    CarouselSection(
                        id = "recently_played",
                        title = "Recently Played",
                        items = getSampleAlbums()
                    ),
                    CarouselSection(
                        id = "top_artists",
                        title = "Top Artists",
                        items = getSampleArtists()
                    ),
                    CarouselSection(
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
                    CarouselSection(
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
        onNavigateToYou = {},
        onNavigateToNotifications = {},
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
            id = MediaId.Plugin("album", "1"),
            name = "Midnight Dreams",
            artists = listOf(ArtistRef(name = "The Nocturnes", id = MediaId.Plugin("artist", "1"))),
            artworkUrl = "https://picsum.photos/seed/album1/400/400",
            releaseYear = 2023,
            trackCount = 12
        ),
        Album(
            id = MediaId.Plugin("album", "2"),
            name = "Electric Waves",
            artists = listOf(ArtistRef(name = "Synth Masters", id = MediaId.Plugin("artist", "2"))),
            artworkUrl = "https://picsum.photos/seed/album2/400/400",
            releaseYear = 2024,
            trackCount = 10
        ),
        Album(
            id = MediaId.Plugin("album", "3"),
            name = "Acoustic Sessions",
            artists = listOf(ArtistRef(name = "The Unplugged", id = MediaId.Plugin("artist", "3"))),
            artworkUrl = "https://picsum.photos/seed/album3/400/400",
            releaseYear = 2022,
            trackCount = 8
        ),
        Album(
            id = MediaId.Plugin("album", "4"),
            name = "Urban Beats",
            artists = listOf(ArtistRef(name = "City Sounds", id = MediaId.Plugin("artist", "4"))),
            artworkUrl = "https://picsum.photos/seed/album4/400/400",
            releaseYear = 2024,
            trackCount = 15
        ),
        Album(
            id = MediaId.Plugin("album", "5"),
            name = "Classical Collection",
            artists = listOf(ArtistRef(name = "Symphony Orchestra", id = MediaId.Plugin("artist", "5"))),
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
            id = MediaId.Plugin("artist", "1"),
            name = "The Nocturnes",
            imageUrl = "https://picsum.photos/seed/artist1/400/400"
        ),
        Artist(
            id = MediaId.Plugin("artist", "2"),
            name = "Synth Masters",
            imageUrl = "https://picsum.photos/seed/artist2/400/400"
        ),
        Artist(
            id = MediaId.Plugin("artist", "3"),
            name = "The Unplugged",
            imageUrl = "https://picsum.photos/seed/artist3/400/400"
        ),
        Artist(
            id = MediaId.Plugin("artist", "4"),
            name = "City Sounds",
            imageUrl = "https://picsum.photos/seed/artist4/400/400"
        )
    )
}

// Helper function to create sample playlists
private fun getSamplePlaylists(): List<Playlist> {
    return listOf(
        Playlist(
            id = MediaId.Plugin("playlist", "1"),
            name = "Chill Vibes",
            description = "Relaxing music for any time",
            artworkUrl = "https://picsum.photos/seed/playlist1/400/400",
            songCount = 50
        ),
        Playlist(
            id = MediaId.Plugin("playlist", "2"),
            name = "Workout Mix",
            description = "High energy tracks",
            artworkUrl = "https://picsum.photos/seed/playlist2/400/400",
            songCount = 30
        ),
        Playlist(
            id = MediaId.Plugin("playlist", "3"),
            name = "Focus Flow",
            description = "Music for concentration",
            artworkUrl = "https://picsum.photos/seed/playlist3/400/400",
            songCount = 40
        ),
        Playlist(
            id = MediaId.Plugin("playlist", "4"),
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
