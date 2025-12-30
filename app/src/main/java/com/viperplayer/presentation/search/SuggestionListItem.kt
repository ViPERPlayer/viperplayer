package com.viperplayer.presentation.search

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HistoryListItem(
    suggestion: String,
    onRemove: () -> Unit,
    onInsert: () -> Unit,
    modifier: Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                suggestion,
                overflow = TextOverflow.StartEllipsis,
                maxLines = 1
            )
        },
        leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onRemove
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                }
                IconButton(
                    onClick = onInsert
                ) {
                    Icon(Icons.Rounded.NorthWest, contentDescription = "Insert")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}