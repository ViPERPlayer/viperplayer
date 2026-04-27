package com.viperplayer.presentation.viper.effect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.viperplayer.R
import com.viperplayer.domain.model.ViperConvolverState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider
import java.io.File

@Composable
fun ConvolverEffect(
    state: ViperConvolverState,
    kernelFiles: List<File>,
    onEnabledChange: (Boolean) -> Unit,
    onImportImpulse: (String) -> Unit,
    onSelectImpulse: (String) -> Unit,
    onDeleteImpulse: (String) -> Unit,
    onCrossChannelChange: (Int) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportImpulse(it.toString()) }
    }

    var showDialog by rememberSaveable { mutableStateOf(false) }

    Effect(
        icon = painterResource(R.drawable.ic_convolver),
        title = "Convolver",
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        Column {
            // File Selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Impulse Response",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )
                Text(
                    text = if (state.impulseResponse != null) {
                        // Simplify display if it's a path
                        File(state.impulseResponse).name
                    } else "Not set",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            // Cross Channel Slider
            ValueSlider(
                title = "Cross Channel",
                summary = state.crossChannel.toString(),
                summaryUnit = "%",
                value = state.crossChannel,
                onValueChange = onCrossChannelChange,
                onValueReset = { onCrossChannelChange(0) },
                valueRange = 0..100
            )
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .fillMaxHeight(0.8f)
            ) {
                Text(
                    text = "Select Kernel File",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    if (kernelFiles.isEmpty()) {
                        Text(
                            text = "No files loaded",
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        kernelFiles.forEach { file ->
                            val isSelected = file.name == state.impulseResponse
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            onSelectImpulse(file.name)
                                            showDialog = false
                                        }
                                    )
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = file.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onDeleteImpulse(file.name) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { launcher.launch(arrayOf("*/*")) }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Add new")
                    }

                    Button(onClick = { showDialog = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}