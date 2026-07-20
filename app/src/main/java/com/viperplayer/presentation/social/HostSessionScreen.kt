package com.viperplayer.presentation.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.viperplayer.R
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.presentation.common.components.InitialsAvatar
import com.viperplayer.presentation.common.components.avatarTonesFor

/**
 * "Host a Jam" — the host counterpart to [JoinSessionScreen]. Renders as a bottom sheet over a dimmed
 * backdrop: on open the [HostSessionViewModel] starts a session through the (currently mock-backed)
 * [com.viperplayer.domain.repository.ListenTogetherRepository], and this sheet shows the live session
 * code + invite so friends can join. All logic lives in the ViewModel — the composable only renders
 * [HostSessionUiState] and forwards events (copy / share / join / end).
 *
 * Reached from [JoinSessionScreen]'s "Host a jam instead" affordance ([onNavigateToJoin] lets the host
 * jump back to the guest/join flow from here).
 */
@Composable
fun HostSessionScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToJoin: () -> Unit,
    viewModel: HostSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // When the host ends the session, leave the sheet.
    LaunchedEffect(state.ended) {
        if (state.ended) onNavigateBack()
    }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            // Tapping the dimmed backdrop dismisses the sheet (the host stays in the session). No
            // ripple/indication — this is a scrim, not a button.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigateBack,
            ),
    ) {
        HostSheet(
            state = state,
            rootPadding = rootPadding,
            onShareInvite = { systemShare(context, viewModel.inviteUrl()) },
            onCopyCode = { copyCode(context, state.session?.code.orEmpty()) },
            onJoinInstead = onNavigateToJoin,
            onEndSession = viewModel::endSession,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HostSheet(
    state: HostSessionUiState,
    rootPadding: PaddingValues,
    onShareInvite: () -> Unit,
    onCopyCode: () -> Unit,
    onJoinInstead: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQr by remember { mutableStateOf(false) }
    val session = state.session

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Consume taps on the sheet body so they don't fall through to the dismiss-on-backdrop.
            // Enabled (so it actually intercepts) but no-op + no indication (it's not a button).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp)
            .padding(bottom = 28.dp)
            // Keep clear of the system nav bar / mini-player when this route is the top screen.
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Drag handle.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(8.dp))

        // Header.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.host_session_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (session == null) {
            StartingState()
        } else {
            NowPlayingCard(
                title = state.nowPlaying?.title,
                artist = state.nowPlaying?.artist,
                artworkUrl = state.nowPlaying?.artworkUrl?.ifBlank { null },
            )
            Spacer(Modifier.height(14.dp))
            ListenersRow(participants = session.participants)
            Spacer(Modifier.height(14.dp))
            SessionCodeBox(code = session.code, onCopy = onCopyCode)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // Primary: share invite.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onShareInvite),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = stringResource(R.string.host_session_share_invite),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Secondary: toggle the inline QR.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { showQr = !showQr },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = stringResource(R.string.host_session_show_qr),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (showQr) {
                Spacer(Modifier.height(16.dp))
                QrCard(content = session.inviteUrl)
            }

            Spacer(Modifier.height(6.dp))
            // "Join a session" link — jump to the guest/join flow.
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(100))
                    .clickable(onClick = onJoinInstead)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = stringResource(R.string.host_session_join_instead),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))

            // "This device" playback-target row.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = stringResource(R.string.host_session_this_device),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            // End the Jam for everyone and leave the sheet.
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(100))
                    .clickable(onClick = onEndSession)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = stringResource(R.string.host_session_end),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Reserve space for the floating mini-player + bottom nav so the sheet content clears them.
        Spacer(Modifier.height(rootPadding.calculateBottomPadding()))
    }
}

@Composable
private fun StartingState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.host_session_starting),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * The now-playing card + HOSTING badge. When a shared track exists ([title] non-null) it shows the
 * artwork slot with a two-line title/artist layout; otherwise it falls back to the placeholder (the
 * sync engine seeds a track only once the host plays into the session). [artworkUrl] renders in the
 * artwork slot when present, falling back to the MusicNote placeholder when null (mock/offline builds
 * leave the shared playback — and so the artwork — null).
 */
@Composable
private fun NowPlayingCard(
    title: String? = null,
    artist: String? = null,
    artworkUrl: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (title == null) {
            Text(
                text = stringResource(R.string.host_session_now_playing_placeholder),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist != null) {
                    Text(
                        text = artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.host_session_hosting_badge).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun ListenersRow(participants: List<SessionParticipant>) {
    // Everyone except the local host, in the mockup's overlapping-avatar cluster.
    val others = participants.filterNot { it.isSelf }
    // Per-person deterministic tint (keyed on the participant id) so the same listener always reads as
    // the same tone rather than shifting with list position.
    val scheme = MaterialTheme.colorScheme
    val and = stringResource(R.string.list_conjunction_and)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (others.isNotEmpty()) {
            Row {
                others.take(3).forEachIndexed { index, participant ->
                    val (container, content) = avatarTonesFor(participant.id, scheme)
                    // Overlap each avatar onto its left neighbour by 10dp, matching the mockup cluster.
                    Box(
                        modifier = Modifier
                            .offset(x = if (index == 0) 0.dp else (-10 * index).dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(2.5.dp),
                    ) {
                        InitialsAvatar(
                            text = participant.initial,
                            containerColor = container,
                            contentColor = content,
                        )
                    }
                }
            }
        }
        val label = if (others.isEmpty()) {
            stringResource(R.string.host_session_listeners_you_only)
        } else {
            // Oxford-style join: "A", "A and B", "A, B and C" (final separator is " and ", no comma).
            val names = when (others.size) {
                1 -> others[0].name
                else -> others.dropLast(1).joinToString(", ") { it.name } + " $and " + others.last().name
            }
            stringResource(R.string.host_session_listeners, names)
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionCodeBox(code: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onCopy)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.host_session_code_label).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Text(
                text = code,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.host_session_copy_code),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Renders the invite [content] as a real QR (ZXing) on a light card so it scans reliably. */
@Composable
private fun QrCard(content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(12.dp),
        ) {
            QrModules(content = content, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Draws the QR matrix as solid dark modules. Mirrors the ZXing encode path used elsewhere. */
@Composable
private fun QrModules(content: String, modifier: Modifier) {
    val qr = remember(content) {
        runCatching { Encoder.encode(content, ErrorCorrectionLevel.M) }.getOrNull()
    }
    Canvas(modifier = modifier) {
        val matrix = qr?.matrix ?: return@Canvas
        val n = matrix.width
        if (n <= 0) return@Canvas
        val quiet = 2
        val total = n + quiet * 2
        val cell = size.minDimension / total
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix.get(x, y).toInt() != 1) continue
                // Pure black modules on the white quiet zone — theme-independent so the QR always scans.
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.2f, cell * 0.2f),
                )
            }
        }
    }
}

private fun copyCode(context: Context, code: String) {
    if (code.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.host_session_code_label), code))
    Toast.makeText(context, context.getString(R.string.host_session_code_copied), Toast.LENGTH_SHORT).show()
}

private fun systemShare(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.host_session_share_invite)))
}
