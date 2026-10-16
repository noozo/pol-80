package com.bare.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Which long edge is down. Derived from the gravity vector only.
 * Does not change activity orientation — portrait lock can stay ON.
 *
 * Android accelerometer / gravity: +X is screen-right, +Y is screen-top,
 * reported acceleration is opposite gravity. Right edge down → ax negative.
 */
internal enum class EdgeSide {
    None,
    RightDown,
    LeftDown,
}

internal object EdgeClockLogic {
    /** Hold this long on a side before the clock appears. Brief tilts do not flash. */
    const val ENTER_HOLD_MS = 480L

    /** Hold upright this long before the clock dismisses. */
    const val EXIT_HOLD_MS = 340L

    /** Minimum |ax| (m/s²) before a pose counts as on its side. */
    const val SIDE_MIN_G = 5.4f

    /**
     * Landscape-ish nightstand pose: sideways gravity beats vertical,
     * and the phone is not lying flat (Z-dominant).
     */
    fun classify(ax: Float, ay: Float, az: Float): EdgeSide {
        val axA = abs(ax)
        val ayA = abs(ay)
        val azA = abs(az)
        if (azA > 7.6f && axA < 5.0f) return EdgeSide.None
        if (ayA >= axA) return EdgeSide.None
        if (axA < SIDE_MIN_G) return EdgeSide.None
        if (axA < ayA * 1.15f) return EdgeSide.None
        return if (ax < 0f) EdgeSide.RightDown else EdgeSide.LeftDown
    }
}

/**
 * Debounces [EdgeClockLogic.classify] so a passing tilt never flashes the clock.
 * Pure: no sensors. [onSample] returns true when the published pose changes.
 */
internal class EdgeClockLatch {
    var shown: Boolean = false
        private set
    var side: EdgeSide = EdgeSide.None
        private set

    private var pending: EdgeSide = EdgeSide.None
    private var pendingSinceMs: Long = 0L

    fun onSample(sample: EdgeSide, nowMs: Long): Boolean {
        if (!shown) {
            if (sample == EdgeSide.None) {
                pending = EdgeSide.None
                return false
            }
            if (sample != pending) {
                pending = sample
                pendingSinceMs = nowMs
                return false
            }
            if (nowMs - pendingSinceMs >= EdgeClockLogic.ENTER_HOLD_MS) {
                shown = true
                side = sample
                return true
            }
            return false
        }
        if (sample == side) {
            pending = sample
            pendingSinceMs = nowMs
            return false
        }
        if (sample != EdgeSide.None) {
            if (sample != pending) {
                pending = sample
                pendingSinceMs = nowMs
                return false
            }
            if (nowMs - pendingSinceMs >= EdgeClockLogic.ENTER_HOLD_MS) {
                side = sample
                return true
            }
            return false
        }
        if (pending != EdgeSide.None) {
            pending = EdgeSide.None
            pendingSinceMs = nowMs
            return false
        }
        if (nowMs - pendingSinceMs >= EdgeClockLogic.EXIT_HOLD_MS) {
            shown = false
            side = EdgeSide.None
            return true
        }
        return false
    }

    fun reset() {
        shown = false
        side = EdgeSide.None
        pending = EdgeSide.None
        pendingSinceMs = 0L
    }
}

/**
 * Gravity (fallback accelerometer) listener. Never calls
 * [android.app.Activity.setRequestedOrientation] — portrait lock is untouched.
 */
internal class EdgeClockSensor(
    context: Context,
    private val onChange: (visible: Boolean, side: EdgeSide) -> Unit,
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val latch = EdgeClockLatch()
    private var listening = false
    private var primed = false
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var lastSampleAtMs = 0L

    fun hasSensor(): Boolean = sensor != null

    fun start() {
        if (listening) return
        val sm = sensorManager ?: return
        val s = sensor ?: return
        listening = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        if (listening) {
            primed = false
            lastSampleAtMs = 0L
        }
    }

    fun stop() {
        if (listening) {
            runCatching { sensorManager?.unregisterListener(this) }
            listening = false
        }
        primed = false
        val wasShown = latch.shown
        latch.reset()
        if (wasShown) onChange(false, EdgeSide.None)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor?.type ?: return
        if (type != Sensor.TYPE_GRAVITY && type != Sensor.TYPE_ACCELEROMETER) return
        if (event.values.size < 3) return
        val now = SystemClock.elapsedRealtime()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (!primed) {
            fx = x
            fy = y
            fz = z
            primed = true
            lastSampleAtMs = now
            return
        }
        if (now - lastSampleAtMs < MIN_SAMPLE_MS) return
        lastSampleAtMs = now
        fx += LOW_PASS * (x - fx)
        fy += LOW_PASS * (y - fy)
        fz += LOW_PASS * (z - fz)
        val sample = EdgeClockLogic.classify(fx, fy, fz)
        if (latch.onSample(sample, now)) {
            onChange(latch.shown, latch.side)
        }
    }

    companion object {
        private const val LOW_PASS = 0.28f
        private const val MIN_SAMPLE_MS = 50L
    }
}

/**
 * Nightstand overlay: huge phosphor time rotated so it reads upright while the
 * phone rests on a long edge. Portrait layout of the activity is not changed.
 * [timeHm] is HH:mm; [seconds] is ss.
 */
@Composable
internal fun EdgeClockOverlay(
    visible: Boolean,
    side: EdgeSide,
    timeHm: String,
    seconds: String,
) {
    if (!visible || side == EdgeSide.None) return
    val rotation = if (side == EdgeSide.RightDown) -90f else 90f
    val configuration = LocalConfiguration.current
    val longDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val hmSize = (longDp / 4.6f).coerceIn(78f, 132f).sp
    val phosphor = BareColors.TechGreen
    val glow = TextStyle(
        shadow = Shadow(
            color = phosphor.copy(alpha = 0.90f),
            offset = Offset.Zero,
            blurRadius = 28f,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(80f)
            .background(Color(0xE6030A06))
            .pointerInput(side) {
                // Eat touches so home icons don't launch while the clock is up.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            .drawBehind {
                val edge = 10.dp.toPx()
                // Phosphor rail on the long edge that is resting down.
                val rail = if (side == EdgeSide.RightDown) {
                    Offset(size.width - edge, 0f) to Offset(size.width - edge, size.height)
                } else {
                    Offset(edge, 0f) to Offset(edge, size.height)
                }
                drawLine(
                    color = phosphor.copy(alpha = 0.85f),
                    start = rail.first,
                    end = rail.second,
                    strokeWidth = 3.dp.toPx(),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            phosphor.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                val step = 3.2.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.28f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.2f,
                    )
                    y += step
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .graphicsLayer { rotationZ = rotation }
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = timeHm,
                color = phosphor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = hmSize,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                style = glow,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = seconds,
                color = BareColors.NeonCyan.copy(alpha = 0.92f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = (hmSize.value * 0.28f).coerceIn(18f, 36f).sp,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(
                        color = BareColors.NeonCyan.copy(alpha = 0.7f),
                        offset = Offset.Zero,
                        blurRadius = 16f,
                    ),
                ),
            )
        }
    }
}
