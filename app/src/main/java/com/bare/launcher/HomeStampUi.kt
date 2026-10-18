package com.bare.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Open composer for a new stamp or an existing one. */
data class StampComposerState(
    val editId: String?,
    val draft: String,
    val xFrac: Float,
    val yFrac: Float,
)

/**
 * Home stamps overlay. v0.52: dragging a stamp moves it (clamped to the overlay, saved on
 * release). While a drag is in progress a phosphor bin fades in at bottom centre; a stamp is
 * deleted ONLY when released with the finger or the stamp's centre over the bin.
 * There is no fling / swipe dismiss.
 */
@Composable
fun HomeStampLayer(
    stamps: List<HomeStampStore.Stamp>,
    enabled: Boolean,
    onEdit: (HomeStampStore.Stamp) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onShare: (HomeStampStore.Stamp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (stamps.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(1.5f)) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        var draggingId by remember { mutableStateOf<String?>(null) }
        var overBin by remember { mutableStateOf(false) }
        var binRect by remember { mutableStateOf(Rect.Zero) }
        val binPad = with(LocalDensity.current) { 6.dp.toPx() }

        fun isOverBin(finger: Offset, center: Offset): Boolean {
            if (binRect == Rect.Zero) return false
            val r = binRect.inflate(binPad)
            return r.contains(finger) || r.contains(center)
        }

        stamps.forEach { stamp ->
            keyStamp(stamp.id) {
                HomeStampChip(
                    stamp = stamp,
                    parentW = w,
                    parentH = h,
                    enabled = enabled,
                    onEdit = { onEdit(stamp) },
                    onShare = { onShare(stamp) },
                    onDragStart = {
                        draggingId = stamp.id
                        overBin = false
                    },
                    onDragMove = { finger, center ->
                        overBin = isOverBin(finger, center)
                    },
                    onDragFinish = { cancelled, xFrac, yFrac ->
                        val drop = !cancelled && overBin
                        draggingId = null
                        overBin = false
                        if (drop) onDelete(stamp.id) else if (!cancelled) onMove(stamp.id, xFrac, yFrac)
                    },
                )
            }
        }

        StampBin(
            visible = draggingId != null,
            hot = overBin,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 132.dp)
                .onPlaced { binRect = it.boundsInParent() },
        )
    }
}

