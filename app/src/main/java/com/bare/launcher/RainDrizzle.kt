package com.bare.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Thin phosphor "drizzle" scanline overlay when weather says rain.
 * Shows ~30s after unlock / home resume; debounced so rapid resumes
 * within a few minutes do not replay. Failures skip silently.
 */
object RainDrizzle {
    const val SHOW_MS = 30_000L
    const val DEBOUNCE_MS = 3L * 60L * 1000L

    private const val PREFS = "bare_rain_drizzle"
    private const val KEY_LAST_SHOWN = "last_shown_ms"

    fun shouldOffer(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = prefs(context).getLong(KEY_LAST_SHOWN, 0L)
        if (last > 0L && (nowMs - last) < DEBOUNCE_MS) return false
        return true
    }

    fun markShown(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_SHOWN, nowMs).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Full-screen non-interactive drizzle overlay. Caller controls [active].
 * When [active] becomes true, runs for [RainDrizzle.SHOW_MS] then calls [onCleared].
 */
@Composable
fun RainDrizzleOverlay(
    active: Boolean,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    val phosphor = BareColors.TechGreen
    val cyan = BareColors.NeonCyan
    val infinite = rememberInfiniteTransition(label = "rain-drizzle")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rain-drift",
    )
    // Fade out in the last 2.5s
    var alphaMul by remember { mutableStateOf(1f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        alphaMul = 1f
        delay(RainDrizzle.SHOW_MS - 2_500L)
        // Soft fade
        val steps = 25
        for (i in 1..steps) {
            alphaMul = 1f - (i.toFloat() / steps)
            delay(100L)
        }
        onCleared()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(4.5f)
            .drawBehind {
                val a = alphaMul.coerceIn(0f, 1f)
                if (a < 0.01f) return@drawBehind
                // Very light phosphor wash
                drawRect(phosphor.copy(alpha = 0.035f * a))
                val step = 3.2.dp.toPx()
                val scroll = (drift * step * 4f) % step
                var y = -step + scroll
                while (y < size.height + step) {
                    val wobble = sin((y + drift * 40f) * 0.045f) * 1.2f
                    drawLine(
                        color = Color.Black.copy(alpha = 0.18f * a),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.05f,
                    )
                    drawLine(
                        color = phosphor.copy(alpha = 0.085f * a),
                        start = Offset(0f, y + 0.6f + wobble),
                        end = Offset(size.width, y + 0.6f + wobble),
                        strokeWidth = 0.9f,
                    )
                    y += step
                }
                // Sparse diagonal drizzle ticks (deterministic-ish seed from size)
                val cols = 28
                val seed = (size.width.toInt() xor size.height.toInt()).toLong()
                val rnd = Random(seed)
                for (i in 0 until cols) {
                    val x = (i + 0.5f) / cols * size.width +
                        sin(drift * 6.28f + i) * 6f
                    val phase = (drift + i * 0.07f) % 1f
                    val len = 8.dp.toPx() + (rnd.nextFloat() * 10.dp.toPx())
                    val y0 = phase * (size.height + len) - len
                    drawLine(
                        color = cyan.copy(alpha = 0.10f * a),
                        start = Offset(x, y0),
                        end = Offset(x + 2.2f, y0 + len),
                        strokeWidth = 1.1f,
                    )
                    drawLine(
                        color = phosphor.copy(alpha = 0.14f * a),
                        start = Offset(x + 0.5f, y0 + 1f),
                        end = Offset(x + 2.0f, y0 + len * 0.7f),
                        strokeWidth = 0.85f,
                    )
                }
            },
    )
}

/**
 * Kick drizzle after resume when raining. Never throws; weather miss → skip.
 */
suspend fun maybeStartRainDrizzle(
    context: Context,
    forceWeatherRefresh: Boolean = false,
): Boolean {
    return try {
        if (!RainDrizzle.shouldOffer(context)) return false
        val raining = WeatherRepo.isRaining(context, force = forceWeatherRefresh)
        if (raining != true) return false
        RainDrizzle.markShown(context)
        true
    } catch (_: Exception) {
        false
    }
}
