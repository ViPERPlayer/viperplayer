package com.viperplayer.presentation.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.viperplayer.domain.model.Song

/**
 * Hosts the three "social" sheets reachable from the player's output bar: Listen-together / Devices,
 * Share invite, and the QR join screen.
 *
 * There is no realtime jam backend, so this is the honest single-host state: you can generate and
 * share an invite (real system share + clipboard + a scannable QR encoding the link), and "Play on
 * device" lists the real local output. The link carries a generated session code for when a backend
 * is added.
 */
private enum class SocialPage { LISTEN_TOGETHER, SHARE_INVITE, QR }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerSocialSheets(
    song: Song,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sessionCode = rememberSaveable { generateSessionCode() }
    val inviteUrl = remember(sessionCode) { "https://viper.player/jam/$sessionCode" }
    val shareText = remember(song, inviteUrl) {
        "Listen with me: ${song.title}${song.artistNames?.let { " — $it" } ?: ""}\n$inviteUrl"
    }

    // One sheet with an internal back stack so Share / QR layer on top of Listen-together and a
    // "back" returns to the previous page instead of closing the whole sheet. The host composes this
    // only while open, so the stack starts fresh at LISTEN_TOGETHER each time it opens.
    val backStack = remember { mutableStateListOf(SocialPage.LISTEN_TOGETHER) }
    fun push(page: SocialPage) { backStack.add(page) }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        when (backStack.last()) {
            SocialPage.LISTEN_TOGETHER -> ListenTogetherContent(
                song = song,
                onShareInvite = { push(SocialPage.SHARE_INVITE) },
                onQr = { push(SocialPage.QR) }
            )

            SocialPage.SHARE_INVITE -> ShareInviteContent(
                song = song,
                inviteUrl = inviteUrl,
                onCopy = { copyToClipboard(context, inviteUrl) },
                onSystemShare = { systemShare(context, shareText) },
                onQr = { push(SocialPage.QR) },
                onBack = { pop() }
            )

            SocialPage.QR -> QrJoinContent(
                song = song,
                inviteUrl = inviteUrl,
                code = sessionCode,
                onCopy = { copyToClipboard(context, inviteUrl) },
                onBack = { pop() }
            )
        }
    }
}

@Composable
private fun ListenTogetherContent(
    song: Song,
    onShareInvite: () -> Unit,
    onQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Listen together", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        HostCard(song = song)

        Spacer(Modifier.height(8.dp))
        Text(
            "Share what you're listening to so friends can follow along.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledPillButton(
                text = "Share invite",
                icon = Icons.Filled.IosShare,
                modifier = Modifier.weight(1f),
                onClick = onShareInvite
            )
            TonalPillButton(icon = Icons.Filled.QrCode2, contentDescription = "Show QR code", onClick = onQr)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Play on device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(4.dp))
        DeviceRow(
            icon = Icons.Filled.Smartphone,
            name = "This phone",
            subtitle = "Connected",
            selected = true
        )
        Text(
            "No other devices found nearby.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun ShareInviteContent(
    song: Song,
    inviteUrl: String,
    onCopy: () -> Unit,
    onSystemShare: () -> Unit,
    onQr: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Invite to listen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        HostCard(song = song)

        Spacer(Modifier.height(20.dp))
        SectionLabel("Invite link")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = inviteUrl,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text("Copy", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledPillButton(
                text = "Share via…",
                icon = Icons.Filled.IosShare,
                modifier = Modifier.weight(1f),
                onClick = onSystemShare
            )
            TonalPillButton(icon = Icons.Filled.QrCode2, contentDescription = "Show QR code", onClick = onQr)
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                "Anyone with the link can join the session and add songs to the queue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QrJoinContent(
    song: Song,
    inviteUrl: String,
    code: String,
    onCopy: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Scan to join",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "You're hosting · ${song.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(20.dp))
        // The QR card is always white for reliable scanning, regardless of theme.
        Box(
            modifier = Modifier
                .size(258.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White)
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            QrCode(content = inviteUrl, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            // Branded center badge with a white halo so the code still scans around it.
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(6.dp, Color.White, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Point a camera at the code, or enter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = code.replace("-", " · "),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onCopy)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Copy link instead", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Renders a real QR (ZXing) as rounded "dot" modules, clearing the center for the badge. */
@Composable
private fun QrCode(content: String, modifier: Modifier) {
    val qr = remember(content) {
        runCatching { Encoder.encode(content, ErrorCorrectionLevel.H) }.getOrNull()
    }
    val moduleColor = Color(0xFF16181C)
    Canvas(modifier = modifier) {
        val matrix = qr?.matrix ?: return@Canvas
        val n = matrix.width
        if (n <= 0) return@Canvas
        val quiet = 2
        val total = n + quiet * 2
        val cell = size.minDimension / total
        val center = total / 2
        val clearHalf = 3
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix.get(x, y).toInt() != 1) continue
                val mx = x + quiet
                val my = y + quiet
                if (mx in (center - clearHalf)..(center + clearHalf) &&
                    my in (center - clearHalf)..(center + clearHalf)
                ) continue
                drawRoundRect(
                    color = moduleColor,
                    topLeft = Offset(mx * cell + cell * 0.1f, my * cell + cell * 0.1f),
                    size = Size(cell * 0.8f, cell * 0.8f),
                    cornerRadius = CornerRadius(cell * 0.35f, cell * 0.35f)
                )
            }
        }
    }
}

@Composable
private fun HostCard(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "YOU'RE HOSTING",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = listOfNotNull(song.title, song.artistNames).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DeviceRow(icon: ImageVector, name: String, subtitle: String?, selected: Boolean) {
    // Plain list row whose leading icon aligns with the "Play on device" section header — the previous
    // inset highlight card pushed the content 12dp to the right of the header. Selection is shown by
    // the primary tint + the trailing check.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FilledPillButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TonalPillButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun generateSessionCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun block(n: Int) = (1..n).map { chars.random() }.joinToString("")
    return "${block(4)}-${block(3)}"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Invite link", text))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun systemShare(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share invite"))
}
