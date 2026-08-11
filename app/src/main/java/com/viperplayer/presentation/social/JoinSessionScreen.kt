package com.viperplayer.presentation.social

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Login
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.viperplayer.R
import com.viperplayer.presentation.theme.Spacing

/**
 * "Join a Jam" — the guest counterpart to the host's share/QR sheets. Scans a Jam invite QR with the
 * camera (decoded via zxing) or accepts a manually-entered code, then joins through the (currently
 * mock-backed) [com.viperplayer.domain.repository.ListenTogetherRepository].
 *
 * [onNavigateToHost] is the self-contained "Host a jam instead" affordance in the manual-entry sheet,
 * which jumps the guest to the [com.viperplayer.presentation.social.HostSessionScreen] host flow.
 */
@Composable
fun JoinSessionScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToHost: () -> Unit = {},
    viewModel: JoinSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val joinedMessage = stringResource(R.string.join_session_joined)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // On a successful join, confirm and leave the screen (the session now lives in the repository).
    LaunchedEffect(state.joined) {
        if (state.joined != null) {
            Toast.makeText(context, joinedMessage, Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    var torchOn by remember { mutableStateOf(false) }

    // Center-biased vignette (dark surface tones) so the viewfinder stays clear while the edges —
    // where the controls + manual-entry sheet sit — darken for legibility over the camera feed. The
    // gradient re-anchors to ~30% down the actual scan area at draw time (mirrors the mockup radial).
    val vignetteInner = MaterialTheme.colorScheme.surface.copy(alpha = if (hasCameraPermission) 0.10f else 0.45f)
    val vignetteOuter = MaterialTheme.colorScheme.surfaceDim.copy(alpha = if (hasCameraPermission) 0.60f else 0.88f)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) {
        // ---- Scanner area ----
        Box(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
            if (hasCameraPermission) {
                CameraScanner(
                    torchOn = torchOn,
                    onScanned = viewModel::onScanned,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Dim the preview so the overlaid controls + manual-entry sheet read over the camera feed.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val brush = Brush.radialGradient(
                            colors = listOf(vignetteInner, vignetteOuter),
                            center = Offset(size.width * 0.5f, size.height * 0.30f),
                            radius = size.maxDimension * 0.9f,
                        )
                        onDrawBehind { drawRect(brush) }
                    },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Close + title
            Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Spacing.sm)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResourceSafe(R.string.action_close), tint = Color.White)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResourceSafe(R.string.join_session_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResourceSafe(R.string.join_session_subtitle),
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xs),
            )

            Spacer(Modifier.height(Spacing.xxxl))
            Viewfinder(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(Spacing.xl))
            // Flashlight pill. Always a single filled FlashlightOn glyph; the active (torch-on) state
            // is signalled by a brighter container + full-opacity content, not by swapping the icon.
            val flashlightContainer = if (torchOn) {
                Color.White.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(100))
                    .background(flashlightContainer)
                    .clickable { torchOn = !torchOn }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val flashlightAlpha = if (torchOn) 1f else 0.85f
                Icon(
                    Icons.Filled.FlashlightOn,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = flashlightAlpha),
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    stringResourceSafe(R.string.join_session_flashlight),
                    color = Color.White.copy(alpha = flashlightAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.weight(1f))

            // ---- Manual entry sheet ----
            ManualEntry(
                code = state.code,
                codeLength = state.codeLength,
                joining = state.joining,
                // Reserve space for the floating mini-player + bottom nav so the Join button isn't
                // hidden behind them (rootPadding already includes the system nav-bar inset).
                bottomPadding = rootPadding.calculateBottomPadding(),
                onCodeChange = viewModel::onCodeChange,
                onPaste = viewModel::onPaste,
                onJoin = viewModel::join,
                onHostInstead = onNavigateToHost,
            )
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(VIEWFINDER)) {
        // Four corner brackets.
        val bracket = 44.dp
        val thickness = 5.dp
        @Composable
        fun corner(align: Alignment, topEdge: Boolean, leftEdge: Boolean) {
            Box(modifier = Modifier.align(align).size(bracket)) {
                Box(Modifier.fillMaxWidth().height(thickness).align(if (topEdge) Alignment.TopStart else Alignment.BottomStart).background(primary))
                Box(Modifier.width(thickness).fillMaxSize().align(if (leftEdge) Alignment.TopStart else Alignment.TopEnd).background(primary))
            }
        }
        corner(Alignment.TopStart, topEdge = true, leftEdge = true)
        corner(Alignment.TopEnd, topEdge = true, leftEdge = false)
        corner(Alignment.BottomStart, topEdge = false, leftEdge = true)
        corner(Alignment.BottomEnd, topEdge = false, leftEdge = false)
        // Animated scan line — offset down within the 232dp viewfinder by the animated fraction.
        val viewfinderPx = with(LocalDensity.current) { VIEWFINDER.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, (sweep * viewfinderPx).toInt()) }
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(3.dp)
                .background(primary),
        )
    }
}

