package com.bare.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Plug-in ritual: phosphor "docked" stamp while power is connected or the
 * battery is charging, plus a one-shot replay of the short CRT home warp
 * on the transition into that state. The reverse edge (docked → unplugged)
 * plays a short CRT power-down sting, also once — not on battery ticks.
 *
 * Battery percentage ticks must not retrigger either effect. A process that
 * is already docked at first read shows the stamp and does not replay.
 * Unknown previous state is neither a plug-in nor an unplug.
 * BatteryManager / receiver failures are swallowed.
 */
internal object ChargeRitual {

    const val STAMP = "docked"

    /**
     * True only on a known not-docked → docked edge.
     * Null previous means we have not observed a real state yet (failed
     * first read, or not primed): do not treat that as a plug-in.
     */
    fun shouldReplayWarp(previousDocked: Boolean?, nowDocked: Boolean): Boolean {
        return previousDocked == false && nowDocked
    }

    /**
     * True only on a known docked → not-docked edge.
     * Null previous is not an unplug (failed first read, or not primed).
     */
    fun shouldPlayUnplugSting(previousDocked: Boolean?, nowDocked: Boolean): Boolean {
        return previousDocked == true && !nowDocked
    }

    /**
     * Docked while a charger is attached (USB / AC / wireless / dock) or the
     * battery reports charging. Full-but-still-plugged stays docked.
     * Never throws.
     */
    fun isDocked(context: Context): Boolean {
        return try {
            val bm = try {
                context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            } catch (_: Exception) {
                null
            }
            val sticky = try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) {
                null
            }
            val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val chargingFlag = try {
                bm?.isCharging == true
            } catch (_: Exception) {
                false
            }
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || chargingFlag
            plugged != 0 || charging
        } catch (_: Exception) {
            false
        }
    }

    fun register(context: Context, receiver: BroadcastReceiver): Boolean {
        return try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                // Level ticks also arrive here. Caller must gate effects
                // with [shouldReplayWarp] / [shouldPlayUnplugSting].
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unregister(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}

/** One-line phosphor stamp. Not a home note — no tap, no swipe. */
@Composable
internal fun DockedPhosphorStamp(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = ChargeRitual.STAMP,
        color = BareColors.TechGreen,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
            .border(1.dp, BareColors.TechGreen.copy(alpha = 0.75f), shape)
            .background(BareColors.Bg.copy(alpha = 0.72f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
