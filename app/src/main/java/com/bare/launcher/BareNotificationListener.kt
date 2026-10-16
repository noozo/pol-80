package com.bare.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Best-effort unread counts + recent notification title digests
 * (WhatsApp, Proton Mail, Slack, SMS, Call).
 * User must enable Bare under Settings → Notification access.
 * Does not scrape app storage — only StatusBarNotification payloads.
 */
class BareNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance.set(this)
        refresh()
    }

    override fun onListenerDisconnected() {
        instance.compareAndSet(this, null)
        cachedWa.set(-1)
        cachedProton.set(-1)
        cachedSlack.set(-1)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) recordPosted(sbn)
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        cachedWa.set(countFor(active, WA_PACKAGES))
        cachedProton.set(countFor(active, PROTON_MAIL_PACKAGES))
        cachedSlack.set(countFor(active, SLACK_PACKAGES))
        seedDigestsFromActive(active)
    }

    private fun seedDigestsFromActive(active: Array<StatusBarNotification>) {
        val collected = linkedMapOf<String, MutableList<NotifDigest>>()
        for (sbn in active) {
            val channel = channelFor(sbn.packageName) ?: continue
            val digest = digestFrom(sbn) ?: continue
            collected.getOrPut(channel) { mutableListOf() }.add(digest)
        }
        for ((channel, items) in collected) {
            val list = digests.getOrPut(channel) { CopyOnWriteArrayList() }
            synchronized(list) {
                if (list.isEmpty()) {
                    val top = items
                        .sortedByDescending { it.whenMs }
                        .distinctBy { it.title }
                        .take(DIGEST_LIMIT)
                    list.addAll(top)
                }
            }
        }
    }

    private fun recordPosted(sbn: StatusBarNotification) {
        val digest = digestFrom(sbn) ?: return
        val channel = channelFor(sbn.packageName) ?: return
        val list = digests.getOrPut(channel) { CopyOnWriteArrayList() }
        synchronized(list) {
            list.removeAll { it.title == digest.title }
            list.add(0, digest)
            while (list.size > DIGEST_LIMIT) {
                list.removeAt(list.lastIndex)
            }
        }
    }

    private fun digestFrom(sbn: StatusBarNotification): NotifDigest? {
        if (channelFor(sbn.packageName) == null) return null
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
        val whenMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        return NotifDigest(
            title = title,
            text = text,
            whenMs = whenMs,
            packageName = sbn.packageName,
        )
    }

    companion object {
        const val DIGEST_LIMIT = 3

        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val PROTON_MAIL_PACKAGES = setOf(
            "ch.protonmail.android",
            "ch.protonmail.android.demo",
        )
        /** Consumer Slack (legacy + current package ids). */
        val SLACK_PACKAGES = setOf(
            "com.Slack",
            "com.tinyspeck.slackandroid",
        )
        val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.google.android.apps.dynamite", // Chat sometimes
        )
        val CALL_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.google.android.apps.tachyon", // Phone by Google / Duo remnant
        )

        private val instance = AtomicReference<BareNotificationListener?>(null)
        private val cachedWa = AtomicReference(-1)
        private val cachedProton = AtomicReference(-1)
        private val cachedSlack = AtomicReference(-1)
        private val digests = ConcurrentHashMap<String, CopyOnWriteArrayList<NotifDigest>>()

        fun isConnected(): Boolean = instance.get() != null

        val CLOCK_PACKAGES = setOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock",
            "com.motorola.timeweatheralarm",
        )

        /** Ongoing Clock timer/stopwatch notification, or null. Never throws. */
        fun activeClockTimer(): Notification? {
            return try {
                val svc = instance.get() ?: return null
                val active = svc.activeNotifications ?: return null
                active.firstOrNull { sbn ->
                    sbn != null && sbn.packageName in CLOCK_PACKAGES &&
                        sbn.notification != null &&
                        (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 &&
                        sbn.notification.category != Notification.CATEGORY_ALARM
                }?.notification
            } catch (_: Exception) {
                null
            }
        }

        /** null = listener not connected / unknown. */
        fun whatsAppUnread(): Int? {
            if (!isConnected()) return null
            val v = cachedWa.get()
            return if (v < 0) 0 else v
        }

        fun protonMailUnread(): Int? {
            if (!isConnected()) return null
            val v = cachedProton.get()
            return if (v < 0) 0 else v
        }

        fun slackUnread(): Int? {
            if (!isConnected()) return null
            val v = cachedSlack.get()
            return if (v < 0) 0 else v
        }

        fun channelFor(packageName: String): String? = when {
            packageName in WA_PACKAGES -> "WA"
            packageName in SLACK_PACKAGES -> "SL"
            packageName in PROTON_MAIL_PACKAGES -> "PR"
            packageName in SMS_PACKAGES -> "SMS"
            packageName in CALL_PACKAGES -> "CALL"
            else -> null
        }

        fun channelFor(target: StatusApps.Target): String = when (target) {
            StatusApps.Target.WA -> "WA"
            StatusApps.Target.SL -> "SL"
            StatusApps.Target.PR -> "PR"
            StatusApps.Target.SMS -> "SMS"
            StatusApps.Target.CALL -> "CALL"
        }

        /** Last ~3 notification titles for a status chip channel (newest first). */
        fun recentDigests(target: StatusApps.Target, limit: Int = DIGEST_LIMIT): List<NotifDigest> {
            val key = channelFor(target)
            val list = digests[key] ?: return emptyList()
            return synchronized(list) { list.take(limit) }
        }

        fun countFor(
            notifications: Array<StatusBarNotification>,
            packages: Set<String>,
        ): Int {
            var total = 0
            for (sbn in notifications) {
                if (sbn.packageName !in packages) continue
                val n = sbn.notification ?: continue
                if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                val number = n.number
                total += if (number > 0) number else 1
            }
            return total
        }
    }
}

data class NotifDigest(
    val title: String,
    val text: String?,
    val whenMs: Long,
    val packageName: String,
) {
    val displayLine: String
        get() = if (text.isNullOrBlank()) title else "$title — $text"
}
