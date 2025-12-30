package com.viperplayer.presentation.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem

@Composable
fun ListItem(
    type: SearchItem.Type,
    title: String,
    badges: List<ItemBadge>,
    subtitle: String?,
    artworkUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (badges.isNotEmpty() || subtitle != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (badges.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                badges.forEach { badge ->
                                    val icon = when (badge) {
                                        ItemBadge.FAVORITE -> Icons.Rounded.Favorite
                                        ItemBadge.EXPLICIT -> Icons.Rounded.Explicit
                                        ItemBadge.LIBRARY -> Icons.Rounded.LibraryAddCheck
                                        ItemBadge.DOWNLOADING -> Icons.Rounded.Downloading
                                        ItemBadge.DOWNLOADED -> Icons.Rounded.Download
                                    }

                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (subtitle != null) {
                            Text(
                                subtitle,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(48.dp), // TODO: Do not hardcode this here!
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = "Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(
                            if (type == SearchItem.Type.ARTIST)
                                CircleShape
                            else
                                RoundedCornerShape(6.dp) // TODO: Do not hardcode this here!
                        )
                )

                AnimatedVisibility(
                    visible = isActive
                ) {
                    if (isPlaying) {
                        PlayingIndicator(
                            color = Color.White,
                            modifier = Modifier.height(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(
                onClick = {}
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More"
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}