package com.viperplayer.presentation.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.viperplayer.R

@Composable
fun SuggestionListItem(
    suggestion: String,
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
        leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingContent = {
            IconButton(
                onClick = onInsert
            ) {
                Icon(
                    Icons.Rounded.NorthWest,
                    contentDescription = stringResource(R.string.search_insert_query)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}