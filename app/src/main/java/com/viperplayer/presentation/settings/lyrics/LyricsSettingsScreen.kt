package com.viperplayer.presentation.settings.lyrics

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.R
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom

@Composable
fun LyricsSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LyricsSettingsContent(
        settings = uiState,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onFontSize = viewModel::setFontSize,
        onAlignment = viewModel::setAlignment,
        onFontWeight = viewModel::setFontWeight,
        onHighlightColor = viewModel::setHighlightColor,
        onActiveLineScale = viewModel::setActiveLineScale,
        onAutoScroll = viewModel::setAutoScroll,
        onTapToSeek = viewModel::setTapToSeek,
        onDimInactiveLines = viewModel::setDimInactiveLines,
        onShowTranslationByDefault = viewModel::setShowTranslationByDefault,
        onShowRomanizationByDefault = viewModel::setShowRomanizationByDefault,
        modifier = modifier,
    )
}

/**
 * Stateless body of the Lyrics settings screen — takes the resolved settings + event lambdas so it
 * can be driven directly from a Compose UI test without Hilt.
 */
@Composable
fun LyricsSettingsContent(
    settings: LyricsSettings,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onFontSize: (LyricsFontSize) -> Unit,
    onAlignment: (LyricsAlignment) -> Unit,
    onFontWeight: (LyricsFontWeight) -> Unit,
    onHighlightColor: (LyricsHighlightColor) -> Unit,
    onActiveLineScale: (Float) -> Unit,
    onAutoScroll: (Boolean) -> Unit,
    onTapToSeek: (Boolean) -> Unit,
    onDimInactiveLines: (Boolean) -> Unit,
    onShowTranslationByDefault: (Boolean) -> Unit,
    onShowRomanizationByDefault: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_lyrics)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = modifier
                .padding(contentPadding)
                .fillMaxWidth()
                .fillMaxSize(),
            contentPadding = rootPadding.bottom()
        ) {
            item { SettingsCategory(stringResource(R.string.lyrics_category_style)) }

            item {
                ChoiceItem(
                    title = stringResource(R.string.lyrics_font_size),
                    icon = Icons.Default.FormatSize,
                    options = LyricsFontSize.entries,
                    selected = settings.fontSize,
                    label = { fontSizeLabel(it) },
                    onSelect = onFontSize,
                )
            }
            item {
                ChoiceItem(
                    title = stringResource(R.string.lyrics_alignment),
                    icon = Icons.AutoMirrored.Default.FormatAlignLeft,
                    options = LyricsAlignment.entries,
                    selected = settings.alignment,
                    label = { alignmentLabel(it) },
                    onSelect = onAlignment,
                )
            }
            item {
                ChoiceItem(
                    title = stringResource(R.string.lyrics_font_weight),
                    icon = Icons.Default.FormatBold,
                    options = LyricsFontWeight.entries,
                    selected = settings.fontWeight,
                    label = { fontWeightLabel(it) },
                    onSelect = onFontWeight,
                )
            }
            item {
                ChoiceItem(
                    title = stringResource(R.string.lyrics_highlight_color),
                    icon = Icons.Default.FormatColorText,
                    options = LyricsHighlightColor.entries,
                    selected = settings.highlightColor,
                    label = { highlightColorLabel(it) },
                    onSelect = onHighlightColor,
                )
            }
            item {
                ActiveLineScaleItem(
                    scale = settings.activeLineScale,
                    onScaleChanged = onActiveLineScale,
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item { SettingsCategory(stringResource(R.string.lyrics_category_behavior)) }

            item {
                SwitchItem(
                    title = stringResource(R.string.lyrics_auto_scroll),
                    description = stringResource(R.string.lyrics_auto_scroll_desc),
                    icon = Icons.Default.SwipeVertical,
                    checked = settings.autoScroll,
                    onCheckedChange = onAutoScroll,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lyrics_tap_to_seek),
                    description = stringResource(R.string.lyrics_tap_to_seek_desc),
                    icon = Icons.Default.TouchApp,
                    checked = settings.tapToSeek,
                    onCheckedChange = onTapToSeek,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lyrics_dim_inactive),
                    description = stringResource(R.string.lyrics_dim_inactive_desc),
                    icon = Icons.Default.LowPriority,
                    checked = settings.dimInactiveLines,
                    onCheckedChange = onDimInactiveLines,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lyrics_show_translation_default),
                    description = stringResource(R.string.lyrics_show_translation_default_desc),
                    icon = Icons.Default.Translate,
                    checked = settings.showTranslationByDefault,
                    onCheckedChange = onShowTranslationByDefault,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.lyrics_show_romanization_default),
                    description = stringResource(R.string.lyrics_show_romanization_default_desc),
                    icon = Icons.Default.Spellcheck,
                    checked = settings.showRomanizationByDefault,
                    onCheckedChange = onShowRomanizationByDefault,
                )
            }
        }
    }
}

@Composable
private fun SettingsCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun <T> ChoiceItem(
    title: String,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            ListItem(
                leadingContent = {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(label(option)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Tapping the row toggles the switch (a common pattern that also makes the row testable).
            .toggleable(value = checked, onValueChange = onCheckedChange),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            leadingContent = {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        )
    }
}

@Composable
private fun ActiveLineScaleItem(
    scale: Float,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = {
                    Text(stringResource(R.string.lyrics_active_line_scale), style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.lyrics_active_line_scale_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.lyrics_active_line_scale_value, scale),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Slider(
                    value = scale,
                    onValueChange = onScaleChanged,
                    valueRange = LyricsSettings.MIN_ACTIVE_LINE_SCALE..LyricsSettings.MAX_ACTIVE_LINE_SCALE,
                    steps = 11, // 0.05x increments across 1.0..1.6
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
    }
}

@Composable
private fun fontSizeLabel(size: LyricsFontSize): String = when (size) {
    LyricsFontSize.SMALL -> stringResource(R.string.lyrics_font_size_small)
    LyricsFontSize.MEDIUM -> stringResource(R.string.lyrics_font_size_medium)
    LyricsFontSize.LARGE -> stringResource(R.string.lyrics_font_size_large)
}

@Composable
private fun alignmentLabel(alignment: LyricsAlignment): String = when (alignment) {
    LyricsAlignment.LEFT -> stringResource(R.string.lyrics_alignment_left)
    LyricsAlignment.CENTER -> stringResource(R.string.lyrics_alignment_center)
}

@Composable
private fun fontWeightLabel(weight: LyricsFontWeight): String = when (weight) {
    LyricsFontWeight.NORMAL -> stringResource(R.string.lyrics_font_weight_normal)
    LyricsFontWeight.BOLD -> stringResource(R.string.lyrics_font_weight_bold)
}

@Composable
private fun highlightColorLabel(color: LyricsHighlightColor): String = when (color) {
    LyricsHighlightColor.PRIMARY -> stringResource(R.string.lyrics_highlight_color_primary)
    LyricsHighlightColor.SECONDARY -> stringResource(R.string.lyrics_highlight_color_secondary)
    LyricsHighlightColor.TERTIARY -> stringResource(R.string.lyrics_highlight_color_tertiary)
}
