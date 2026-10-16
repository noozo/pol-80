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
import androidx.compose.ui.draw.alpha
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
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Open composer for a new stamp or an existing one. */
data class StampComposerState(
    val editId: String?,
    val draft: String,
    val xFrac: Float,
    val yFrac: Float,
)

@Composable
fun HomeStampLayer(
    stamps: List<HomeStampStore.Stamp>,
    enabled: Boolean,
    onEdit: (HomeStampStore.Stamp) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (HomeStampStore.Stamp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (stamps.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(1.5f)) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val dismissPx = with(density) { 56.dp.toPx() }
        stamps.forEach { stamp ->
            keyStamp(stamp.id) {
                HomeStampChip(
                    stamp = stamp,
                    parentW = w,
                    parentH = h,
                    enabled = enabled,
                    dismissPx = dismissPx,
                    onEdit = { onEdit(stamp) },
                    onDelete = { onDelete(stamp.id) },
                    onShare = { onShare(stamp) },
                )
            }
        }
    }
}

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
    dismissPx: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var dragX by remember(stamp.id) { mutableFloatStateOf(0f) }
    var dragY by remember(stamp.id) { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(6.dp)
    val baseX = stamp.xFrac * parentW
    val baseY = stamp.yFrac * parentH
    val dragDist = sqrt(dragX * dragX + dragY * dragY)
    val fading = (1f - (dragDist / (dismissPx * 2.2f)).coerceIn(0f, 0.85f))

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (baseX + dragX).roundToInt(),
                    (baseY + dragY).roundToInt(),
                )
            }
            .alpha(fading)
            .widthIn(max = 160.dp)
            .border(1.dp, BareColors.TechGreen.copy(alpha = 0.75f), shape)
            .background(BareColors.Bg.copy(alpha = 0.72f), shape)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(stamp.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (sqrt(dragX * dragX + dragY * dragY) > dismissPx) {
                                        onDelete()
                                    } else {
                                        dragX = 0f
                                        dragY = 0f
                                    }
                                },
                                onDragCancel = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    dragY += amount.y
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
                    text = "short pin — long-press share · swipe off to delete",
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
