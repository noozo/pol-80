package com.bare.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact tech-header counters + expandable drawer extras.
 * Missing permission → null (UI shows "—" or muted).
 * WhatsApp / Proton Mail / Slack are notification-based only (no storage scrape).
 *
 * CALL / SMS update via ContentObserver (threads + SMS/MMS URIs) plus a short
 * poll and an onResume force-refresh from MainActivity. SMS badge uses unread
 * non-archived Threads (not raw Sms.Inbox READ=0) so Google Messages mark-all
 * clears the count.
 */
data class NextCalendarEvent(
    val title: String,
    val startMillis: Long,
)

data class TechStatus(
    val missedCalls: Int? = null,
    val unreadSms: Int? = null,
    val unreadWhatsApp: Int? = null,
    val unreadProton: Int? = null,
    val unreadSlack: Int? = null,
    val notificationAccess: Boolean = false,
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val networkLabel: String? = null,
    val nextEvent: NextCalendarEvent? = null,
    val calendarPermission: Boolean = false,
    /** CRT radio-weather from live Wi‑Fi / cell quality. */
    val signalWeather: SignalWeatherReading = SignalWeatherReading(SignalWx.UNKNOWN),
)

data class MissedCallHit(
    val id: Long,
    val number: String?,
    val dateMillis: Long,
)

object StatusMonitor {

    /** Poll interval while home is visible (backup to ContentObserver). */
    const val POLL_INTERVAL_MS = 3_000L

    /** CALL chip phosphor ring after a new missed call. */
    const val RADAR_WINDOW_MS = 90_000L

    private const val RADAR_PREFS = "bare_call_radar"
    private const val KEY_RADAR_ACK_ID = "ack_call_id"

    /** Value color thresholds for CALL/SMS/WA/PR/SL (easy to tweak). */
    const val STATUS_AMBER_AT = 1
    const val STATUS_RED_AT = 5

    fun snapshot(context: Context): TechStatus {
        val nls = BareNotificationListener.isConnected()
        val bat = battery(context)
        return TechStatus(
            missedCalls = missedCalls(context),
            unreadSms = unreadSms(context),
            unreadWhatsApp = BareNotificationListener.whatsAppUnread(),
            unreadProton = BareNotificationListener.protonMailUnread(),
            unreadSlack = BareNotificationListener.slackUnread(),
            notificationAccess = nls,
            batteryPercent = bat.first,
            batteryCharging = bat.second,
            networkLabel = networkLabel(context),
            nextEvent = nextCalendarEvent(context),
            calendarPermission = hasCalendarPermission(context),
            signalWeather = SignalWeather.snapshot(context),
        )
    }

