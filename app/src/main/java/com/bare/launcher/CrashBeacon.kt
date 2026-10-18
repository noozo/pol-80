package com.bare.launcher

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-shot crash beacon for the launcher process.
 *
 * The default uncaught handler writes a single local fault line, then chains
 * the previous handler so Android still kills the process and restarts HOME.
 * The handler is installed once (Application.attachBaseContext and again from
 * the launcher Activity if Application was skipped) and never replaces itself.
 *
 * On the next launch a fresh, unread fault (last ~10 minutes) flashes in the
 * header. Tap copies the full line and deletes the file. Older files are
 * deleted unread so a beacon cannot spam.
 */
object CrashBeacon {

    private const val FILE_NAME = "crash_beacon.txt"
    private const val SEP = "\u001f"

    /** Show / keep a beacon only this long after the fault was written. */
    const val FRESH_MS = 10L * 60L * 1000L

    @Volatile
    private var installed = false

    /**
     * Chain the current default handler. Safe to call more than once.
     * Does not swallow the throwable.
     */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            // In Application.attachBaseContext applicationContext is still null.
            val app = context.applicationContext ?: context
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    write(app, throwable)
                } catch (_: Throwable) {
                }
                // Chain the system handler so the process still dies and HOME restarts.
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    throw throwable
                }
            }
            installed = true
        }
    }

    fun write(context: Context, throwable: Throwable) {
        val top = throwable.stackTrace.firstOrNull { frame ->
            val name = frame.className
            !name.startsWith("java.") &&
                !name.startsWith("kotlin.") &&
                !name.startsWith("android.") &&
                !name.startsWith("androidx.") &&
                !name.startsWith("com.android.")
        } ?: throwable.stackTrace.firstOrNull()
        val frame = if (top != null) {
            val cls = top.className.substringAfterLast('.')
            "${cls}.${top.methodName}:${top.lineNumber}"
        } else {
            "unknown"
        }
        val simple = throwable.javaClass.simpleName.ifBlank { "Exception" }
        val fullClass = throwable.javaClass.name.ifBlank { simple }
        val at = System.currentTimeMillis()
        val line = listOf(at.toString(), simple, fullClass, frame).joinToString(SEP)
        val dir = (context.applicationContext ?: context).filesDir
        val file = File(dir, FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(line)
        // v0.54: full report for the plain-View crash screen (shown only after a startup crash).
        try {
            writeReport(dir, throwable, at)
        } catch (_: Throwable) {
        }
    }

    // ---------------- v0.54 startup crash catcher ----------------

    private const val REPORT_FILE = "crash_report.txt"
    private const val REPORT_MAX_CHARS = 16_000
    /** Show a stored startup-crash report only this long after it was written. */
    const val REPORT_FRESH_MS = 24L * 60L * 60L * 1000L

    /**
     * True from MainActivity.onCreate until ~3s after the first onResume. A crash in that
     * window is a "startup crash" and earns the error screen on the next launch.
     */
    @Volatile
    var startupPhase: Boolean = false

    data class Report(val atMillis: Long, val startup: Boolean, val text: String)

    private fun writeReport(dir: File, throwable: Throwable, at: Long) {
        val stamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(at))
        } catch (_: Exception) {
            at.toString()
        }
        val header = buildString {
            append("POL-80 crash report\n")
            append("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            append("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})\n")
            append("time: $stamp\n")
            append("startup: $startupPhase\n\n")
        }
        val text = (header + throwable.stackTraceToString()).take(REPORT_MAX_CHARS)
        // First line is machine-readable: at|startup
        File(dir, REPORT_FILE).writeText("$at|$startupPhase\n$text")
    }

    /** A fresh report from a startup crash, or null. Never throws. */
    fun pendingStartupReport(context: Context, now: Long = System.currentTimeMillis()): Report? =
        try {
            val f = File(context.applicationContext.filesDir, REPORT_FILE)
            if (!f.isFile) {
                null
            } else {
                val raw = f.readText()
                val nl = raw.indexOf('\n')
                val meta = if (nl > 0) raw.substring(0, nl).split('|') else emptyList()
                val at = meta.getOrNull(0)?.toLongOrNull() ?: f.lastModified()
                val startup = meta.getOrNull(1) == "true"
                val age = now - at
                when {
                    age > REPORT_FRESH_MS || age < -60_000L -> {
                        f.delete()
                        null
                    }
                    !startup -> null
                    else -> Report(at, true, if (nl > 0) raw.substring(nl + 1) else raw)
                }
            }
        } catch (_: Throwable) {
            null
        }

    fun clearReport(context: Context) {
        try {
            File(context.applicationContext.filesDir, REPORT_FILE).delete()
        } catch (_: Exception) {
        }
    }

    data class Beacon(
        val code: String,
        val fullLine: String,
        val atMillis: Long,
    )

    /**
     * Fresh unread fault, or null. Deletes the file when it is older than
     * [FRESH_MS] so the next launch stays quiet. Never throws.
     */
    fun peek(context: Context, now: Long = System.currentTimeMillis()): Beacon? {
        return try {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (!file.exists() || !file.isFile) return null
            val raw = file.readText().trim()
            if (raw.isEmpty()) {
                file.delete()
                return null
            }
            val parts = raw.split(SEP)
            val at = parts.getOrNull(0)?.toLongOrNull() ?: file.lastModified()
            val age = now - at
            if (age > FRESH_MS || age < -60_000L) {
                file.delete()
                return null
            }
            val simple = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Fault"
            val fullClass = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: simple
            val frame = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "unknown"
            val code = "$simple@$frame".take(48)
            val stamp = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
            } catch (_: Exception) {
                at.toString()
            }
            Beacon(
                code = code,
                fullLine = "$fullClass @ $frame @ $stamp",
                atMillis = at,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    /** Copy the full one-liner, then clear. False if there is nothing to copy. */
    fun copyAndClear(context: Context): Boolean {
        val beacon = peek(context) ?: return false
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            cm.setPrimaryClip(ClipData.newPlainText("POL-80 fault", beacon.fullLine))
            clear(context)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class BareApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Before Application.onCreate / Activity.onCreate so launcher crashes
        // still hit this handler. Previous (system) handler stays chained.
        CrashBeacon.install(this)
    }
}
