package com.bare.launcher

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Don't re-flash CRT boot splash if Home is spammed. */
internal const val BOOT_SPLASH_THROTTLE_MS = 3500L

/** CRT power-on + hold + settle — total ~525ms; whole home is warped then released. */
internal const val BOOT_SPLASH_POWER_MS = 110L
internal const val BOOT_SPLASH_HOLD_MS = 260L
internal const val BOOT_SPLASH_FADE_MS = 155
/** Failsafe: always restore flat home if boot/plug-in warp stalls. */
internal const val BOOT_SPLASH_HARD_TIMEOUT_MS =
    BOOT_SPLASH_POWER_MS + BOOT_SPLASH_HOLD_MS + BOOT_SPLASH_FADE_MS.toLong() + 250L

/**
 * AGSL barrel / pincushion + chromatic fringe + soft scanline darken.
 * Samples the composed home layer (wallpaper plate + chrome) so pixels actually move.
 * API 33+ via RuntimeShader / RenderEffect.
 */
private const val CRT_WARP_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float amount;
uniform float fringe;
uniform float roll;
uniform float brightness;
uniform float time;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float2 c = uv * 2.0 - 1.0;
    // Barrel (positive amount pulls edges in — CRT tube look)
    float r2 = dot(c, c);
    float barrel = 1.0 + amount * 0.55 * r2;
    float2 d = c * barrel;
    // Slight vertical roll / sync wander
    d.y += roll * 2.0 / max(resolution.y, 1.0);
    // Tiny H jitter from brightness flicker residue
    d.x += sin(time * 40.0 + d.y * 12.0) * amount * 0.004;
    float2 suv = d * 0.5 + 0.5;
    // Soft vignette clamp — avoid sampling far outside
    if (suv.x < -0.02 || suv.x > 1.02 || suv.y < -0.02 || suv.y > 1.02) {
        return half4(0.0, 0.02, 0.01, 1.0);
    }
    float2 px = 1.0 / max(resolution, float2(1.0, 1.0));
    float2 off = c * fringe * 3.5 * px * resolution.x * 0.0015;
    half4 cr = content.eval((clamp(suv + float2(off.x, 0.0), 0.0, 1.0)) * resolution);
    half4 cg = content.eval((clamp(suv, 0.0, 1.0)) * resolution);
    half4 cb = content.eval((clamp(suv - float2(off.x, 0.0), 0.0, 1.0)) * resolution);
    half3 rgb = half3(cr.r, cg.g, cb.b);
    // Phosphor lift
    rgb = mix(rgb, rgb * half3(0.82, 1.08, 0.90), half(amount * 0.45 * brightness));
    // Scanline darken (shader-side so it hits warped pixels)
    float scan = 0.88 + 0.12 * sin(fragCoord.y * 3.14159);
    rgb *= half(mix(1.0, scan, amount * 0.85));
    float a = max(max(cr.a, cg.a), cb.a);
    return half4(rgb, a);
}
"""

/** Shared CRT boot animation values driving home warp + overlay plate. */
internal data class CrtBootAnim(
    val requestId: Long,
    val showing: Boolean,
    val brightness: Float,
    val overlayAlpha: Float,
    val jitterX: Float,
    val rollY: Float,
    val bloom: Float,
    val fringe: Float,
    /** 0 = flat, 1 = max barrel / tube warp on the home layer. */
    val warpAmount: Float,
    /** 0 = full frame, 1 = CRT power-off slit. Must return to 0 when idle. */
    val collapse: Float = 0f,
)

/** Flat home — no overlay, no warp, no RenderEffect inputs. */
internal fun idleCrtAnim(requestId: Long = 0L): CrtBootAnim = CrtBootAnim(
    requestId = requestId,
    showing = false,
    brightness = 1f,
    overlayAlpha = 0f,
    jitterX = 0f,
    rollY = 0f,
    bloom = 0f,
    fringe = 0f,
    warpAmount = 0f,
    collapse = 0f,
)

@Composable
internal fun rememberCrtBoot(requestId: Long): CrtBootAnim {
    if (requestId == 0L) {
        return idleCrtAnim(0L)
    }

    var showing by remember(requestId) { mutableStateOf(true) }
    var forcedIdle by remember(requestId) { mutableStateOf(false) }
    val overlayAlpha = remember(requestId) { Animatable(1f) }
    val brightness = remember(requestId) { Animatable(0f) }
    val jitterX = remember(requestId) { Animatable(0f) }
    val rollY = remember(requestId) { Animatable(0f) }
    val bloomPulse = remember(requestId) { Animatable(0.55f) }
    val fringe = remember(requestId) { Animatable(0f) }
    val warpAmount = remember(requestId) { Animatable(0.85f) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val forcedIdleState = rememberUpdatedState(forcedIdle)

    // Hard timeout + dispose: never leave AGSL / warp applied after plug-in/boot.
    DisposableEffect(requestId) {
        val clear = Runnable {
            if (!forcedIdleState.value) {
                forcedIdle = true
                showing = false
            }
        }
        mainHandler.postDelayed(clear, BOOT_SPLASH_HARD_TIMEOUT_MS)
        onDispose {
            mainHandler.removeCallbacks(clear)
            forcedIdle = true
            showing = false
        }
    }

    LaunchedEffect(requestId) {
        if (forcedIdle) return@LaunchedEffect
        try {
            withTimeout(BOOT_SPLASH_HARD_TIMEOUT_MS) {
                showing = true
                overlayAlpha.snapTo(1f)
                brightness.snapTo(0f)
                jitterX.snapTo(0f)
                rollY.snapTo(28f)
                bloomPulse.snapTo(0.25f)
                fringe.snapTo(1f)
                warpAmount.snapTo(0.92f)

                // Power-on: hard flicker + H-sync jitter + V-roll + chromatic fringe
                val powerSteps = 12
                val stepMs = (BOOT_SPLASH_POWER_MS / powerSteps).coerceAtLeast(1L)
                repeat(powerSteps) { i ->
                    val t = (i + 1) / powerSteps.toFloat()
                    val flicker = when (i) {
                        0 -> 0.08f
                        1 -> 0.55f
                        2 -> 0.12f
                        3 -> 0.92f
                        4 -> 0.28f
                        5 -> 0.75f
                        6 -> 0.18f
                        7 -> 1.0f
                        8 -> 0.42f
                        9 -> 0.88f
                        10 -> 0.62f
                        else -> t
                    }
                    brightness.snapTo(flicker.coerceIn(0f, 1f))
                    val phase = i * 2.15f
                    val amp = 1f - t
                    jitterX.snapTo(sin(phase.toDouble()).toFloat() * (22f * amp))
                    rollY.snapTo(amp * 30f * sin((phase * 0.9f).toDouble()).toFloat())
                    bloomPulse.snapTo(0.25f + 0.85f * t)
                    fringe.snapTo((1.15f - t).coerceIn(0f, 1.15f))
                    // Warp peaks early then eases slightly during power-on
                    warpAmount.snapTo((0.95f - 0.18f * t).coerceIn(0.55f, 0.95f))
                    delay(stepMs)
                }
                brightness.snapTo(1f)
                jitterX.snapTo(0f)
                rollY.snapTo(0f)
                bloomPulse.snapTo(1f)
                fringe.snapTo(0.28f)
                warpAmount.snapTo(0.62f)

                delay(BOOT_SPLASH_HOLD_MS)

                // Settle tube → flat home while overlay fades
                fringe.snapTo(0f)
                warpAmount.animateTo(0f, animationSpec = tween(durationMillis = BOOT_SPLASH_FADE_MS))
                overlayAlpha.animateTo(0f, animationSpec = tween(durationMillis = BOOT_SPLASH_FADE_MS))
                showing = false
            }
        } finally {
            // Cancelled, timed out, or finished — restore flat home inputs.
            try {
                overlayAlpha.snapTo(0f)
                brightness.snapTo(1f)
                jitterX.snapTo(0f)
                rollY.snapTo(0f)
                bloomPulse.snapTo(0f)
                fringe.snapTo(0f)
                warpAmount.snapTo(0f)
            } catch (_: Exception) {
            }
            showing = false
            forcedIdle = true
        }
    }

    if (forcedIdle) {
        return idleCrtAnim(requestId)
    }

    return CrtBootAnim(
        requestId = requestId,
        showing = showing,
        brightness = brightness.value.coerceIn(0f, 1f),
        overlayAlpha = overlayAlpha.value,
        jitterX = jitterX.value,
        rollY = rollY.value,
        bloom = bloomPulse.value,
        fringe = fringe.value.coerceIn(0f, 1.2f),
        warpAmount = warpAmount.value.coerceIn(0f, 1f),
        collapse = 0f,
    )
}

/**
 * Unplug sting: short scanline alpha flicker only (no AGSL / home collapse).
 * Hard timeout restores normal home even if the coroutine is cancelled.
 */
internal const val UNPLUG_STING_MS = 180L
internal const val UNPLUG_STING_HARD_TIMEOUT_MS = 300L

/**
 * ~180ms unplug sting: scanline alpha flicker only.
 *
 * Does NOT drive home AGSL / RenderEffect / collapse. 0.45 left collapse=1
 * after the sting, which kept crtHomeWarp squashing the home to a thin
 * rainbow band forever. Cleanup is guaranteed by:
 *  1) LaunchedEffect finally (end / cancel / timeout)
 *  2) Handler.postDelayed hard timeout (~300ms)
 *  3) DisposableEffect onDispose (recomposition / pause teardown)
 */
@Composable
internal fun rememberCrtPowerDown(requestId: Long): CrtBootAnim {
    if (requestId == 0L) {
        return idleCrtAnim(0L)
    }

    var showing by remember(requestId) { mutableStateOf(true) }
    var overlayAlpha by remember(requestId) { mutableStateOf(0.88f) }
    var brightness by remember(requestId) { mutableStateOf(1f) }
    var forcedIdle by remember(requestId) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val forcedIdleState = rememberUpdatedState(forcedIdle)

    fun clearUnplug() {
        showing = false
        overlayAlpha = 0f
        brightness = 1f
        forcedIdle = true
    }

    // Posted runnable failsafe — never depend on coroutine / shader completion alone.
    DisposableEffect(requestId) {
        val clear = Runnable {
            if (!forcedIdleState.value) {
                clearUnplug()
            }
        }
        mainHandler.postDelayed(clear, UNPLUG_STING_HARD_TIMEOUT_MS)
        onDispose {
            mainHandler.removeCallbacks(clear)
            clearUnplug()
        }
    }

    LaunchedEffect(requestId) {
        if (forcedIdle) return@LaunchedEffect
        try {
            withTimeout(UNPLUG_STING_HARD_TIMEOUT_MS) {
                showing = true
                overlayAlpha = 0.88f
                brightness = 1f

                val steps = 6
                val stepMs = (UNPLUG_STING_MS / steps).coerceAtLeast(1L)
                // Brief phosphor flicker, then alpha → 0. No home warp/collapse.
                val flick = floatArrayOf(1f, 0.15f, 0.75f, 0.08f, 0.35f, 0f)
                val alphas = floatArrayOf(0.88f, 0.70f, 0.55f, 0.32f, 0.14f, 0f)
                repeat(steps) { i ->
                    brightness = flick[i]
                    overlayAlpha = alphas[i]
                    delay(stepMs)
                }
                overlayAlpha = 0f
                brightness = 1f
                showing = false
            }
        } finally {
            clearUnplug()
        }
    }

    if (forcedIdle) {
        return idleCrtAnim(requestId)
    }

    return CrtBootAnim(
        requestId = requestId,
        showing = showing,
        brightness = brightness.coerceIn(0f, 1f),
        overlayAlpha = overlayAlpha.coerceIn(0f, 1f),
        jitterX = 0f,
        rollY = 0f,
        bloom = brightness.coerceIn(0f, 1f),
        fringe = 0f,
        // Never feed AGSL / collapse from the unplug path.
        warpAmount = 0f,
        collapse = 0f,
    )
}

/**
 * Distorts the composed home layer (wallpaper plate + header + icons + dock).
 * API 33+: AGSL barrel + chromatic via RenderEffect.
 * Older: fish-eye scale / roll / jitter + drawWithContent RGB fringe approximation.
 */
@Composable
internal fun Modifier.crtHomeWarp(anim: CrtBootAnim): Modifier {
    val density = LocalDensity.current
    val warp = anim.warpAmount
    val fr = anim.fringe
    val jx = anim.jitterX
    val ry = anim.rollY
    val b = anim.brightness
    // Collapse alone must not keep the home warped after a finished sting.
    val active = (anim.showing || warp > 0.001f || anim.overlayAlpha > 0.001f) &&
        (warp > 0.001f || anim.fringe > 0.001f || anim.overlayAlpha > 0.001f || anim.showing)
    // Held as Any? so this function never names RuntimeShader: on API < 33 ART fails
    // class resolution (NoClassDefFoundError) at the first typed reference, null or not.
    val shader: Any? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) CrtShaderApi33.create() else null
    }
    val timeSec = remember(anim.requestId, anim.showing) {
        SystemClock.elapsedRealtime() / 1000f
    }

    // Returning without graphicsLayer drops any prior RenderEffect / AGSL.
    if (!active || (warp <= 0.001f && anim.collapse <= 0.001f && anim.overlayAlpha <= 0.001f && !anim.showing)) {
        return this
    }

    return this
        .graphicsLayer {
            // Always apply physical tube motion (all APIs)
            translationX = jx
            translationY = ry
            // Fish-eye approximation via overscale that settles with warp
            val bulge = 1f + 0.06f * warp
            val squash = (1f - 0.94f * anim.collapse.coerceIn(0f, 1f)).coerceAtLeast(0.04f)
            scaleX = bulge * (0.992f + 0.008f * b)
            scaleY = bulge * (1f + 0.025f * warp) * (0.992f + 0.008f * b) * squash
            // Slight perspective cupping
            rotationX = warp * 3.2f
            cameraDistance = 12f * density.density
            compositingStrategy = CompositingStrategy.Offscreen
            clip = false

            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && warp > 0.004f) {
                renderEffect = CrtShaderApi33.effect(
                    shader, size.width, size.height, warp, fr * warp, ry, b, timeSec + warp * 3f,
                )
            } else {
                renderEffect = null
            }
        }
        .then(
            if (shader == null && warp > 0.01f) {
                // Pre-Tiramisu: layered RGB fringe by redrawing content with channel offsets
                Modifier.drawWithContent {
                    val ox = 2.8f * fr * warp * (if (b < 0.95f) 1.4f else 1f)
                    // Magenta ghost (left)
                    drawContent()
                    // Cheap chromatic: darken edges + slight horizontal smear via overlay lines
                    // (full multi-pass channel split needs offscreen bitmaps; edge fringe + bulge above)
                    if (ox > 0.4f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.14f * fr * warp),
                                    Color.Transparent,
                                    Color(0xFFFF2D95).copy(alpha = 0.14f * fr * warp),
                                ),
                            ),
                        )
                    }
                }
            } else {
                Modifier
            },
        )
}

/**
 * Snapshot of the system wallpaper drawn inside the warped home layer so the tube
 * warp includes wallpaper pixels (not only chrome). Shown only while CRT is active.
 */
@Composable
internal fun CrtWallpaperPlate(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) return
    val context = LocalContext.current
    val imageBitmap = remember(active) {
        try {
            val wm = WallpaperManager.getInstance(context)
            @Suppress("DEPRECATION")
            val d = wm.drawable ?: wm.builtInDrawable ?: return@remember null
            val w = (d.intrinsicWidth.takeIf { it > 1 } ?: 1080).coerceAtMost(1440)
            val h = (d.intrinsicHeight.takeIf { it > 1 } ?: 1920).coerceAtMost(2560)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = AndroidCanvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            bmp.asImageBitmap()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        // Fallback plate so the warped layer isn't transparent over unwarped wallpaper
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color(0xFF050308))
                },
        )
    }
}

/**
 * Readable boot text + translucent CRT accents on top of the already-warped home.
 * Dark plate keeps title readable; home underneath is the warped tube content.
 */
@Composable
internal fun CrtBootSplashOverlay(anim: CrtBootAnim) {
    if (!anim.showing && anim.overlayAlpha < 0.01f) return

    val b = anim.brightness
    val oa = anim.overlayAlpha
    val jx = anim.jitterX * 0.35f
    val ry = anim.rollY * 0.35f
    val bloom = anim.bloom
    val fr = anim.fringe
    val warp = anim.warpAmount

    val phosphor = BareColors.TechGreen
    val cyan = BareColors.NeonCyan
    val magenta = BareColors.NeonMagenta

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .alpha(oa)
            .graphicsLayer {
                translationX = jx
                translationY = ry
                val s = 0.988f + 0.012f * b
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val inset = 4.dp.toPx()
                val corner = 18.dp.toPx()

                // Phosphor wash (translucent)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            phosphor.copy(alpha = 0.28f * b * (0.5f + 0.5f * warp)),
                            cyan.copy(alpha = 0.12f * b),
                            phosphor.copy(alpha = 0.08f * b),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        radius = size.minDimension * 0.78f,
                    ),
                )
                drawRect(color = Color(0xFF001806).copy(alpha = 0.10f * b))

                if (b < 0.98f) {
                    drawRect(color = Color.White.copy(alpha = 0.16f * (1f - b)))
                    drawRect(color = phosphor.copy(alpha = 0.10f * (1f - b)))
                }

                // Scanlines (overlay accent; shader also scanline-darkens warped pixels on API 33+)
                val step = 2.8.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.32f + 0.12f * (1f - b)),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.55f,
                    )
                    if ((y / step).toInt() % 2 == 0) {
                        drawLine(
                            color = cyan.copy(alpha = 0.09f * b),
                            start = Offset(0f, y + 1.2f),
                            end = Offset(size.width, y + 1.2f),
                            strokeWidth = 1.1f,
                        )
                    }
                    y += step
                }

                // Tube vignette
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF050308).copy(alpha = 0.38f * b),
                            Color(0xFF020104).copy(alpha = 0.70f + 0.18f * (1f - b)),
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.60f,
                    ),
                )

                if (fr > 0.02f) {
                    val fringeW = 10.dp.toPx() * fr
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                cyan.copy(alpha = 0.36f * fr * b),
                                Color.Transparent,
                            ),
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fringeW * 2.4f, size.height),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                magenta.copy(alpha = 0.34f * fr * b),
                            ),
                        ),
                        topLeft = Offset(size.width - fringeW * 2.4f, 0f),
                        size = Size(fringeW * 2.4f, size.height),
                    )
                }

                drawRoundRect(
                    color = cyan.copy(alpha = 0.55f * b),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 2.2.dp.toPx()),
                )
                drawRoundRect(
                    color = magenta.copy(alpha = 0.40f * b),
                    topLeft = Offset(inset + 2.5.dp.toPx(), inset + 2.5.dp.toPx()),
                    size = Size(
                        size.width - 2 * inset - 5.dp.toPx(),
                        size.height - 2 * inset - 5.dp.toPx(),
                    ),
                    cornerRadius = CornerRadius(corner - 2.dp.toPx(), corner - 2.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx()),
                )
                drawRoundRect(
                    color = phosphor.copy(alpha = 0.16f * b),
                    topLeft = Offset(inset - 1.dp.toPx(), inset - 1.dp.toPx()),
                    size = Size(
                        size.width - 2 * inset + 2.dp.toPx(),
                        size.height - 2 * inset + 2.dp.toPx(),
                    ),
                    cornerRadius = CornerRadius(corner + 2.dp.toPx(), corner + 2.dp.toPx()),
                    style = Stroke(width = 3.2.dp.toPx()),
                )

                if (b < 0.98f) {
                    val bandY = size.height * (0.10f + 0.80f * b)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                phosphor.copy(alpha = 0.30f * b),
                                cyan.copy(alpha = 0.20f * b),
                                Color.White.copy(alpha = 0.09f * b),
                                Color.Transparent,
                            ),
                        ),
                        topLeft = Offset(0f, bandY - 14.dp.toPx()),
                        size = Size(size.width, 28.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = (0.35f + 0.65f * b).coerceIn(0f, 1f)
                }
                .padding(horizontal = 20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    phosphor.copy(alpha = 0.38f * bloom * b),
                                    cyan.copy(alpha = 0.14f * bloom * b),
                                    Color.Transparent,
                                ),
                            ),
                            topLeft = Offset(-10.dp.toPx(), -8.dp.toPx()),
                            size = Size(size.width + 20.dp.toPx(), size.height + 16.dp.toPx()),
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color(0xE6000804),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color(0xFF00140A).copy(alpha = 0.72f * b),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    phosphor.copy(alpha = 0.18f * bloom * b),
                                    Color.Transparent,
                                    cyan.copy(alpha = 0.10f * bloom * b),
                                ),
                            ),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                        drawRoundRect(
                            color = phosphor.copy(alpha = 0.55f * b),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                            style = Stroke(width = 1.4.dp.toPx()),
                        )
                        drawRoundRect(
                            color = cyan.copy(alpha = 0.35f * b),
                            topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                            size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                            style = Stroke(width = 1.0.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "POL-80 // ONLINE",
                    color = magenta.copy(alpha = 0.55f * bloom * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = -3.8f * (0.45f + fr)
                        translationY = 1.2f
                        scaleX = 1.16f
                        scaleY = 1.28f
                    },
                )
                Text(
                    text = "POL-80 // ONLINE",
                    color = cyan.copy(alpha = 0.62f * bloom * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = 3.4f * (0.45f + fr)
                        translationY = -0.8f
                        scaleX = 1.12f
                        scaleY = 1.18f
                    },
                )
                Text(
                    text = "POL-80 // ONLINE",
                    color = phosphor.copy(alpha = 0.70f * bloom * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.14f
                    },
                )
                Text(
                    text = "POL-80 // ONLINE",
                    color = Color(0xFFE8FFD0).copy(alpha = (0.96f + 0.04f * bloom) * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xCC000804),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        )
                        drawRoundRect(
                            color = phosphor.copy(alpha = 0.28f * b),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = 1.0.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "> CRT BOOT  ·  TUBE WARP",
                    color = Color(0xFFB8FFF0).copy(alpha = 0.98f * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HOME READY",
                    color = Color(0xFFD4FFB0).copy(alpha = 0.95f * b),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
            }
        }
    }
}

/**
 * Short unplug scanline flicker. No boot title, no home AGSL.
 * Removed when overlayAlpha hits 0 (animation end + hard timeout + onDispose).
 */
@Composable
internal fun CrtPowerDownFlicker(anim: CrtBootAnim) {
    if (!anim.showing && anim.overlayAlpha < 0.01f) return
    val b = anim.brightness.coerceIn(0f, 1f)
    val oa = anim.overlayAlpha.coerceIn(0f, 1f)
    val phosphor = BareColors.TechGreen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(110f)
            .graphicsLayer {
                alpha = oa
            }
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.14f * b))
                drawRect(phosphor.copy(alpha = 0.12f * b))
                val step = 2.6.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.36f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.35f,
                    )
                    y += step
                }
                // Thin mid-band flash (visual only — home layer is never collapsed).
                val slitH = 10.dp.toPx()
                val slitY = size.height * 0.5f - slitH / 2f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            phosphor.copy(alpha = 0.40f * b),
                            BareColors.NeonCyan.copy(alpha = 0.22f * b),
                            phosphor.copy(alpha = 0.40f * b),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(0f, slitY),
                    size = Size(size.width, slitH),
                )
            },
    )
}


/** Every RuntimeShader / RenderEffect reference lives here; only touched on API 33+. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object CrtShaderApi33 {
    fun create(): Any? = try {
        RuntimeShader(CRT_WARP_AGSL)
    } catch (_: Exception) {
        null
    }

    fun effect(
        shaderAny: Any,
        w: Float, h: Float, amount: Float, fringe: Float, roll: Float, brightness: Float, time: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val shader = shaderAny as? RuntimeShader ?: return null
        shader.setFloatUniform("resolution", w, h)
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("fringe", fringe)
        shader.setFloatUniform("roll", roll)
        shader.setFloatUniform("brightness", brightness)
        shader.setFloatUniform("time", time)
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