    fun hasCallLogPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Register ContentObservers for SMS/MMS/Threads + CallLog so mark-as-read /
     * missed-call changes reach the header immediately. Returns an unregister
     * lambda. Safe to call with missing permissions (no-ops those URIs).
     */
    fun registerCallSmsObservers(
        context: Context,
        onChanged: () -> Unit,
    ): () -> Unit {
        val appCtx = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var pending = false
        val deliver = Runnable {
            pending = false
            onChanged()
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (pending) return
                pending = true
                handler.postDelayed(deliver, 80L)
            }
        }
        val cr = appCtx.contentResolver
        try {
            if (hasSmsPermission(appCtx)) {
                // Message rows (SMS/MMS) + conversation threads (what Messages mark-all updates).
                cr.registerContentObserver(Telephony.Sms.Inbox.CONTENT_URI, true, observer)
                cr.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                cr.registerContentObserver(Telephony.Mms.Inbox.CONTENT_URI, true, observer)
                cr.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
                cr.registerContentObserver(Telephony.Threads.CONTENT_URI, true, observer)
                cr.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
            }
        } catch (_: Exception) {
        }
        try {
            if (hasCallLogPermission(appCtx)) {
                cr.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            }
        } catch (_: Exception) {
        }
        return {
            handler.removeCallbacks(deliver)
            try {
                cr.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    private fun missedCalls(context: Context): Int? {
        if (!hasCallLogPermission(context)) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"),
                null,
            ).useCount()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unread SMS/MMS as Google Messages shows them: count of non-archived
     * conversations with READ=0 on Telephony.Threads.
     *
     * Why not Sms.Inbox READ=0? That counts every leftover inbox *row*. Google
     * Messages (esp. RCS) "Mark all as read" updates thread read state and the
     * Messages UI, but often leaves orphan SMS rows at READ=0 (archived/spam/
     * old carrier stubs). That made the header stick (e.g. SMS:9) after the
     * inbox looked empty. Threads match the UI; archived is excluded so muted
     * archive unreads do not inflate the badge.
     *
     * Fallback: SMS inbox + MMS inbox message counts if Threads is unavailable.
     */
    private fun unreadSms(context: Context): Int? {
        if (!hasSmsPermission(context)) return null
        unreadThreads(context)?.let { return it }
        return unreadSmsMmsMessages(context)
    }

    private fun unreadThreads(context: Context): Int? {
        return try {
            // Telephony.Threads.CONTENT_URI is content://mms-sms/conversations?simple=true
            context.contentResolver.query(
                Telephony.Threads.CONTENT_URI,
                arrayOf(Telephony.Threads._ID),
                "${Telephony.Threads.READ}=0 AND ${Telephony.Threads.ARCHIVED}=0",
                null,
                null,
            ).useCount()
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // Some OEMs reject ARCHIVED in selection — retry read-only.
            try {
                context.contentResolver.query(
                    Telephony.Threads.CONTENT_URI,
                    arrayOf(Telephony.Threads._ID),
                    "${Telephony.Threads.READ}=0",
                    null,
                    null,
                ).useCount()
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unreadSmsMmsMessages(context: Context): Int? {
        return try {
            val sms = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ}=0",
                null,
                null,
            ).useCount() ?: return null
            val mms = try {
                context.contentResolver.query(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID),
                    "${Telephony.Mms.READ}=0",
                    null,
                    null,
                ).useCount() ?: 0
            } catch (_: Exception) {
                0
            }
            sms + mms
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun battery(context: Context): Pair<Int?, Boolean> {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
            val sticky = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                (bm?.isCharging == true)
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val fromSticky = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }
            (pct ?: fromSticky) to charging
        } catch (_: Exception) {
            null to false
        }
    }

    private fun networkLabel(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network = cm.activeNetwork ?: return "OFFLINE"
            val caps = cm.getNetworkCapabilities(network) ?: return "OFFLINE"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val ssid = wifiSsid(context)
                    if (ssid.isNullOrBlank()) "Wi‑Fi" else "Wi‑Fi $ssid"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                else -> "Online"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun wifiSsid(context: Context): String? {
        // SSID requires location (or NEARBY_WIFI_DEVICES on 33+) on modern Android.
        if (!hasLocationPermission(context) &&
            !(Build.VERSION.SDK_INT >= 33 && hasNearbyWifiPermission(context))
        ) {
            return null
        }
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo ?: return null
            @Suppress("DEPRECATION")
            val raw = info.ssid ?: return null
            val cleaned = raw.trim('"')
            if (cleaned.isEmpty() || cleaned.equals("<unknown ssid>", ignoreCase = true)) {
                null
            } else {
                cleaned
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasNearbyWifiPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun nextCalendarEvent(context: Context): NextCalendarEvent? {
        if (!hasCalendarPermission(context)) return null
        val now = System.currentTimeMillis()
        val windowEnd = now + 14L * 24 * 60 * 60 * 1000 // two weeks
        return try {
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
            )
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(windowEnd.toString())
                .build()
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.ALL_DAY}=0 OR ${CalendarContract.Instances.ALL_DAY}=1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val title = cursor.getString(0)?.trim().orEmpty().ifBlank { "(no title)" }
                val begin = cursor.getLong(1)
                NextCalendarEvent(title = title, startMillis = begin)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun formatEventTime(millis: Long): String {
        val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    /**
     * Newest still-unread missed call, if READ_CALL_LOG is granted.
     * Null when permission is missing, the log is empty, or the miss was cleared
     * (NEW != 1). Never throws.
     */
    fun latestUnreadMissed(context: Context): MissedCallHit? {
        if (!hasCallLogPermission(context)) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                ),
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val raw = cursor.getString(1)?.trim().orEmpty()
                MissedCallHit(
                    id = cursor.getLong(0),
                    number = dialableNumber(raw),
                    dateMillis = cursor.getLong(2),
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pulse target: an unread miss newer than [RADAR_WINDOW_MS] that the user
     * has not already tapped. Cleared misses and missing permission → null.
     */
    fun activeRadar(context: Context, nowMillis: Long = System.currentTimeMillis()): MissedCallHit? {
        val hit = latestUnreadMissed(context) ?: return null
        if (hit.id == acknowledgedRadarId(context)) return null
        val age = nowMillis - hit.dateMillis
        if (age < 0L || age > RADAR_WINDOW_MS) return null
        return hit
    }

    fun acknowledgeRadar(context: Context, callId: Long) {
        try {
            context.applicationContext
                .getSharedPreferences(RADAR_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_RADAR_ACK_ID, callId)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun acknowledgedRadarId(context: Context): Long {
        return try {
            context.applicationContext
                .getSharedPreferences(RADAR_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_RADAR_ACK_ID, Long.MIN_VALUE)
        } catch (_: Exception) {
            Long.MIN_VALUE
        }
    }

    /** Keep a real dial string; drop private / empty / placeholder call-log values. */
    private fun dialableNumber(raw: String): String? {
        if (raw.isEmpty() || raw == "-1" || raw == "-2" || raw.equals("null", ignoreCase = true)) {
            return null
        }
        val cleaned = buildString(raw.length) {
            raw.forEachIndexed { index, c ->
                when {
                    c.isDigit() || c == '*' || c == '#' -> append(c)
                    c == '+' && index == 0 -> append(c)
                }
            }
        }
        return cleaned.takeIf { it.any { ch -> ch.isDigit() } }
    }

    private fun Cursor?.useCount(): Int? {
        if (this == null) return null
        return use { it.count }
    }
}
