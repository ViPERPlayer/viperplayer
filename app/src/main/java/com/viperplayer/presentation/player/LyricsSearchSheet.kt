package com.viperplayer.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.domain.model.LyricsCandidate

/**
 * The manual lyric-match picker (feature-parity gap B). Shown when [LyricsSearchState.isOpen]. Lets
 * the user edit the title/artist (prefilled from the current song), run a provider search, and pick
 * a candidate to apply as this song's override. Stateless beyond the passed [state] — all logic
 * lives in the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchSheet(
    state: LyricsSearchState,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (LyricsCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isOpen) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.lyrics_search_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.lyrics_search_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.artist,
                onValueChange = onArtistChange,
                label = { Text(stringResource(R.string.lyrics_search_field_artist)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSearch,
                enabled = state.title.isNotBlank() && state.phase != LyricsSearchState.Phase.Searching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.lyrics_search_button),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (val phase = state.phase) {
                LyricsSearchState.Phase.Idle -> Unit
                LyricsSearchState.Phase.Searching -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is LyricsSearchState.Phase.Results ->
                    if (phase.results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.lyrics_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(phase.results) { candidate ->
                                LyricsCandidateRow(candidate = candidate, onClick = { onPick(candidate) })
                            }
                        }
                    }
            }
        }
    }
}

/** A single lyric-match candidate: title, artist·album line, and a synced/plain + duration badge. */
@Composable
private fun LyricsCandidateRow(
    candidate: LyricsCandidate,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (candidate.synced) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.trackName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(candidate.artistName, candidate.albumName)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val syncLabel = stringResource(
            if (candidate.synced) R.string.lyrics_search_synced else R.string.lyrics_search_plain
        )
        val badge = candidate.durationMs
            ?.let { "$syncLabel · ${formatDuration(it)}" }
            ?: syncLabel
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** m:ss formatting for a candidate's track duration. */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
