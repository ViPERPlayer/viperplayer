package com.viperplayer.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.TagDetails
import com.viperplayer.presentation.theme.Spacing
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    viewModel: TagDetailsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagDetailsScreenContent(
        rootPadding = rootPadding,
        state = state,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Pure, state-rendering content for the Tag-details screen: a scrollable, labeled key/value list
 * grouped into Tags / File / Audio sections. Only rows whose value is present are emitted (empties are
 * omitted). Split out from the VM-backed [TagDetailsScreen] so it can be driven directly in tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailsScreenContent(
    rootPadding: PaddingValues,
    state: TagDetailsUiState,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { stringResource(R.string.tag_details_title) },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        when (state) {
            is TagDetailsUiState.Loading -> LoadingContent(innerPadding)
            is TagDetailsUiState.Empty -> EmptyContent(innerPadding)
            is TagDetailsUiState.Loaded -> LoadedContent(state.details, rootPadding, innerPadding)
        }
    }
}

@Composable
private fun LoadingContent(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("tagDetailsLoading"))
    }
}

@Composable
private fun EmptyContent(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tag_details_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("tagDetailsEmpty"),
        )
    }
}

@Composable
private fun LoadedContent(
    details: TagDetails,
    rootPadding: PaddingValues,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 22.dp)
            .testTag("tagDetailsList"),
        contentPadding = PaddingValues(bottom = rootPadding.calculateBottomPadding() + Spacing.lg),
    ) {
        // ---- Tags ----
        item {
            val tagRows = buildTagRows(details)
            if (tagRows.isNotEmpty()) {
                DetailSectionLabel(stringResource(R.string.tag_details_section_tags), top = Spacing.sm)
                DetailRowsCard(tagRows)
            }
        }

        // ---- File ----
        item {
            val fileRows = buildFileRows(details)
            if (fileRows.isNotEmpty()) {
                DetailSectionLabel(stringResource(R.string.tag_details_section_file), top = Spacing.xl)
                DetailRowsCard(fileRows)
            }
        }

        // ---- Audio ----
        item {
            val audioRows = buildAudioRows(details)
            if (audioRows.isNotEmpty()) {
                DetailSectionLabel(stringResource(R.string.tag_details_section_audio), top = Spacing.xl)
                DetailRowsCard(audioRows)
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun buildTagRows(d: TagDetails): List<Pair<String, String>> = buildList {
    d.title?.let { add(stringResource(R.string.tag_details_field_title) to it) }
    d.artists.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.tag_details_field_artist) to it.joinToString(", ")) }
    d.album?.let { add(stringResource(R.string.tag_details_field_album) to it) }
    d.albumArtist?.let { add(stringResource(R.string.tag_details_field_album_artist) to it) }
    d.composer?.let { add(stringResource(R.string.tag_details_field_composer) to it) }
    d.genre?.let { add(stringResource(R.string.tag_details_field_genre) to it) }
    d.date?.let { add(stringResource(R.string.tag_details_field_date) to it) }
    d.trackNumber?.let { add(stringResource(R.string.tag_details_field_track) to positionText(it, d.trackTotal)) }
    d.discNumber?.let { add(stringResource(R.string.tag_details_field_disc) to positionText(it, d.discTotal)) }
    d.comment?.let { add(stringResource(R.string.tag_details_field_comment) to it) }
    d.encoder?.let { add(stringResource(R.string.tag_details_field_encoder) to it) }
}

@Composable
private fun buildFileRows(d: TagDetails): List<Pair<String, String>> = buildList {
    d.fileName?.let { add(stringResource(R.string.tag_details_field_file_name) to it) }
    d.filePath?.let { add(stringResource(R.string.tag_details_field_path) to it) }
    d.container?.let { add(stringResource(R.string.tag_details_field_format) to it) }
    d.mimeType?.let { add(stringResource(R.string.tag_details_field_mime) to it) }
    d.fileSizeBytes?.let { add(stringResource(R.string.tag_details_field_size) to formatFileSize(it)) }
}

@Composable
private fun buildAudioRows(d: TagDetails): List<Pair<String, String>> = buildList {
    d.durationMs?.let { add(stringResource(R.string.tag_details_field_duration) to formatDurationMs(it)) }
    d.bitrate?.let { add(stringResource(R.string.tag_details_field_bitrate) to stringResource(R.string.tag_details_value_kbps, it / 1000)) }
    d.sampleRate?.let { add(stringResource(R.string.tag_details_field_sample_rate) to stringResource(R.string.tag_details_value_hz, it)) }
    d.channelCount?.let { add(stringResource(R.string.tag_details_field_channels) to it.toString()) }
    d.bitsPerSample?.let { add(stringResource(R.string.tag_details_field_bit_depth) to stringResource(R.string.tag_details_value_bit, it)) }
    d.coverArtBytes?.let { add(stringResource(R.string.tag_details_field_cover_art) to formatFileSize(it.toLong())) }
}

/** "5" or "5 / 12" — a track/disc number optionally out of a total. */
@Composable
private fun positionText(number: Int, total: Int?): String =
    total?.let { stringResource(R.string.tag_details_position_of, number, it) } ?: number.toString()

@Composable
private fun DetailSectionLabel(text: String, top: Dp = 0.dp) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = Spacing.sm),
    )
}

@Composable
private fun DetailRowsCard(rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.lg))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
    ) {
        rows.forEachIndexed { i, (label, value) ->
            DetailRow(label, value, divider = i < rows.lastIndex)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, divider: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
    if (divider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

private fun formatDurationMs(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(m, s)
}

/** Human-readable file size (B / KB / MB), matching common tag-viewer formatting. */
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
