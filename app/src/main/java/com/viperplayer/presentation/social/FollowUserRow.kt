package com.viperplayer.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.domain.social.FollowUser
import com.viperplayer.presentation.common.components.FilledPill
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.OutlinedPill
import com.viperplayer.presentation.common.components.PersonGlyphAvatar
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.common.components.avatarTonesFor
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.theme.Spacing

/** Avatar diameter for a follow-graph user row. */
private val FollowAvatarSize = 44.dp

/**
 * A single follow-graph user row (shared by Find people + Following): a tinted initials avatar, the
 * `@handle` over the display name, and a trailing Follow / Following toggle. [isFollowing] drives the
 * toggle style — a filled "Follow" pill when not yet following, an outlined "Following" pill once
 * followed. [onToggleFollow] fires the follow/unfollow (handled optimistically in the ViewModel).
 */
@Composable
fun FollowUserRow(
    user: FollowUser,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FollowAvatar(handle = user.handle, displayName = user.displayName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = handleLabel(user.handle),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.displayName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isFollowing) {
                OutlinedPill(
                    text = stringResource(R.string.follow_action_following),
                    onClick = onToggleFollow,
                )
            } else {
                FilledPill(
                    text = stringResource(R.string.follow_action_follow),
                    onClick = onToggleFollow,
                )
            }
        }
    }
}

/** A 44dp identity avatar tinted deterministically from the handle so each user keeps a stable tone. */
@Composable
private fun FollowAvatar(handle: String, displayName: String) {
    val initial = displayName.firstOrNull()?.uppercase()
        ?: handle.firstOrNull()?.uppercase().orEmpty()
    if (initial.isEmpty()) {
        PersonGlyphAvatar(size = FollowAvatarSize)
    } else {
        val (container, content) = avatarTonesFor(handle.ifBlank { displayName }, MaterialTheme.colorScheme)
        InitialsAvatar(
            text = initial,
            size = FollowAvatarSize,
            containerColor = container,
            contentColor = content,
        )
    }
}

/** Formats a bare handle as `@handle` (leaving an already-prefixed or blank value untouched). */
private fun handleLabel(handle: String): String = when {
    handle.isBlank() -> ""
    handle.startsWith("@") -> handle
    else -> "@$handle"
}

/** The backend-off gated state shared by the follow-graph screens: social is unavailable on this build. */
@Composable
fun SocialGatedState(
    innerPadding: PaddingValues,
    rootPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .padding(rootPadding.bottom()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SurfaceCard(contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 28.dp)) {
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
                    .padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