/** Phosphor-outline bin target, HUD style. Fades in/out; red-hot when a drop would delete. */
@Composable
private fun StampBin(visible: Boolean, hot: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "binAlpha")
    val scale by animateFloatAsState(if (hot) 1.12f else 1f, tween(120), label = "binScale")
    val color = if (hot) BIN_HOT else BareColors.TechGreen
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, color.copy(alpha = if (hot) 0.95f else 0.7f), shape)
            .background(
                (if (hot) BIN_HOT.copy(alpha = 0.16f) else BareColors.Bg.copy(alpha = 0.72f)),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 26.dp, height = 28.dp)) {
            val sw = 1.6.dp.toPx()
            val cw = size.width
            val ch = size.height
            // lid + handle
            drawLine(color, Offset(cw * 0.08f, ch * 0.18f), Offset(cw * 0.92f, ch * 0.18f), sw)
            drawLine(color, Offset(cw * 0.38f, ch * 0.18f), Offset(cw * 0.38f, ch * 0.06f), sw)
            drawLine(color, Offset(cw * 0.38f, ch * 0.06f), Offset(cw * 0.62f, ch * 0.06f), sw)
            drawLine(color, Offset(cw * 0.62f, ch * 0.06f), Offset(cw * 0.62f, ch * 0.18f), sw)
            // can body (slight taper)
            drawLine(color, Offset(cw * 0.16f, ch * 0.26f), Offset(cw * 0.24f, ch * 0.96f), sw)
            drawLine(color, Offset(cw * 0.84f, ch * 0.26f), Offset(cw * 0.76f, ch * 0.96f), sw)
            drawLine(color, Offset(cw * 0.24f, ch * 0.96f), Offset(cw * 0.76f, ch * 0.96f), sw)
            // ribs
            for (fx in listOf(0.38f, 0.5f, 0.62f)) {
                drawLine(color, Offset(cw * fx, ch * 0.36f), Offset(cw * fx, ch * 0.84f), sw * 0.8f)
            }
        }
        Text(
            text = if (hot) "[ RELEASE: DEL ]" else "[ DROP TO DEL ]",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val BIN_HOT = Color(0xFFFF4A5C)

@Composable
private fun keyStamp(id: String, content: @Composable () -> Unit) {
    // Local key helper so recomposition tracks each stamp independently.
    androidx.compose.runtime.key(id) { content() }
}

/**
 * Share stamp text via Android share sheet (ACTION_SEND text/plain).
 * Never deletes the stamp. Failures are quiet (Toast optional).
 */
fun shareHomeStamp(context: Context, stamp: HomeStampStore.Stamp) {
    val text = stamp.text.trim()
    if (text.isEmpty()) return
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "stamp")
        }
        val chooser = Intent.createChooser(send, "Share stamp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        // Never crash the launcher over a share miss.
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeStampChip(
    stamp: HomeStampStore.Stamp,
    parentW: Float,
    parentH: Float,
    enabled: Boolean,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (finger: Offset, center: Offset) -> Unit,
    onDragFinish: (cancelled: Boolean, xFrac: Float, yFrac: Float) -> Unit,
) {
    var dragX by remember(stamp.id) { mutableFloatStateOf(0f) }
    var dragY by remember(stamp.id) { mutableFloatStateOf(0f) }
    var chipSize by remember(stamp.id) { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(6.dp)

    // Gesture lambdas outlive recompositions; always read the latest values.
    val cur by rememberUpdatedState(stamp)
    val pw by rememberUpdatedState(parentW)
    val ph by rememberUpdatedState(parentH)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDragMove)
    val finish by rememberUpdatedState(onDragFinish)

    fun maxX() = (pw - chipSize.width).coerceAtLeast(0f)
    fun maxY() = (ph - chipSize.height).coerceAtLeast(0f)
    // Saved position is clamped on draw too, so old/odd saves can never sit off-screen.
    fun baseX() = (cur.xFrac * pw).coerceIn(0f, maxX())
    fun baseY() = (cur.yFrac * ph).coerceIn(0f, maxY())
    fun liveX() = (baseX() + dragX).coerceIn(0f, maxX())
    fun liveY() = (baseY() + dragY).coerceIn(0f, maxY())

    val dragging = dragX != 0f || dragY != 0f

    Box(
        modifier = Modifier
            .offset { IntOffset(liveX().roundToInt(), liveY().roundToInt()) }
            .onSizeChanged { chipSize = it }
            .zIndex(if (dragging) 1f else 0f)
            .widthIn(max = 160.dp)
            .border(
                1.dp,
                (if (dragging) BareColors.NeonCyan else BareColors.TechGreen).copy(alpha = 0.75f),
                shape,
            )
            .background(BareColors.Bg.copy(alpha = 0.72f), shape)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(stamp.id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                    start()
                                },
                                onDragEnd = {
                                    val x = liveX()
                                    val y = liveY()
                                    dragX = 0f
                                    dragY = 0f
                                    finish(false, x / pw, y / ph)
                                },
                                onDragCancel = {
                                    dragX = 0f
                                    dragY = 0f
                                    finish(true, cur.xFrac, cur.yFrac)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    // Keep the accumulated offset inside the clamp so the
                                    // chip follows the finger back from an edge immediately.
                                    dragX = (baseX() + dragX + amount.x).coerceIn(0f, maxX()) - baseX()
                                    dragY = (baseY() + dragY + amount.y).coerceIn(0f, maxY()) - baseY()
                                    val tl = Offset(liveX(), liveY())
                                    val finger = tl + change.position
                                    val center = tl + Offset(chipSize.width / 2f, chipSize.height / 2f)
                                    move(finger, center)
                                },
                            )
                        }
                        .combinedClickable(
                            onClick = onEdit,
                            onLongClick = onShare,
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = stamp.text,
            color = BareColors.TechGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp,
            maxLines = 3,
        )
    }
}

@Composable
fun HomeStampComposerDialog(
    state: StampComposerState,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val title = if (state.editId == null) "stamp" else "edit stamp"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = BareColors.TechGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        },
        text = {
            Column {
                Text(
                    text = "short pin — drag to move · drop on bin to delete · long-press share",
                    color = BareColors.TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                BasicTextField(
                    value = state.draft,
                    onValueChange = { onDraftChange(it.take(HomeStampStore.MAX_TEXT_LEN)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = BareColors.TechGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(BareColors.NeonCyan),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BareColors.TechGreen.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .background(BareColors.BgAlt.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (state.draft.isEmpty()) {
                                Text(
                                    text = "keys, idea…",
                                    color = BareColors.TextMuted.copy(alpha = 0.55f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("save", color = BareColors.NeonCyan, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("cancel", color = BareColors.TextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = BareColors.Surface,
    )
}

/**
 * Map a tap [offset] inside an overlay of [size] to sanitized fractional coords.
 */
fun stampFractionsFromTap(offset: Offset, size: Offset): Pair<Float, Float> {
    val w = size.x.coerceAtLeast(1f)
    val h = size.y.coerceAtLeast(1f)
    return HomeStampStore.sanitizePosition(offset.x / w, offset.y / h)
}
