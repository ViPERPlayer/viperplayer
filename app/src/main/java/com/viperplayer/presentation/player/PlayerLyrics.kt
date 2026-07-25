package com.viperplayer.presentation.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import android.provider.Settings
import com.viperplayer.R
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsBehavior
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsLine
import com.viperplayer.domain.model.LyricsOffset
import com.viperplayer.domain.model.LyricsSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Test tag on the lyrics loading shimmer, so UI tests can assert the loading state renders. */
const val LYRICS_LOADING_TAG = "lyrics-loading-shimmer"

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
    val loading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val translationEnabled by viewModel.translationEnabled.collectAsStateWithLifecycle()
    val translatedLines by viewModel.translatedLines.collectAsStateWithLifecycle()
    val romanizationEnabled by viewModel.romanizationEnabled.collectAsStateWithLifecycle()
    val romanizedLines by viewModel.romanizedLines.collectAsStateWithLifecycle()
    val settings by viewModel.lyricsSettings.collectAsStateWithLifecycle()
    val offsetMs by viewModel.lyricsOffsetMs.collectAsStateWithLifecycle()
    val overridden by viewModel.lyricsOverridden.collectAsStateWithLifecycle()
    val searchState by viewModel.lyricsSearch.collectAsStateWithLifecycle()

    // The line-selection position already carries the per-song timing offset (applied in the
    // ViewModel), so the renderer stays free of that logic. Re-polled when the offset changes so a
    // nudge takes effect immediately rather than at the next line boundary.
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(offsetMs) {
        while (isActive) {
            position = viewModel.getEffectiveLyricsPosition()
            // Poll faster for word-by-word lyrics so the highlight tracks each word smoothly.
            delay(if (lyrics?.wordSynced == true) 90L else 200L)
        }
    }

    LyricsSheetContent(
        lyrics = lyrics,
        loading = loading,
        position = position,
        translationEnabled = translationEnabled,
        translatedLines = translatedLines,
        romanizationEnabled = romanizationEnabled,
        romanizedLines = romanizedLines,
        settings = settings,
        offsetMs = offsetMs,
        overridden = overridden,
        onToggleTranslation = viewModel::toggleTranslation,
        onToggleRomanization = viewModel::toggleRomanization,
        onAdjustOffset = viewModel::adjustLyricsOffset,
        onResetOffset = viewModel::resetLyricsOffset,
        onOpenSearch = viewModel::openLyricsSearch,
        onRevertToAuto = viewModel::revertToAutoLyrics,
        onSeek = onSeek,
    )

    LyricsSearchSheet(
        state = searchState,
        onTitleChange = viewModel::updateLyricsSearchTitle,
        onArtistChange = viewModel::updateLyricsSearchArtist,
        onSearch = viewModel::searchLyrics,
        onPick = viewModel::pickLyricsCandidate,
        onDismiss = viewModel::dismissLyricsSearch,
    )
}

/**
 * Stateless body of the lyrics sheet — renders the header (title + translate/romanize toggles) and
 * the lyric lines (synced with active-line highlight, or plain scroll). Per line it shows, beneath
 * the original text, the romanization ([romanizedLines]) when [romanizationEnabled] and the
 * translation ([translatedLines]) when [translationEnabled]. Kept free of ViewModel/data access so
 * it can be exercised in Compose UI tests.
 *
 * Visual polish handled here: a shimmer placeholder while [loading]; a distinct empty/instrumental
 * state; soft top+bottom fade edges on the scrolling list; smooth auto-scroll that centers the active
 * line and steps aside for manual scrolling; and animated active-line emphasis (size/color/alpha +
 * per-word fill) that collapses to instant switches under reduced-motion.
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
    loading: Boolean = false,
    settings: LyricsSettings = LyricsSettings(),
    offsetMs: Long = 0L,
    overridden: Boolean = false,
    onAdjustOffset: (Int) -> Unit = {},
    onResetOffset: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onRevertToAuto: () -> Unit = {},
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
            LyricsOverflowMenu(
                overridden = overridden,
                onOpenSearch = onOpenSearch,
                onRevertToAuto = onRevertToAuto,
            )
        }

        // The per-song timing offset stepper — only meaningful for line-synced lyrics.
        if (lyrics?.synced == true && lyrics.lines.isNotEmpty()) {
            LyricsOffsetControl(
                offsetMs = offsetMs,
                onAdjustOffset = onAdjustOffset,
                onResetOffset = onResetOffset,
            )
        }

        when {
            // Still fetching: show a shimmer placeholder rather than a premature "no lyrics".
            loading && (lyrics == null || lyrics.isEmpty) -> LyricsLoadingState()

            lyrics == null || lyrics.isEmpty -> LyricsEmptyState()

            lyrics.synced && lyrics.lines.isNotEmpty() -> SyncedLyrics(
                lyrics = lyrics,
                position = position,
                translatedLines = translatedLines,
                romanizedLines = romanizedLines,
                settings = settings,
                onSeek = onSeek,
            )

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

/**
 * The lyrics overflow menu: "Search lyrics…" (the manual lyric-match picker) and, when a manual
 * override is active, "Use automatic lyrics" to revert.
 */