private val VIEWFINDER = 232.dp

@Composable
private fun ManualEntry(
    code: String,
    codeLength: Int,
    joining: Boolean,
    bottomPadding: Dp,
    onCodeChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onJoin: () -> Unit,
    onHostInstead: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val isComplete = code.length >= codeLength

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = Spacing.xxl, top = Spacing.xxl, end = Spacing.xxl, bottom = 28.dp + bottomPadding),
    ) {
        // "OR ENTER A CODE" divider.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Text(
                stringResourceSafe(R.string.join_session_or_enter_code).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }

        Spacer(Modifier.height(18.dp))

        // Segmented cells backed by one hidden field (so IME / paste / autofill work).
        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .alpha(0f),
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onJoin() }),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.clickable {
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            ) {
                repeat(codeLength) { i ->
                    CodeCell(char = code.getOrNull(i), focused = i == code.length.coerceAtMost(codeLength - 1) && code.length < codeLength)
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    val pasted = clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrBlank()) onPaste(pasted)
                }
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(stringResourceSafe(R.string.join_session_paste), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.primary)
                .alpha(if (isComplete && !joining) 1f else 0.55f)
                .clickable(enabled = isComplete && !joining, onClick = onJoin)
                .padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Login, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(21.dp))
            Text(
                stringResourceSafe(R.string.join_session_join),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        Spacer(Modifier.height(6.dp))
        // Self-contained entry to the Host-a-Jam flow (5d) for guests who'd rather start their own.
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(100))
                .clickable(onClick = onHostInstead)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
            Text(
                stringResourceSafe(R.string.join_session_host_instead),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CodeCell(char: Char?, focused: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = when {
        char != null -> primary
        focused -> primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(char.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        } else if (focused) {
            Box(Modifier.width(2.dp).height(26.dp).background(primary))
        }
    }
}

@Composable
private fun CameraScanner(
    torchOn: Boolean,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }

    LaunchedEffect(Unit) {
        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            QrCodeAnalyzer(onScanned),
        )
        controller.bindToLifecycle(lifecycleOwner)
    }
    LaunchedEffect(torchOn) {
        runCatching { controller.enableTorch(torchOn) }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
    )
}

/** CameraX [androidx.camera.core.ImageAnalysis.Analyzer] that decodes a QR from the Y (luma) plane. */
private class QrCodeAnalyzer(
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    @Volatile
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) {
            image.close()
            return
        }
        try {
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            val data = if (rowStride == width) {
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } else {
                // Strip row padding so the luminance grid is width*height contiguous.
                ByteArray(width * height).also { out ->
                    val row = ByteArray(rowStride)
                    for (y in 0 until height) {
                        buffer.position(y * rowStride)
                        buffer.get(row, 0, rowStride)
                        System.arraycopy(row, 0, out, y * width, width)
                    }
                }
            }
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            if (result != null && result.text.isNotBlank()) {
                done = true
                onResult(result.text)
            }
        } catch (_: Exception) {
            // NotFoundException etc. — no QR in this frame.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

// Small indirection so a missing string key surfaces at compile time rather than a runtime crash.
@Composable
private fun stringResourceSafe(id: Int): String = stringResource(id)
