package com.bare.launcher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Night phosphor chrome: mono green/cyan icon look + cyan labels after sunset.
 * Mode Auto (default) uses Open-Meteo sunrise/sunset when cached, else ~19:00–07:00
 * Europe/Lisbon. On/Off force the look regardless of clock.
 */
enum class PhosphorMode {
    Auto,
    On,
    Off,
    ;

    fun next(): PhosphorMode = when (this) {
        Auto -> On
        On -> Off
        Off -> Auto
    }

    val menuLabel: String
        get() = when (this) {
            Auto -> "Night phosphor: Auto"
            On -> "Night phosphor: On"
            Off -> "Night phosphor: Off"
        }
}

object PhosphorNight {

    val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")
    private val FALLBACK_START: LocalTime = LocalTime.of(19, 0)
    private val FALLBACK_END: LocalTime = LocalTime.of(7, 0)

    /** Green/cyan CRT phosphor remap (luminance → phosphor). */
    fun iconColorFilter(): ColorFilter {
        // R/G/B rows map input RGB into a green-cyan mono look.
        val m = ColorMatrix(
            floatArrayOf(
                0.12f, 0.28f, 0.06f, 0f, 0f,
                0.38f, 0.72f, 0.18f, 0f, 28f,
                0.28f, 0.52f, 0.42f, 0f, 52f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return ColorFilter.colorMatrix(m)
    }

    @Composable
    fun rememberIconColorFilter(): ColorFilter = remember { iconColorFilter() }

    fun isActive(context: Context, mode: PhosphorMode, nowMs: Long = System.currentTimeMillis()): Boolean {
        return when (mode) {
            PhosphorMode.On -> true
            PhosphorMode.Off -> false
            PhosphorMode.Auto -> isNight(context, nowMs)
        }
    }

    /**
     * Night = before today's sunrise OR at/after today's sunset (Open-Meteo local).
     * Fallback clock window 19:00–07:00 in Europe/Lisbon when sun times missing.
     */
    fun isNight(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val sun = WeatherRepo.cachedSun(context)
        if (sun != null) {
            return nowMs < sun.sunriseMs || nowMs >= sun.sunsetMs
        }
        val local = Instant.ofEpochMilli(nowMs).atZone(LISBON).toLocalTime()
        return !local.isBefore(FALLBACK_START) || local.isBefore(FALLBACK_END)
    }
}
