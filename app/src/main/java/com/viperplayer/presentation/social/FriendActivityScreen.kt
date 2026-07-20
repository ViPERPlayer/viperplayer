package com.viperplayer.presentation.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.AvatarRing
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.OutlinedPill
import com.viperplayer.presentation.common.components.PersonGlyphAvatar
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.common.components.TonalChip
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus

/** Corner radius of the small artwork thumbnails in the feed. */
private val ThumbCornerRadius = 10.dp

/** Divider inset for artwork/avatar-led feed rows (avatar/thumb + gap). */
private val FeedRowDividerInset = 74.dp

/**
 * Friend-activity feed (route `FriendActivity`, mockup 5a). A "listening now" rail of friends playing
 * right now (with a joinable badge for anyone hosting a Jam) followed by an "earlier" list of shared /
 * liked / followed events. Backed by [FriendActivityViewModel]; when social is off or the feed is empty
 * it renders the gated / empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendActivityScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.friend_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = stringResource(R.string.friend_activity_invite),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.enabled -> GatedState(innerPadding = innerPadding, rootPadding = rootPadding)
            state.loading -> LoadingState(innerPadding = innerPadding)
            state.isEmpty -> EmptyState(
                message = stringResource(R.string.friend_activity_empty),
                innerPadding = innerPadding,
                rootPadding = rootPadding,
            )
            else -> FriendActivityFeed(
                state = state,
                innerPadding = innerPadding,
                rootPadding = rootPadding,
            )
        }
    }
}

@Composable
private fun FriendActivityFeed(
    state: FriendActivityUiState,
    innerPadding: PaddingValues,
    rootPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp) + rootPadding.bottom(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.listeningNow.isNotEmpty()) {
            item(key = "listening-now-label") {
                SectionLabel(
                    text = stringResource(R.string.friend_activity_section_listening_now),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            item(key = "listening-now-card") {
                SurfaceCard {
                    state.listeningNow.forEachIndexed { index, item ->
                        if (index > 0) InsetDivider(startInset = FeedRowDividerInset)
                        ListeningNowRow(item = item)
                    }
                }
            }
        }
        if (state.earlier.isNotEmpty()) {
            item(key = "earlier-label") {
                SectionLabel(
                    text = stringResource(R.string.friend_activity_section_earlier),
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            item(key = "earlier-card") {
                SurfaceCard {
                    state.earlier.forEachIndexed { index, (item, time) ->
                        if (index > 0) InsetDivider(startInset = FeedRowDividerInset)
                        EarlierRow(item = item, relativeTime = time.toString())
                    }
                }
            }
        }
    }
}

/** A "listening now" row: live avatar, name (+ Jam badge), track, and Join pill or play affordance. */
@Composable
private fun ListeningNowRow(item: FriendActivityItem.ListeningNow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(name = item.friend.displayName, live = true)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.friend.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isHosting) JamBadge()
            }
            Text(
                text = stringResource(
                    R.string.friend_activity_track_line,
                    item.track.title,
                    item.track.artist,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isHosting) {
            FilledPill(text = stringResource(R.string.friend_activity_join), onClick = {})
        } else {
            ArtworkThumb(
                artworkUrl = item.track.artworkUrl,
                contentDescription = null,
                size = 40.dp,
            )
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.friend_activity_cd_play),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** An "earlier" row: idle avatar, event line (name + action + time), detail, and a type affordance. */
@Composable
private fun EarlierRow(item: FriendActivityItem, relativeTime: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(name = item.friend.displayName, live = false)
        Column(modifier = Modifier.weight(1f)) {
            val eventText = when (item) {
                is FriendActivityItem.SharedPlaylist ->
                    stringResource(R.string.friend_activity_shared_playlist, item.friend.displayName, relativeTime)
                is FriendActivityItem.LikedSong ->
                    stringResource(R.string.friend_activity_liked_song, item.friend.displayName, relativeTime)
                is FriendActivityItem.FollowedArtist ->
                    stringResource(R.string.friend_activity_followed_artist, item.friend.displayName, relativeTime)
                is FriendActivityItem.ListeningNow ->
                    stringResource(R.string.friend_activity_track_line, item.track.title, item.track.artist)
            }
            val detailText = when (item) {
                is FriendActivityItem.SharedPlaylist -> item.playlistName
                is FriendActivityItem.LikedSong -> item.songTitle
                is FriendActivityItem.FollowedArtist -> item.artistName
                is FriendActivityItem.ListeningNow -> item.track.title
            }
            Text(
                text = eventText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detailText,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EarlierTrailing(item = item)
    }
}

/** The trailing affordance for an "earlier" row, keyed to the event type. */
@Composable
private fun EarlierTrailing(item: FriendActivityItem) {
    when (item) {
        is FriendActivityItem.SharedPlaylist ->
            TonalChip(text = stringResource(R.string.action_save), onClick = {})
        is FriendActivityItem.LikedSong ->
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.friend_activity_cd_liked),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
        is FriendActivityItem.FollowedArtist ->
            ArtworkThumb(
                artworkUrl = item.friend.avatarUrl,
                contentDescription = null,
                size = 40.dp,
                shape = CircleShape,
            )
        is FriendActivityItem.ListeningNow -> Unit
    }
}

/**
 * Shared-with-you inbox (route `SharedWithYou`, mockup 5b). A "new" section of unread invites rendered
 * as full action cards (Save to library / Preview / dismiss) and an "earlier" section of already-seen
 * invites shown as compact saved rows. Backed by [SharedWithYouViewModel]; when social is off or the
 * inbox is empty it renders the gated / empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedWithYouScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedWithYouViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shared_with_you_title)) },
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
    ) { innerPadding ->
        when {
            !state.enabled -> GatedState(innerPadding = innerPadding, rootPadding = rootPadding)
            state.loading -> LoadingState(innerPadding = innerPadding)
            state.isEmpty -> EmptyState(
                message = stringResource(R.string.shared_with_you_empty),
                innerPadding = innerPadding,
                rootPadding = rootPadding,
            )
            else -> SharedWithYouInbox(
                state = state,
                onSave = viewModel::save,
                onDismiss = viewModel::dismiss,
                innerPadding = innerPadding,
                rootPadding = rootPadding,
            )
        }
    }
}

@Composable
private fun SharedWithYouInbox(
    state: SharedWithYouUiState,
    onSave: (String) -> Unit,
    onDismiss: (String) -> Unit,
    innerPadding: PaddingValues,
    rootPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) + rootPadding.bottom(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.new.isNotEmpty()) {
            item(key = "new-label") {
                SectionLabel(
                    text = stringResource(R.string.shared_with_you_section_new),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
            items(items = state.new, key = { it.invite.id }) { inviteUi ->
                NewInviteCard(
                    inviteUi = inviteUi,
                    onSave = { onSave(inviteUi.invite.id) },
                    onDismiss = { onDismiss(inviteUi.invite.id) },
                )
            }
        }
        if (state.earlier.isNotEmpty()) {
            item(key = "earlier-label") {
                SectionLabel(
                    text = stringResource(R.string.shared_with_you_section_earlier),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            item(key = "earlier-card") {
                SurfaceCard {
                    state.earlier.forEachIndexed { index, inviteUi ->
                        if (index > 0) InsetDivider(startInset = FeedRowDividerInset)
                        EarlierInviteRow(inviteUi = inviteUi)
                    }
                }
            }
        }
    }
}

/** A full "new" invite card: artwork, name, sharer + meta, optional note, and the three inbox actions. */
@Composable
private fun NewInviteCard(
    inviteUi: InviteUi,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val invite = inviteUi.invite
    SurfaceCard(contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArtworkThumb(
                artworkUrl = invite.playlistArtworkUrl,
                contentDescription = invite.playlistName,
                size = 64.dp,
                shape = RoundedCornerShape(12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = invite.playlistName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharerAvatar(name = invite.sharerName, avatarUrl = invite.sharerAvatarUrl)
                    Text(
                        text = stringResource(
                            R.string.shared_with_you_from_meta,
                            invite.sharerName,
                            inviteUi.relativeTime.toString(),
                            pluralStringResource(
                                R.plurals.shared_with_you_song_count,
                                invite.songCount,
                                invite.songCount,
                            ),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (invite.message != null) {
                    Text(
                        text = stringResource(R.string.shared_with_you_note, invite.message),
                        modifier = Modifier.padding(top = 3.dp),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledPill(
                text = stringResource(R.string.shared_with_you_save_to_library),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            OutlinedPill(
                text = stringResource(R.string.shared_with_you_preview),
                onClick = {},
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.shared_with_you_cd_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A compact already-seen invite row: artwork, name, sharer + time, and a "Saved" state. */
@Composable
private fun EarlierInviteRow(inviteUi: InviteUi) {
    val invite = inviteUi.invite
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumb(
            artworkUrl = invite.playlistArtworkUrl,
            contentDescription = invite.playlistName,
            size = 48.dp,
            shape = RoundedCornerShape(ThumbCornerRadius),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.playlistName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.shared_with_you_from_short,
                    invite.sharerName,
                    inviteUi.relativeTime.toString(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.shared_with_you_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- Shared small pieces ----------------------------------------------------------------------

/** A 48dp friend avatar: a live gradient ring when [live], initials from [name]. */
@Composable
private fun FriendAvatar(name: String, live: Boolean) {
    AvatarRing(size = 48.dp, hasLiveActivity = live) {
        val initial = name.firstOrNull()?.uppercase().orEmpty()
        if (initial.isEmpty()) PersonGlyphAvatar() else InitialsAvatar(text = initial)
    }
}

/** A small 18dp identity avatar for the sharer meta line. */
@Composable
private fun SharerAvatar(name: String, avatarUrl: String?) {
    val initial = name.firstOrNull()?.uppercase().orEmpty()
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    } else if (initial.isEmpty()) {
        PersonGlyphAvatar(size = 18.dp)
    } else {
        InitialsAvatar(
            text = initial,
            size = 18.dp,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** The small "IN A JAM" presence badge shown next to a hosting friend's name. */
@Composable
private fun JamBadge() {
    Text(
        text = stringResource(R.string.friend_activity_in_a_jam).uppercase(),
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** A rounded artwork thumbnail with a themed placeholder box behind it for null/loading art. */
@Composable
private fun ArtworkThumb(
    artworkUrl: String?,
    contentDescription: String?,
    size: Dp,
    shape: Shape = RoundedCornerShape(ThumbCornerRadius),
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

// ---- Non-feed states --------------------------------------------------------------------------

/** Centered spinner while the feed loads (only shown when social is enabled). */
@Composable
private fun LoadingState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** The "no activity / nothing shared yet" empty state — a centered card once data has loaded empty. */
@Composable
private fun EmptyState(
    message: String,
    innerPadding: PaddingValues,
    rootPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(rootPadding.bottom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The backend-off gated state: social is not available on this build. */
@Composable
private fun GatedState(
    innerPadding: PaddingValues,
    rootPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(rootPadding.bottom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SurfaceCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
            Text(
                text = stringResource(R.string.social_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.social_disabled_body),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