@Composable
private fun LyricsOverflowMenu(
    overridden: Boolean,
    onOpenSearch: () -> Unit,
    onRevertToAuto: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.lyrics_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.lyrics_search_action)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSearch()
                },
            )
            if (overridden) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lyrics_use_automatic)) },
                    leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRevertToAuto()
                    },
                )
            }
        }
    }
}

/**
 * Inline +/- stepper for the per-song lyrics timing offset. Steps by [LyricsOffset.STEP_MS] each
 * press; shows the current offset (signed, in seconds) and — when non-zero — a reset affordance.
 */
@Composable
private fun LyricsOffsetControl(
    offsetMs: Long,
    onAdjustOffset: (Int) -> Unit,
    onResetOffset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_offset_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onAdjustOffset(-1) }) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.lyrics_offset_decrease),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.lyrics_offset_value, offsetMs / 1000f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { onAdjustOffset(1) }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.lyrics_offset_increase),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (offsetMs != 0L) {
            IconButton(onClick = onResetOffset) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = stringResource(R.string.lyrics_offset_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The scrolling synced-lyrics list: soft fade edges, active line smoothly centered in the viewport
 * (yielding briefly to manual scrolling), and animated per-line emphasis with a smooth per-word fill.
 */
@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    position: Long,
    translatedLines: List<String>?,
    romanizedLines: List<String?>?,
    settings: LyricsSettings,
    onSeek: (Long) -> Unit,
) {
    val activeIndex = lyrics.currentLineIndex(position)
    val listState = rememberLazyListState()
    val reducedMotion = rememberReducedMotion()

    // Track the last manual drag so auto-scroll pauses briefly (then resumes) instead of fighting it.
    // Collecting DragInteraction.Start (a *user*-sourced signal) avoids counting our own programmatic
    // animateScrollToItem — which also flips isScrollInProgress — as a manual scroll.
    var lastUserInteractionMs by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                lastUserInteractionMs = System.currentTimeMillis()
            }
        }
    }

    // Auto-scroll: center the active line, honoring the toggle and the post-drag quiet period. Keyed
    // on lastUserInteractionMs too, so a manual drag both cancels the pending scroll (via re-keying)
    // and — after the quiet period — this effect re-runs and resumes following without waiting for the
    // next line boundary.
    LaunchedEffect(activeIndex, settings.autoScroll, lastUserInteractionMs) {
        if (activeIndex < 0 || !settings.autoScroll) return@LaunchedEffect

        // If we're still inside the post-drag quiet period, wait out the remainder, then resume. A
        // fresh interaction re-keys this effect and cancels the wait, so we never fight the user.
        if (lastUserInteractionMs >= 0) {
            val elapsed = System.currentTimeMillis() - lastUserInteractionMs
            val remaining = LyricsScrollMath.AUTO_SCROLL_RESUME_DELAY_MS - elapsed
            if (remaining > 0) delay(remaining)
        }

        val target = activeIndex.coerceAtMost(lyrics.lines.lastIndex)
        val viewportPx = listState.layoutInfo.viewportSize.height
        // When the target line isn't visible yet, its measured height is unknown, so we center on a
        // zero-height fallback (top lands at mid-viewport); it self-corrects once the line is on screen.
        val itemPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == target }?.size ?: 0
        val offset = -LyricsScrollMath.centeredScrollOffsetPx(viewportPx, itemPx)
        if (reducedMotion) {
            listState.scrollToItem(target, offset)
        } else {
            listState.animateScrollToItem(target, offset)
        }
    }

    val sungColor = highlightColor(settings.highlightColor)
    val pendingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val dimmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val undimmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val lineAlign = LyricsStyleMapping.textAlign(settings.alignment)
    val columnAlignment = when (settings.alignment) {
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .heightIn(max = 520.dp)
            .fadingEdges(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == activeIndex
            val dimmed = LyricsBehavior.shouldDimLine(index, activeIndex, settings.dimInactiveLines)
            val romanization = romanizedLines?.getOrNull(index)?.takeIf { it.isNotBlank() }
            val translation = translatedLines?.getOrNull(index)?.takeIf { it.isNotBlank() }

            LyricRow(
                line = line,
                active = active,
                dimmed = dimmed,
                position = position,
                settings = settings,
                reducedMotion = reducedMotion,
                sungColor = sungColor,
                pendingColor = pendingColor,
                dimmedColor = dimmedColor,
                undimmedColor = undimmedColor,
                secondaryColor = secondaryColor,
                lineAlign = lineAlign,
                columnAlignment = columnAlignment,
                romanization = romanization,
                translation = translation,
                onSeek = onSeek,
            )
        }
    }
}

