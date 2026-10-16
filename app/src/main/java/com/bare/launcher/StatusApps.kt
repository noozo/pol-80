package com.bare.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.Telephony
import android.widget.Toast

/**
 * Resolve + launch the right app for each status metric.
 * Tries known packages / system intents; falls back to ACTION_VIEW / Play Store.
 */
object StatusApps {

    enum class Target { CALL, SMS, WA, PR, SL }

    private val WA_CANDIDATES = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val PROTON_CANDIDATES = listOf(
        "ch.protonmail.android",
        "ch.protonmail.android.demo",
    )
    private val SLACK_CANDIDATES = listOf(
        "com.Slack",
        "com.tinyspeck.slackandroid",
    )
    private val DIALER_CANDIDATES = listOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
    )
    private val SMS_CANDIDATES = listOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
    )

    fun open(context: Context, target: Target) {
        val ok = when (target) {
            Target.CALL -> openCall(context)
            Target.SMS -> openSms(context)
            Target.WA -> openPackageOrMarket(context, WA_CANDIDATES, "com.whatsapp", whatsappDeep = true)
            Target.PR -> openPackageOrMarket(context, PROTON_CANDIDATES, "ch.protonmail.android")
            Target.SL -> openPackageOrMarket(context, SLACK_CANDIDATES, "com.Slack")
        }
        if (!ok) {
            Toast.makeText(context, "Could not open ${target.name}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Call back a missed number. CALL_PHONE is not declared, so this is always
     * ACTION_DIAL (user confirms). Unknown number or a failed dial falls back
     * to the Phone / dialer app. Never throws.
     */
    fun dialNumber(context: Context, number: String?): Boolean {
        val raw = number?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$raw"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (safeStart(context, dial)) return true
        }
        return try {
            openCall(context)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * SMS compose to [number] with body "on my way". No SEND_SMS permission.
     * Missing number or no handler → existing SMS app path. Never throws.
     */
    fun smsOnMyWay(context: Context, number: String?): Boolean {
        val raw = number?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val body = ON_MY_WAY
            val encoded = Uri.encode(raw)
            val intents = listOf(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$encoded")).apply {
                    putExtra("sms_body", body)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent(Intent.ACTION_VIEW, Uri.parse("sms:$encoded?body=${Uri.encode(body)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$encoded")).apply {
                    putExtra("sms_body", body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            for (intent in intents) {
                if (safeStart(context, intent)) return true
            }
        }
        return try {
            openSms(context)
        } catch (_: Exception) {
            false
        }
    }

    private const val ON_MY_WAY = "on my way"

    private fun openCall(context: Context): Boolean {
        // Prefer call log (missed-call context), then dialer / ACTION_DIAL.
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent("android.intent.action.CALL_BUTTON")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_DIAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in intents) {
            if (safeStart(context, intent)) return true
        }
        return openFirstInstalled(context, DIALER_CANDIDATES)
            || openMarket(context, "com.google.android.dialer")
    }

    private fun openSms(context: Context): Boolean {
        val defaultSms = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
        if (!defaultSms.isNullOrBlank() && launchPackage(context, defaultSms)) return true

        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("smsto:"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in intents) {
            if (safeStart(context, intent)) return true
        }
        return openFirstInstalled(context, SMS_CANDIDATES)
            || openMarket(context, "com.google.android.apps.messaging")
    }

    private fun openPackageOrMarket(
        context: Context,
        packages: List<String>,
        marketPackage: String,
        whatsappDeep: Boolean = false,
    ): Boolean {
        if (openFirstInstalled(context, packages)) return true
        if (whatsappDeep) {
            val wa = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (safeStart(context, wa)) return true
        }
        return openMarket(context, marketPackage)
    }

    private fun openFirstInstalled(context: Context, packages: List<String>): Boolean {
        for (pkg in packages) {
            if (launchPackage(context, pkg)) return true
        }
        return false
    }

    private fun launchPackage(context: Context, packageName: String): Boolean {
        if (!isInstalled(context, packageName)) return false
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return safeStart(context, launch)
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openMarket(context: Context, packageName: String): Boolean {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (safeStart(context, market)) return true
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, web)
    }

    private fun safeStart(context: Context, intent: Intent): Boolean {
        return try {
            // Prefer resolved activities; still try VIEW market/web links.
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null && intent.data?.scheme !in setOf("market", "http", "https", "whatsapp", "sms", "smsto", "tel")) {
                return false
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
