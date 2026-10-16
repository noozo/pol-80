package com.bare.launcher

import android.app.AlarmManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ContextKind { TIMER, CHARGE, ALARM, LOW_BAT }

data class ContextReading(val kind: ContextKind, val text: String)

/**
 * v0.48 context chip: one header slot, highest-priority relevant fact only.
 * TIMER > CHG time-to-full > ALM within 12h > BAT <15%. Null = hide entirely.
 */
object ContextChipLogic {
    const val ALARM_WINDOW_MS = 12L * 60 * 60 * 1000
    const val LOW_BAT_PCT = 15

    fun read(context: Context, nowMs: Long = System.currentTimeMillis()): ContextReading? =
        try {
            timer(nowMs) ?: charge(context) ?: alarm(context, nowMs) ?: lowBattery(context)
        } catch (_: Exception) {
            null
        }

    private fun timer(nowMs: Long): ContextReading? = try {
        val n = BareNotificationListener.activeClockTimer() ?: return null
        val extras = n.extras
        val chrono = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true
        val countDown = Build.VERSION.SDK_INT >= 24 &&
            extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) == true
        val label = when {
            chrono && countDown && n.`when` > nowMs -> "TMR " + fmtDur(n.`when` - nowMs)
            chrono && !countDown && n.`when` in 1..nowMs -> "SW " + fmtDur(nowMs - n.`when`)
            else -> {
                val t = (extras?.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras?.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim()
                val m = t?.let { Regex("""\d{1,2}(:\d{2}){1,2}""").find(it)?.value }
                if (m != null) "TMR $m" else "TMR ON"
            }
        }
        ContextReading(ContextKind.TIMER, label)
    } catch (_: Exception) { null }

    private fun batteryIntent(context: Context): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Exception) { null }

    private fun charge(context: Context): ContextReading? {
        val i = batteryIntent(context) ?: return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (status != BatteryManager.BATTERY_STATUS_CHARGING && !(plugged && status != BatteryManager.BATTERY_STATUS_FULL)) return null
        if (Build.VERSION.SDK_INT < 28) return null
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val ms = try { bm.computeChargeTimeRemaining() } catch (_: Exception) { -1L }
        if (ms <= 0L) return null
        return ContextReading(ContextKind.CHARGE, "CHG " + fmtDur(ms))
    }

    private fun alarm(context: Context, nowMs: Long): ContextReading? {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val next = try { am.nextAlarmClock } catch (_: Exception) { null } ?: return null
        val t = next.triggerTime
        if (t <= nowMs || t - nowMs > ALARM_WINDOW_MS) return null
        val hhmm = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
        return ContextReading(ContextKind.ALARM, "ALM $hhmm")
    }

    private fun lowBattery(context: Context): ContextReading? {
        val i = batteryIntent(context) ?: return null
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (plugged) return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val pct = level * 100 / scale
        if (pct >= LOW_BAT_PCT) return null
        return ContextReading(ContextKind.LOW_BAT, "BAT $pct%")
    }

    fun fmtDur(ms: Long): String {
        val totalMin = (ms + 59_999L) / 60_000L
        val sec = ms / 1000L
        return when {
            totalMin >= 60 -> "${totalMin / 60}h${(totalMin % 60).toString().padStart(2, '0')}m"
            sec >= 60 -> "${totalMin}m"
            else -> "${sec}s"
        }
    }

    fun onTap(context: Context, reading: ContextReading) {
        val intent = when (reading.kind) {
            ContextKind.ALARM -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            ContextKind.TIMER -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
            ContextKind.CHARGE, ContextKind.LOW_BAT -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (_: Exception) {
            if (reading.kind == ContextKind.TIMER) {
                try { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        }
    }
}

/** Header slot. Emits nothing (no gap) when there is nothing relevant. */
@Composable
fun ContextChip(refreshKey: Int) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<ContextReading?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { bump++ }
        }
        val f = IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(r, f)
            true
        } catch (_: Exception) { false }
        onDispose { if (ok) try { context.unregisterReceiver(r) } catch (_: Exception) {} }
    }
    LaunchedEffect(refreshKey, bump) {
        reading = ContextChipLogic.read(context)
    }
    LaunchedEffect(Unit) {
        while (true) {
            // Timer ticks need 1s; otherwise status-poll pace.
            delay(if (reading?.kind == ContextKind.TIMER) 1_000L else 5_000L)
            reading = ContextChipLogic.read(context)
        }
    }
    val r = reading ?: return
    val color = when (r.kind) {
        ContextKind.LOW_BAT -> BareColors.StatusAmber
        ContextKind.ALARM -> BareColors.NeonCyan
        ContextKind.TIMER -> BareColors.NeonMagentaSoft
        ContextKind.CHARGE -> BareColors.TechGreen
    }
    Text(
        text = r.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = Modifier
            .padding(top = 2.dp)
            .clickable { ContextChipLogic.onTap(context, r) },
    )
}
