package com.viperplayer.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R

/**
 * Full-screen player UI.
 * Displayed when user taps the mini player or navigates to NowPlaying.
 */
@Composable
fun PlayerScreen(
    contentWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showQueueBottomSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(contentWindowInsets),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Now playing"
            )

            uiState.playingFrom?.let { playingFrom ->
                Text(
                    text = playingFrom,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE
                    ),
                    maxLines = 1,
                )
            }
        }

        val items = remember {
            listOf(
                "Item 1",
                "Item 2",
                "Item 3"
            )
        }

        val pagerState = rememberPagerState(
            pageCount = {
                items.size
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val item = items[page]

            AsyncImage(
                model = item,
                contentDescription = "Artwork",
                placeholder = painterResource(R.drawable.ic_launcher_foreground)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Title",
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE
                        )
                    )

                    Text(
                        text = "Artist with very long name that does not fit in a single line, with an even longer name just to be sure",
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE
                        )
                    )
                }
            }

            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {  },
                            modifier = Modifier.animateWidth(interactionSource).weight(1f),
                            interactionSource = interactionSource,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    },
                    menuContent = {
                        // Empty
                    }
                )

                customItem(
                    buttonGroupContent = {
                        var checked by remember { mutableStateOf(false) }
                        val interactionSource = remember { MutableInteractionSource() }
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.animateWidth(interactionSource).weight(2f),
                            interactionSource = interactionSource
                        ) {
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Outlined.Pause,
                                    contentDescription = "Pause",
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    },
                    menuContent = {
                        // Empty
                    }
                )

                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {  },
                            modifier = Modifier.animateWidth(interactionSource).weight(1f),
                            interactionSource = interactionSource,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    },
                    menuContent = {
                        // Empty
                    }
                )
            }
        }

        Spacer(Modifier.height(300.dp))
    }

    if (showQueueBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showQueueBottomSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            QueueScreen()
        }
    }
}

