package com.viperplayer.alarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.domain.AlarmContentSpec
import com.viperplayer.domain.model.Playlist
import java.time.DayOfWeek
import kotlin.math.roundToInt

/**
 * Bottom-sheet editor for a single alarm: time picker, day-of-week chips, content picker, volume +
 * fade-in sliders, and vibrate toggle. Entirely stateless w.r.t. persistence — it edits a local copy
 * and hands the result back through [onSave]; the caller (the screen) persists + schedules it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(
    initial: Alarm,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    var days by remember { mutableStateOf(initial.daysOfWeek) }
    var label by remember { mutableStateOf(initial.label) }
    var content by remember { mutableStateOf(initial.content) }
    var volume by remember { mutableStateOf(initial.volume) }
    var fadeInSec by remember { mutableStateOf(initial.fadeInDurationSec) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial.id == 0L) R.string.alarm_new else R.string.alarm_edit
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            TimePicker(
                state = timeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            SectionLabel(stringResource(R.string.alarm_repeat))
            DayOfWeekSelector(
                selected = days,
                onToggleDay = { day ->
                    days = if (day in days) days - day else days + day
                },
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.alarm_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.alarm_content))
            ContentSelector(
                content = content,
                playlists = playlists,
                onSelect = { content = it },
            )

            SectionLabel(
                stringResource(R.string.alarm_volume) + "  ${(volume * 100).roundToInt()}%"
            )
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(
                stringResource(R.string.alarm_fade_in) + "  " +
                    if (fadeInSec <= 0) stringResource(R.string.alarm_fade_in_off)
                    else stringResource(R.string.alarm_fade_in_seconds, fadeInSec)
            )
            Slider(
                value = fadeInSec.toFloat(),
                onValueChange = { fadeInSec = it.roundToInt() },
                valueRange = 0f..60f,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.alarm_vibrate),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.alarm_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(
                    onClick = {
                        onSave(
                            initial.copy(
                                hour = timeState.hour,
                                minute = timeState.minute,
                                daysOfWeek = days,
                                label = label.trim(),
                                content = content,
                                volume = volume,
                                fadeInDurationSec = fadeInSec,
                                vibrate = vibrate,
                                // Editing a spent one-shot re-arms it.
                                enabled = true,
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfWeekSelector(
    selected: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AlarmFormatting.orderedDays().forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggleDay(day) },
                label = {
                    Text(
                        text = AlarmFormatting.dayShortLabel(context, day).take(2),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentSelector(
    content: AlarmContentSpec,
    playlists: List<Playlist>,
    onSelect: (AlarmContentSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FilterChip(
            selected = content is AlarmContentSpec.ShuffleAll,
            onClick = { onSelect(AlarmContentSpec.ShuffleAll) },
            label = { Text(stringResource(R.string.alarm_content_shuffle_all)) },
        )
        playlists.forEach { playlist ->
            val selectedContent = content as? AlarmContentSpec.PlaylistContent
            FilterChip(
                selected = selectedContent?.playlistId == playlist.id,
                onClick = {
                    onSelect(
                        AlarmContentSpec.PlaylistContent(
                            playlistId = playlist.id,
                            shuffle = selectedContent?.shuffle ?: false,
                        )
                    )
                },
                label = { Text(playlist.name) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
