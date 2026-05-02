package com.viperplayer.presentation.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.rememberAsyncImagePainter
import com.viperplayer.R

private val MaxExpandedAppBarHeight: Dp = 480.dp

/**
 * A scaffold with a collapsing artwork app bar. The artwork fills the expanded area of a
 * [LargeTopAppBar] and collapses down to a single-line app bar with the title when scrolled.
 *
 * A gradient scrim is drawn over the bottom portion of the artwork so the title text
 * remains readable over any image.
 *
 * @param artworkUrl URL of the artwork to display in the collapsing header.
 * @param title Title shown in the app bar (large when expanded, small when collapsed).
 * @param onNavigateBack Callback for the back navigation icon.
 * @param modifier Modifier applied to the scaffold.
 * @param actions Additional action icons in the app bar (e.g. refresh).
 * @param content The screen content below the collapsing app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingArtworkScaffold(
    artworkUrl: String?,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Load artwork and derive expanded height from intrinsic aspect ratio
    val artworkPainter = rememberAsyncImagePainter(
        model = artworkUrl,
//        placeholder = painterResource(R.drawable.ic_notification),
//        error = painterResource(R.drawable.ic_notification),
    )

    val screenWidthDp = LocalWindowInfo.current.containerDpSize.width
    val expandedHeight by remember {
        derivedStateOf {
            val intrinsicSize = artworkPainter.intrinsicSize
            if (intrinsicSize != Size.Unspecified && intrinsicSize.width > 0f) {
                val aspectRatio = intrinsicSize.height / intrinsicSize.width
                val naturalHeight = screenWidthDp * aspectRatio
                min(naturalHeight, MaxExpandedAppBarHeight)
            } else {
                // Assume 1:1 aspect ratio
                min(screenWidthDp, MaxExpandedAppBarHeight)
            }
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val contentColor by remember(onSurface) {
        derivedStateOf {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            // Transition from White (expanded) to onSurface (collapsed)
            lerp(Color.White, onSurface, collapsedFraction)
        }
    }

    ViperScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val density = LocalDensity.current
            val topContentWindowInset = with(density) {
                ViperScaffoldDefaults.contentWindowInsets
                    .getTop(density)
                    .toDp()
            }

            Box {
                // Artwork as background — matchParentSize tracks
                // the LargeTopAppBar's dynamic height as it collapses
                Image(
                    painter = artworkPainter,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient scrim at the bottom for title readability
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.7f),
                                ),
                            )
                        )
                )

                // LargeTopAppBar overlaid on top
                val titleMaxLines by remember {
                    derivedStateOf {
                        if (scrollBehavior.state.collapsedFraction < 0.5f) 3 else 1
                    }
                }

                LargeTopAppBar(
                    title = {
                        Text(
                            text = title,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = titleMaxLines,
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = actions,
                    expandedHeight = expandedHeight - topContentWindowInset,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = contentColor,
                        titleContentColor = contentColor,
                        actionIconContentColor = contentColor
                    ),
                )
            }
        },
        content = content,
    )
}

