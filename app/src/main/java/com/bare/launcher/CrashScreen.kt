package com.bare.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * v0.54: plain android.widget error screen shown on the launch after a startup crash.
 * No Compose, no app theme lookups, so it still works when the Compose UI is what crashes.
 */
object CrashScreen {

    fun show(activity: Activity, report: CrashBeacon.Report, onContinue: () -> Unit) {
        val green = Color.rgb(0x39, 0xFF, 0x88)
        val pad = (16 * activity.resources.displayMetrics.density).toInt()

        val title = TextView(activity).apply {
            text = "POL-80 // FAULT ON LAST START"
            setTextColor(green)
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setPadding(0, 0, 0, pad / 2)
        }
        val hint = TextView(activity).apply {
            text = "The launcher crashed while starting. Share this report with Pedro, then tap Continue."
            setTextColor(Color.rgb(0xA0, 0xB0, 0xA8))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 0, 0, pad / 2)
        }
        val body = TextView(activity).apply {
            text = report.text
            setTextColor(green)
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(activity).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        fun button(label: String, action: () -> Unit) = Button(activity).apply {
            text = label
            typeface = Typeface.MONOSPACE
            isAllCaps = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        // v0.55 rescue: check GitHub right away; offer the fix if one is released.
        val updateStatus = TextView(activity).apply {
            text = "checking GitHub for a fix…"
            setTextColor(Color.rgb(0xA0, 0xB0, 0xA8))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
        }
        val updateButton = Button(activity).apply {
            typeface = Typeface.MONOSPACE
            isAllCaps = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        var found: UpdateChecker.LatestInfo? = null
        updateButton.setOnClickListener {
            val info = found ?: return@setOnClickListener
            try {
                if (!RescueUpdater.canInstall(activity)) {
                    updateStatus.text = "allow \"Install unknown apps\" for POL-80, then come back and tap UPDATE again"
                    if (!RescueUpdater.openInstallSettings(activity)) {
                        updateStatus.text = "open Settings → Apps → POL-80 → Install unknown apps, allow it, then tap UPDATE"
                    }
                    return@setOnClickListener
                }
                updateButton.isEnabled = false
                updateStatus.text = "downloading…"
                RescueUpdater.downloadAndInstallAsync(
                    activity,
                    info,
                    onStatus = { updateStatus.text = it },
                    onDone = { err ->
                        updateButton.isEnabled = true
                        updateStatus.text = if (err == null) {
                            "installer opened — confirm Install, then open POL-80 again"
                        } else {
                            "update failed: $err — tap UPDATE to retry"
                        }
                    },
                )
            } catch (t: Throwable) {
                updateButton.isEnabled = true
                updateStatus.text = "update failed: ${t.message ?: t.javaClass.simpleName}"
            }
        }
        RescueUpdater.checkAsync { result ->
            when (result) {
                is RescueUpdater.Check.Newer -> {
                    found = result.info
                    val short = result.info.versionName.substringBefore('-')
                    updateButton.text = "UPDATE TO $short"
                    updateButton.visibility = View.VISIBLE
                    updateStatus.text = "fix available: ${result.info.versionName} (this is ${BuildConfig.VERSION_NAME})"
                }
                is RescueUpdater.Check.NoneNewer ->
                    updateStatus.text = "no fix released yet — POL-80 checks again next time you open it"
                is RescueUpdater.Check.Failed ->
                    updateStatus.text = "update check failed (${result.message}) — retries next time you open POL-80"
            }
        }

        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, 0)
            addView(button("Share") { share(activity, report.text) })
            addView(button("Copy") { copy(activity, report.text) })
            addView(button("Continue") {
                CrashBeacon.clearReport(activity)
                onContinue()
            })
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(0x05, 0x0A, 0x08))
            // Generous top/bottom padding instead of insets APIs (works API 26+).
            setPadding(pad, pad * 3, pad, pad * 2)
            addView(title)
            addView(hint)
            addView(scroll)
            addView(updateStatus)
            addView(updateButton)
            addView(buttons)
        }
        activity.setContentView(root)
    }

    private fun share(context: Context, text: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "POL-80 crash report")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Share crash report"))
        } catch (_: Exception) {
            copy(context, text)
        }
    }

    private fun copy(context: Context, text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("POL-80 crash report", text))
            Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }
}
