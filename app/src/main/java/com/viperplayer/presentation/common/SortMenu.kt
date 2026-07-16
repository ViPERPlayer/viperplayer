package com.viperplayer.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder

/** The string resource label for a [SortOption]. [nameLabel] uses "Name" instead of "Title". */
@StringRes
fun SortOption.labelRes(nameLabel: Boolean = false): Int = when (this) {
    SortOption.DEFAULT -> R.string.sort_order_default
    SortOption.TITLE -> if (nameLabel) R.string.sort_order_name else R.string.sort_order_title
    SortOption.ARTIST -> R.string.sort_order_artist
    SortOption.ALBUM -> R.string.sort_order_album
    SortOption.ALBUM_ARTIST -> R.string.sort_order_album_artist
    SortOption.DATE_ADDED -> R.string.sort_order_date_added
    SortOption.DATE_MODIFIED -> R.string.sort_order_date_modified
    SortOption.DURATION -> R.string.sort_order_duration
    SortOption.TRACK_NUMBER -> R.string.sort_order_track_number
    SortOption.YEAR -> R.string.sort_order_year
    SortOption.PLAY_COUNT -> R.string.sort_order_play_count
}

/**
 * A sort icon that opens a dropdown menu of [options] for choosing the list order.
 *
 * Stateless: it renders [current] and forwards choices to [onOrderChange]. Picking a different option
 * selects it (ascending); picking the already-selected option toggles its direction. [SortOption.DEFAULT]
 * has no direction — picking it just returns to the original order. The parent (a ViewModel) owns and
 * persists the resulting [SortOrder].
 *
 * @param options the orders offered for this view; the first should be [SortOption.DEFAULT].
 * @param useNameLabel render TITLE as "Name" (for artists/playlists) rather than "Title".
 */
@Composable
fun SortMenu(
    current: SortOrder,
    options: List<SortOption>,
    onOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
    useNameLabel: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("sortMenuButton"),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.action_sort),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEach { option ->
            val selected = option == current.option
            DropdownMenuItem(
                modifier = Modifier.testTag("sortOption_${option.name}"),
                text = { Text(stringResource(option.labelRes(useNameLabel))) },
                leadingIcon = {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        // Keep the label alignment stable whether or not a check is shown.
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                trailingIcon = {
                    // The direction arrow only applies to the currently-selected non-default option.
                    if (selected && option != SortOption.DEFAULT) {
                        Icon(
                            imageVector = if (current.direction == SortDirection.ASCENDING) {
                                Icons.Filled.ArrowUpward
                            } else {
                                Icons.Filled.ArrowDownward
                            },
                            contentDescription = stringResource(
                                if (current.direction == SortDirection.ASCENDING) {
                                    R.string.sort_direction_ascending
                                } else {
                                    R.string.sort_direction_descending
                                }
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                onClick = {
                    val next = when {
                        // DEFAULT has no direction — just return to the original order.
                        option == SortOption.DEFAULT -> SortOrder.DEFAULT
                        // Re-picking the selected option toggles its direction.
                        selected -> current.copy(direction = current.direction.toggled())
                        // Picking a new option selects it ascending.
                        else -> SortOrder(option, SortDirection.ASCENDING)
                    }
                    onOrderChange(next)
                    expanded = false
                },
            )
        }
    }
}
