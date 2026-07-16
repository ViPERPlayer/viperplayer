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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Spellcheck
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsBehavior
import com.viperplayer.domain.model.LyricsHighlightColor
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
    val settings by viewModel.lyricsSettings.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            position = viewModel.getCurrentPosition()
            // Poll faster for word-by-word lyrics so the highlight tracks each word smoothly.
            delay(if (lyrics?.wordSynced == true) 90L else 200L)
        }
    }

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
            IconButton(onClick = { viewModel.toggleRomanization() }) {
                Icon(
                    imageVector = Icons.Filled.Spellcheck,
                    contentDescription = stringResource(R.string.lyrics_romanize),
                    tint = if (romanizationEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = { viewModel.toggleTranslation() }) {
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

        val current = lyrics
        when {
            current == null || current.isEmpty -> {
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

            current.synced && current.lines.isNotEmpty() -> {
                val activeIndex = current.currentLineIndex(position)
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex, settings.autoScroll) {
                    // Auto-scroll honors its toggle; when off, the user scrolls freely.
                    if (settings.autoScroll && activeIndex >= 0) {
                        listState.animateScrollToItem(
                            index = activeIndex.coerceAtMost(current.lines.lastIndex),
                            scrollOffset = -200
                        )
                    }
                }
                val sungColor = highlightColor(settings.highlightColor)
                val pendingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                // Inactive lines dim only when the "dim inactive lines" behavior is on.
                val dimmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                val undimmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                val translationColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                val translations = translatedLines
                val lineAlign = LyricsStyleMapping.textAlign(settings.alignment)
                val columnAlignment = when (settings.alignment) {
                    LyricsAlignment.CENTER -> Alignment.CenterHorizontally
                    else -> Alignment.Start
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 520.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(current.lines) { index, line ->
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
                        val dimmed = LyricsBehavior.shouldDimLine(index, activeIndex, settings.dimInactiveLines)
                        val translation = translations?.getOrNull(index)?.takeIf { it.isNotBlank() }
                        val rowModifier = Modifier
                            .fillMaxWidth()
                            .let { if (settings.tapToSeek) it.clickable { onSeek(line.startMs) } else it }
                            .padding(vertical = 2.dp)
                        Column(
                            modifier = rowModifier,
                            horizontalAlignment = columnAlignment
                        ) {
                            Text(
                                text = lineText,
                                color = when {
                                    active && line.words.isNotEmpty() -> Color.Unspecified
                                    active -> sungColor
                                    dimmed -> dimmedColor
                                    else -> undimmedColor
                                },
                                textAlign = lineAlign,
                                fontSize = if (active) {
                                    LyricsStyleMapping.activeFontSize(settings.fontSize, settings.activeLineScale)
                                } else {
                                    LyricsStyleMapping.baseFontSize(settings.fontSize)
                                },
                                fontWeight = if (active) {
                                    LyricsStyleMapping.activeFontWeight(settings.fontWeight)
                                } else {
                                    LyricsStyleMapping.inactiveFontWeight(settings.fontWeight)
                                },
                            )
                            // Per-line translation: smaller and dimmer, never cropped (wraps freely).
                            if (translation != null) {
                                Text(
                                    text = translation,
                                    color = translationColor,
                                    textAlign = lineAlign,
                                    fontSize = LyricsStyleMapping.subLineFontSize(settings.fontSize, active),
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
                    text = current.plainText.orEmpty(),
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

/** Resolve the configured active-line highlight to a concrete theme color. */
@Composable
private fun highlightColor(color: LyricsHighlightColor): Color = when (color) {
    LyricsHighlightColor.PRIMARY -> MaterialTheme.colorScheme.primary
    LyricsHighlightColor.SECONDARY -> MaterialTheme.colorScheme.secondary
    LyricsHighlightColor.TERTIARY -> MaterialTheme.colorScheme.tertiary
}
