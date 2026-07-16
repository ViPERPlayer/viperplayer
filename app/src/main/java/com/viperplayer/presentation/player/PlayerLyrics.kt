package com.viperplayer.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Lyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The single-line lyric teaser shown on the player over the artwork. Shows the current synced line
 * (or the first non-blank line of plain lyrics), italic with a pulse glyph. Tapping opens the full
 * lyrics sheet. Render only when [lyrics] is non-null.
 */
@Composable
fun LyricLine(
    lyrics: Lyrics,
    positionMs: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Derived so a 60fps position tick only recomposes this row when the *line* actually changes.
    val lyricState = remember(lyrics) { derivedStateOf { currentLyricText(lyrics, positionMs()) } }
    val text = lyricState.value ?: return

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp)
        )
        // Lyrics are never cropped — the current line wraps to as many lines as it needs.
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
        )
    }
}

private fun currentLyricText(lyrics: Lyrics, positionMs: Long): String? {
    if (lyrics.synced && lyrics.lines.isNotEmpty()) {
        val idx = lyrics.currentLineIndex(positionMs)
        val line = lyrics.lines.getOrNull(if (idx >= 0) idx else 0)
        return line?.text?.takeIf { it.isNotBlank() }
    }
    return lyrics.plainText
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
}

/**
 * Full lyrics sheet. Synced lyrics scroll and highlight the active line (tap a line to seek there);
 * plain lyrics render as scrollable text.
 */
@Composable
fun LyricsSheet(
    viewModel: PlayerViewModel,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val translationEnabled by viewModel.translationEnabled.collectAsStateWithLifecycle()
    val translatedLines by viewModel.translatedLines.collectAsStateWithLifecycle()
    val romanizationEnabled by viewModel.romanizationEnabled.collectAsStateWithLifecycle()
    val romanizedLines by viewModel.romanizedLines.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            position = viewModel.getCurrentPosition()
            // Poll faster for word-by-word lyrics so the highlight tracks each word smoothly.
            delay(if (lyrics?.wordSynced == true) 90L else 200L)
        }
    }

    LyricsSheetContent(
        lyrics = lyrics,
        position = position,
        translationEnabled = translationEnabled,
        translatedLines = translatedLines,
        romanizationEnabled = romanizationEnabled,
        romanizedLines = romanizedLines,
        onToggleTranslation = viewModel::toggleTranslation,
        onToggleRomanization = viewModel::toggleRomanization,
        onSeek = onSeek,
    )
}

/**
 * Stateless body of the lyrics sheet — renders the header (title + translate/romanize toggles) and
 * the lyric lines (synced with active-line highlight, or plain scroll). Per line it shows, beneath
 * the original text, the romanization ([romanizedLines]) when [romanizationEnabled] and the
 * translation ([translatedLines]) when [translationEnabled]. Kept free of ViewModel/data access so
 * it can be exercised in Compose UI tests.
 */
@Composable
fun LyricsSheetContent(
    lyrics: Lyrics?,
    position: Long,
    translationEnabled: Boolean,
    translatedLines: List<String>?,
    romanizationEnabled: Boolean,
    romanizedLines: List<String?>?,
    onToggleTranslation: () -> Unit,
    onToggleRomanization: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lyrics_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleRomanization) {
                Icon(
                    imageVector = Icons.Filled.Abc,
                    contentDescription = stringResource(R.string.lyrics_romanize),
                    tint = if (romanizationEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onToggleTranslation) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = stringResource(R.string.lyrics_translate),
                    tint = if (translationEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        when {
            lyrics == null || lyrics.isEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            lyrics.synced && lyrics.lines.isNotEmpty() -> {
                val activeIndex = lyrics.currentLineIndex(position)
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) {
                        listState.animateScrollToItem(
                            index = activeIndex.coerceAtMost(lyrics.lines.lastIndex),
                            scrollOffset = -200
                        )
                    }
                }
                val sungColor = MaterialTheme.colorScheme.primary
                val pendingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 520.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val active = index == activeIndex
                        // On the active line with word timings, reveal it word-by-word: words already
                        // sung take the accent color, upcoming words stay dim. Otherwise the whole
                        // line highlights at once.
                        val lineText = if (active && line.words.isNotEmpty()) {
                            val wordIndex = line.currentWordIndex(position)
                            buildAnnotatedString {
                                line.words.forEachIndexed { i, word ->
                                    withStyle(SpanStyle(color = if (i <= wordIndex) sungColor else pendingColor)) {
                                        append(word.text)
                                    }
                                }
                            }
                        } else {
                            AnnotatedString(line.text)
                        }
                        val romanization = romanizedLines?.getOrNull(index)?.takeIf { it.isNotBlank() }
                        val translation = translatedLines?.getOrNull(index)?.takeIf { it.isNotBlank() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.startMs) }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = lineText,
                                color = when {
                                    active && line.words.isNotEmpty() -> Color.Unspecified
                                    active -> sungColor
                                    else -> inactiveColor
                                },
                                fontSize = if (active) 20.sp else 17.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                            // Per-line romanization: shown directly under the original (read-along),
                            // smaller and dimmer, never cropped (wraps freely).
                            if (romanization != null) {
                                Text(
                                    text = romanization,
                                    color = secondaryColor,
                                    fontSize = if (active) 15.sp else 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            // Per-line translation: smaller and dimmer, never cropped (wraps freely).
                            if (translation != null) {
                                Text(
                                    text = translation,
                                    color = secondaryColor,
                                    fontSize = if (active) 15.sp else 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = lyrics.plainText.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