/**
 * A single lyric line row with animated emphasis. The active line animates its size (via a scale
 * layer), color and alpha rather than snapping between states; word-synced lines fill each current
 * word smoothly. Under [reducedMotion] the transitions become instant. Tap-to-seek gives a ripple.
 */
@Composable
private fun LyricRow(
    line: LyricsLine,
    active: Boolean,
    dimmed: Boolean,
    position: Long,
    settings: LyricsSettings,
    reducedMotion: Boolean,
    sungColor: Color,
    pendingColor: Color,
    dimmedColor: Color,
    undimmedColor: Color,
    secondaryColor: Color,
    lineAlign: TextAlign,
    columnAlignment: Alignment.Horizontal,
    romanization: String?,
    translation: String?,
    onSeek: (Long) -> Unit,
) {
    val animSpec = tween<Color>(durationMillis = if (reducedMotion) 0 else 260)
    val floatSpec = tween<Float>(durationMillis = if (reducedMotion) 0 else 260)

    // The active line scales up toward activeLineScale; inactive lines sit at 1x. Animating a
    // graphicsLayer scale (rather than the font sp) keeps the animation cheap and avoids reflow jitter.
    val targetScale = if (active) settings.activeLineScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = floatSpec,
        label = "lyricScale",
    )
    // Whole-line target color (word-synced lines paint per-word instead, so they stay unspecified).
    val targetColor = when {
        active -> sungColor
        dimmed -> dimmedColor
        else -> undimmedColor
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = animSpec,
        label = "lyricColor",
    )

    // Tap-to-seek gives a ripple plus a subtle press "squeeze" so the seek affordance is legible.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (settings.tapToSeek && pressed) 0.97f else 1f,
        animationSpec = floatSpec,
        label = "lyricPressScale",
    )
    val rowModifier = Modifier
        .fillMaxWidth()
        .let {
            if (settings.tapToSeek) {
                it.clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onSeek(line.startMs) },
                )
            } else {
                it
            }
        }
        .padding(vertical = 2.dp)

    Column(
        modifier = rowModifier
            .graphicsLayer {
                scaleX = scale * pressScale
                scaleY = scale * pressScale
                transformOrigin = when (columnAlignment) {
                    Alignment.CenterHorizontally -> TransformOrigin(0.5f, 0.5f)
                    else -> TransformOrigin(0f, 0.5f)
                }
            },
        horizontalAlignment = columnAlignment
    ) {
        val lineText = if (active && line.words.isNotEmpty()) {
            buildWordFillText(line, position, sungColor, pendingColor)
        } else {
            AnnotatedString(line.text)
        }
        Text(
            text = lineText,
            color = if (active && line.words.isNotEmpty()) Color.Unspecified else animatedColor,
            textAlign = lineAlign,
            fontSize = LyricsStyleMapping.baseFontSize(settings.fontSize),
            fontWeight = if (active) {
                LyricsStyleMapping.activeFontWeight(settings.fontWeight)
            } else {
                LyricsStyleMapping.inactiveFontWeight(settings.fontWeight)
            },
        )
        if (romanization != null) {
            Text(
                text = romanization,
                color = secondaryColor,
                textAlign = lineAlign,
                fontSize = LyricsStyleMapping.subLineFontSize(settings.fontSize, active),
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (translation != null) {
            Text(
                text = translation,
                color = secondaryColor,
                textAlign = lineAlign,
                fontSize = LyricsStyleMapping.subLineFontSize(settings.fontSize, active),
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/**
 * Builds the word-by-word annotated text for the active line: fully-sung words take the accent color,
 * upcoming words stay dim, and the *current* word is blended between the two by its progress fraction
 * (a smooth fill rather than a hard on/off).
 */
private fun buildWordFillText(
    line: LyricsLine,
    position: Long,
    sungColor: Color,
    pendingColor: Color,
): AnnotatedString {
    val wordIndex = line.currentWordIndex(position)
    return buildAnnotatedString {
        line.words.forEachIndexed { i, word ->
            val color = when {
                i < wordIndex -> sungColor
                i > wordIndex -> pendingColor
                else -> {
                    // Current word: derive its end from the next word's start (or +600ms for the last).
                    val end = line.words.getOrNull(i + 1)?.startMs ?: (word.startMs + 600)
                    val fraction = LyricsScrollMath.wordProgressFraction(position, word.startMs, end)
                    lerpColor(pendingColor, sungColor, fraction)
                }
            }
            withStyle(SpanStyle(color = color)) { append(word.text) }
        }
    }
}

/** Linear color blend used for the per-word fill; kept local to avoid a Compose-graphics import churn. */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}

/** A shimmering placeholder shown while the current track's lyrics are still being fetched. */
@Composable
private fun LyricsLoadingState() {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "lyricsShimmer")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    // Under reduced motion, hold a steady mid-shimmer alpha instead of pulsing.
    val alpha = if (reducedMotion) 0.35f else pulseAlpha
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag(LYRICS_LOADING_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A few placeholder "lines" of varying width, like real lyrics.
        listOf(0.85f, 0.7f, 0.9f, 0.6f, 0.78f).forEach { widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(18.dp)
                    .background(base, RoundedCornerShape(6.dp))
            )
        }
    }
}

/** The clear empty state shown when a track genuinely has no lyrics (instrumental / none found). */
@Composable
private fun LyricsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.MusicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(R.string.lyrics_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Soft top+bottom fade so lyric lines dissolve in/out at the container edges instead of hard-clipping.
 * Draws the content to an offscreen layer, then multiplies a vertical alpha gradient over it.
 */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = (size.height * 0.12f).coerceAtMost(64.dp.toPx())
        // Top fade.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        // Bottom fade.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * True when the system's animator duration scale is 0 (the user turned animations off, i.e. the
 * platform equivalent of `prefers-reduced-motion`). The renderer then uses instant transitions.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}

/** Resolve the configured active-line highlight to a concrete theme color. */
@Composable
private fun highlightColor(color: LyricsHighlightColor): Color = when (color) {
    LyricsHighlightColor.PRIMARY -> MaterialTheme.colorScheme.primary
    LyricsHighlightColor.SECONDARY -> MaterialTheme.colorScheme.secondary
    LyricsHighlightColor.TERTIARY -> MaterialTheme.colorScheme.tertiary
}
