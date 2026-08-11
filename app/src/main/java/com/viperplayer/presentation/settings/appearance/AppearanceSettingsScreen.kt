package com.viperplayer.presentation.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.ThemeMode
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.theme.ColorMath
import com.viperplayer.presentation.theme.Spacing

/** A named preset accent seed. The [name] doubles as the swatch's accessibility label. */
data class AccentPreset(val name: String, val color: Color)

/** Preset accent seeds offered before the custom color picker. Order is stable for tests. */
val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("Purple", Color(0xFF6750A4)), // brand default
    AccentPreset("Green", Color(0xFF386A20)),
    AccentPreset("Blue", Color(0xFF00639B)),
    AccentPreset("Red", Color(0xFFB3261E)),
    AccentPreset("Mauve", Color(0xFF7D5260)),
    AccentPreset("Orange", Color(0xFF8C4A00)),
    AccentPreset("Teal", Color(0xFF00696D)),
    AccentPreset("Gold", Color(0xFF6C5D00)),
)

/** Resolves the localized accessibility label for a preset's stable [AccentPreset.name] key. */
@Composable
private fun accentPresetLabel(name: String): String = when (name) {
    "Purple" -> stringResource(R.string.appearance_accent_purple)
    "Green" -> stringResource(R.string.appearance_accent_green)
    "Blue" -> stringResource(R.string.appearance_accent_blue)
    "Red" -> stringResource(R.string.appearance_accent_red)
    "Mauve" -> stringResource(R.string.appearance_accent_mauve)
    "Orange" -> stringResource(R.string.appearance_accent_orange)
    "Teal" -> stringResource(R.string.appearance_accent_teal)
    "Gold" -> stringResource(R.string.appearance_accent_gold)
    else -> name
}

@Composable
fun AppearanceSettingsScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppearanceSettingsContent(
        state = uiState,
        rootPadding = rootPadding,
        onNavigateBack = onNavigateBack,
        onDynamicThemeMode = viewModel::setDynamicThemeMode,
        onThemeMode = viewModel::setThemeMode,
        onPureBlack = viewModel::setPureBlack,
        onAccentColor = viewModel::setAccentColor,
        modifier = modifier
    )
}

/**
 * Stateless body: renders appearance state and forwards events. Kept Hilt-free so it can be driven
 * directly by Compose UI tests.
 */
@Composable
fun AppearanceSettingsContent(
    state: AppearanceSettingsUiState,
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onDynamicThemeMode: (DynamicThemeMode) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onAccentColor: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDynamicThemeDialog by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
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
            item {
                SettingsCategory(stringResource(R.string.appearance_category_theme))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.appearance_dynamic_theme),
                    description = getDynamicThemeDescription(state.dynamicThemeMode),
                    icon = Icons.Default.Palette,
                    onClick = { showDynamicThemeDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.appearance_theme_type),
                    description = getThemeDescription(state.themeMode),
                    icon = Icons.Default.Brightness6,
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                // Custom accent only takes effect when dynamic color is off; disable otherwise so the
                // user isn't misled into thinking a pick would override Material You / album art.
                AccentColorCard(
                    selectedArgb = state.accentColor,
                    enabled = state.dynamicThemeMode == DynamicThemeMode.OFF,
                    onSelect = onAccentColor,
                    onCustom = { showCustomColorDialog = true }
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.appearance_pure_black),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.appearance_pure_black_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = state.pureBlack,
                                onCheckedChange = onPureBlack,
                                enabled = state.themeMode == ThemeMode.DARK || state.themeMode == ThemeMode.SYSTEM
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = state.themeMode,
            onThemeSelected = { theme ->
                onThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showDynamicThemeDialog) {
        DynamicThemeDialog(
            currentMode = state.dynamicThemeMode,
            onModeSelected = { mode ->
                onDynamicThemeMode(mode)
                showDynamicThemeDialog = false
            },
            onDismiss = { showDynamicThemeDialog = false }
        )
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initial = state.accentColor?.let(::Color) ?: AccentPresets.first().color,
            onConfirm = { color ->
                onAccentColor(color.toArgb())
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorCard(
    selectedArgb: Int?,
    enabled: Boolean,
    onSelect: (Int?) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.appearance_accent_color),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.appearance_accent_color_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // "Default" clears the custom accent (falls back to the brand default seed).
                SwatchDot(
                    color = AccentPresets.first().color,
                    selected = selectedArgb == null,
                    enabled = enabled,
                    contentDescription = stringResource(R.string.appearance_accent_default),
                    onClick = { onSelect(null) }
                )
                // Preset seeds (skip index 0 — it's the default handled above). Show all remaining
                // presets so none is unreachable.
                AccentPresets.drop(1).forEach { preset ->
                    SwatchDot(
                        color = preset.color,
                        selected = selectedArgb == preset.color.toArgb(),
                        enabled = enabled,
                        contentDescription = accentPresetLabel(preset.name),
                        onClick = { onSelect(preset.color.toArgb()) }
                    )
                }
                // Custom color entry point.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = "custom_accent"
                        }
                        .then(if (enabled) Modifier.clickable(onClick = onCustom) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = stringResource(R.string.appearance_accent_custom),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = if (enabled) color else color.copy(alpha = 0.38f)
    // Pick a check-mark color that contrasts against the swatch (pure color math).
    val onColor = if (ColorMath.onColorFor(color.toArgb()) == ColorMath.WHITE) Color.White else Color.Black
    // Announce the selected state to screen readers, e.g. "Blue, Selected accent color".
    val selectedLabel = stringResource(R.string.appearance_accent_selected)
    val semanticsLabel = when {
        contentDescription == null -> null
        selected -> "$contentDescription, $selectedLabel"
        else -> contentDescription
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(dotColor, CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (semanticsLabel != null) {
                    Modifier.semantics { this.contentDescription = semanticsLabel }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
private fun CustomColorDialog(
    initial: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initial) { ColorMath.toHsv(initial.toArgb()) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val preview = Color(ColorMath.fromHsv(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_accent_custom_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(preview, MaterialTheme.shapes.medium)
                        .semantics { contentDescription = "custom_accent_preview" }
                )
                Text(
                    text = stringResource(R.string.appearance_accent_hue),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = Spacing.md)
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(
                    text = stringResource(R.string.appearance_accent_saturation),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text(
                    text = stringResource(R.string.appearance_accent_brightness),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(preview) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }
}

@Composable
private fun ThemeDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_theme_type)) },
        text = {
            Column {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(
                                text = getThemeDescription(theme),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@Composable
private fun getThemeDescription(theme: ThemeMode): String {
    return when (theme) {
        ThemeMode.LIGHT -> stringResource(R.string.appearance_theme_mode_light)
        ThemeMode.DARK -> stringResource(R.string.appearance_theme_mode_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.appearance_theme_mode_system)
    }
}

@Composable
private fun DynamicThemeDialog(
    currentMode: DynamicThemeMode,
    onModeSelected: (DynamicThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_dynamic_theme)) },
        text = {
            Column {
                DynamicThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(
                                text = getDynamicThemeDescription(mode),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@Composable
private fun getDynamicThemeDescription(mode: DynamicThemeMode): String {
    return when (mode) {
        DynamicThemeMode.OFF -> stringResource(R.string.appearance_dynamic_theme_off)
        DynamicThemeMode.DYNAMIC -> stringResource(R.string.appearance_dynamic_theme_dynamic)
        DynamicThemeMode.SYSTEM -> stringResource(R.string.appearance_dynamic_theme_system)
    }
}
