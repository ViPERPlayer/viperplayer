package com.viperplayer.presentation.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import java.util.concurrent.TimeUnit

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    viewModel: SongInfoViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val audioFormat by viewModel.audioFormat.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val song = state.song ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.song_info_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { shareSong(context, song) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = rootPadding.calculateBottomPadding() + 16.dp),
        ) {
            // Hero row
            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        song.artistNames?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            // Artist / Album navigation tiles
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    state.artist?.let { artist ->
                        NavTile(
                            icon = Icons.Filled.Person,
                            role = stringResource(R.string.song_info_artist_role),
                            name = artist.name,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToArtist(artist) },
                        )
                    }
                    state.album?.let { album ->
                        NavTile(
                            icon = Icons.Filled.Album,
                            role = stringResource(R.string.song_info_album_role),
                            name = album.name,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToAlbum(album) },
                        )
                    }
                }
            }

            // Source plugin
            item {
                SectionLabel(stringResource(R.string.song_info_source_plugin), top = 20.dp)
                PluginRow(
                    pluginPackage = state.pluginPackage,
                    pluginName = state.pluginName ?: state.pluginPackage,
                    onClick = { openPlugin(context, state.pluginPackage) },
                )
            }

            // Audio format (runtime — only for the playing track)
            audioFormat?.takeIf { it.hasAny() }?.let { fmt ->
                item {
                    SectionLabel(stringResource(R.string.song_detail_audio_format), top = 20.dp)
                    AudioFormatGrid(fmt)
                }
            }

            // Normalization
            if (song.replayGainDb != null || song.peakAmplitude != null) {
                item {
                    SectionLabel(stringResource(R.string.song_info_normalization), top = 20.dp)
                    RowsCard {
                        val rows = buildList {
                            song.replayGainDb?.let {
                                add(stringResource(R.string.song_info_replaygain_track) to String.format("%+.1f dB", it))
                            }
                            song.peakAmplitude?.let {
                                add(stringResource(R.string.song_detail_peak_amplitude) to String.format("%.3f", it))
                            }
                        }
                        rows.forEachIndexed { i, (label, value) -> InfoRow(label, value, divider = i < rows.lastIndex) }
                    }
                }
            }

            // Track
            item {
                SectionLabel(stringResource(R.string.song_info_track), top = 18.dp)
                RowsCard {
                    val rows = buildList {
                        song.album?.let { add(stringResource(R.string.song_detail_album) to it.name) }
                        song.trackNumber?.let { track ->
                            val total = song.album?.trackCount?.takeIf { it > 0 }
                            add(
                                stringResource(R.string.song_detail_track_number) to
                                    (total?.let { stringResource(R.string.song_info_number_of, track, it) } ?: track.toString())
                            )
                        }
                        song.discNumber?.let { add(stringResource(R.string.song_detail_disc_number) to it.toString()) }
                        song.durationMs?.takeIf { it > 0 }?.let { add(stringResource(R.string.song_detail_duration) to formatDuration(it)) }
                    }
                    rows.forEachIndexed { i, (label, value) -> InfoRow(label, value, divider = i < rows.lastIndex) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun AudioFormat.hasAny(): Boolean =
    codec != null || sampleRate != null || bitDepth != null || bitrate != null || channelCount != null

@Composable
private fun AudioFormatGrid(fmt: AudioFormat) {
    // Build the visible cells (unit-suffixed), then lay them out 3-up.
    val cells = buildList {
        fmt.codec?.let { add(StatCellData(stringResource(R.string.song_info_format), it, null)) }
        fmt.sampleRate?.let { add(StatCellData(stringResource(R.string.song_detail_sample_rate), (it / 1000f).let { r -> if (r % 1f == 0f) r.toInt().toString() else r.toString() }, stringResource(R.string.song_info_unit_khz))) }
        fmt.bitDepth?.let { add(StatCellData(stringResource(R.string.song_info_depth), it.toString(), stringResource(R.string.song_info_unit_bit))) }
        fmt.bitrate?.let { add(StatCellData(stringResource(R.string.song_detail_bitrate), "%,d".format(it), stringResource(R.string.song_info_unit_kbps))) }
        fmt.channelCount?.let {
            val name = when (it) {
                1 -> stringResource(R.string.channels_mono)
                2 -> stringResource(R.string.channels_stereo)
                else -> stringResource(R.string.song_detail_channel_count, it)
            }
            add(StatCellData(stringResource(R.string.song_detail_channels), name, "${it}${stringResource(R.string.song_info_unit_ch)}"))
        }
        fmt.lossless?.let {
            add(StatCellData(stringResource(R.string.song_info_codec), stringResource(if (it) R.string.song_info_lossless else R.string.song_info_lossy), null))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCells.forEach { StatCell(it, Modifier.weight(1f)) }
                repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class StatCellData(val label: String, val value: String, val unit: String?)

@Composable
private fun StatCell(data: StatCellData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            data.label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
            Text(data.value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            data.unit?.let {
                Text(
                    " $it",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavTile(icon: ImageVector, role: String, name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(role.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PluginRow(pluginPackage: String, pluginName: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(pluginPackage) {
        runCatching { context.packageManager.getApplicationIcon(pluginPackage).toBitmap().asImageBitmap() }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(pluginName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluginPackage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 8.dp),
    )
}

@Composable
private fun RowsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String, divider: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    if (divider) {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

private fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(m, s)
}

/** Open the source plugin: its own launcher screen if it has one, else the system app-details page. */
private fun openPlugin(context: android.content.Context, pluginPackage: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(pluginPackage)
    val intent = launch ?: Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", pluginPackage, null)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareSong(context: android.content.Context, song: Song) {
    val text = buildString {
        append(song.title)
        song.artistNames?.let { append(" — $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}
