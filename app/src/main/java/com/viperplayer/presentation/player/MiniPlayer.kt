package com.viperplayer.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Mini player that appears above the bottom navigation bar.
 * Shows current track info and basic playback controls.
 */
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onExpandClick),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Track info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Track Title",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Artist Name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* TODO: Toggle play/pause */ }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }
                IconButton(onClick = { /* TODO: Skip to next */ }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next"
                    )
                }
            }
        }
    }
}

