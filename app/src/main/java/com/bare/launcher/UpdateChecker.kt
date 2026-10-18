package com.bare.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * GitHub-only OTA updates for POL-80 (0.51+).
 *
 * Reads https://api.github.com/repos/noozo/pol-80/releases/latest anonymously: the
 * latest.json asset first, else versionCode from the tag (v0.51 -> 51) with the sha256
 * from the release body. When remote versionCode > local, downloads the APK asset
 * (redirects followed), verifies sha256, and launches the package installer.
 *
 * Snooze: tapping Later stores the remote versionCode + 24h; prompts resume only when
 * a newer versionCode appears or the 24h window elapses.
 */
object UpdateChecker {

    data class LatestInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String?,
        val sha256: String,
        val notes: String?,
    )

    sealed class CheckResult {
        data class Available(val info: LatestInfo) : CheckResult()
        data object UpToDate : CheckResult()
        data object Snoozed : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    sealed class InstallPrepResult {
        data class Ready(val uri: Uri) : InstallPrepResult()
        data class NeedPermission(val settingsIntent: Intent) : InstallPrepResult()
        data class Failed(val message: String) : InstallPrepResult()
    }

    private const val PREFS = "bare_updates"
    private const val KEY_SNOOZE_VERSION = "snooze_version_code"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_ms"
    private const val SNOOZE_MS = 24L * 60L * 60L * 1000L
    const val AUTO_CHECK_INTERVAL_MS = 15L * 60L * 1000L

    fun localVersionCode(): Int = BuildConfig.VERSION_CODE

    fun localVersionName(): String = BuildConfig.VERSION_NAME

    suspend fun check(context: Context, manual: Boolean): CheckResult =
        withContext(Dispatchers.IO) {
            val info = try {
                fetchGithubLatest()
            } catch (e: Exception) {
                return@withContext CheckResult.Error(e.message ?: "GitHub check failed")
            } ?: return@withContext CheckResult.Error("No usable GitHub release")
            if (info.versionCode <= localVersionCode()) {
                return@withContext CheckResult.UpToDate
            }
            if (!manual && isSnoozed(context, info.versionCode)) {
                return@withContext CheckResult.Snoozed
            }
            CheckResult.Available(info)
        }

    fun snooze(context: Context, remoteVersionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_SNOOZE_VERSION, remoteVersionCode)
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
    }

    fun clearSnooze(context: Context) {
        prefs(context).edit()
            .remove(KEY_SNOOZE_VERSION)
            .remove(KEY_SNOOZE_UNTIL)
            .apply()
    }

    private fun isSnoozed(context: Context, remoteVersionCode: Int): Boolean {
        val p = prefs(context)
        val snoozedVer = p.getInt(KEY_SNOOZE_VERSION, -1)
        val until = p.getLong(KEY_SNOOZE_UNTIL, 0L)
        if (snoozedVer < 0) return false
        // New version supersedes snooze for an older remote build.
        if (remoteVersionCode > snoozedVer) return false
        // Same (or older) remote version: respect 24h window.
        return System.currentTimeMillis() < until
    }

    suspend fun downloadAndVerify(
        context: Context,
        info: LatestInfo,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): InstallPrepResult = withContext(Dispatchers.IO) {
        downloadAndVerifyBlocking(context, info, onProgress)
    }

    /** Blocking variant for the plain-View rescue path (call off the main thread). */
    fun downloadAndVerifyBlocking(
        context: Context,
        info: LatestInfo,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): InstallPrepResult = run {
        val url = info.apkUrl?.trim()?.takeIf { it.startsWith("https://") }
            ?: return@run InstallPrepResult.Failed("No apkUrl")
        val expected = info.sha256.trim().lowercase()
        if (expected.length != 64 || expected.any { it !in "0123456789abcdef" }) {
            return@run InstallPrepResult.Failed("Invalid sha256 in latest.json")
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "POL-80-${info.versionName}.apk")
        try {
            if (out.exists()) out.delete()
            downloadToFile(url, out, onProgress)
            val actual = sha256Hex(out)
            if (actual != expected) {
                out.delete()
                return@run InstallPrepResult.Failed(
                    "SHA-256 mismatch (got ${actual.take(12)}…)",
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settings = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                    return@run InstallPrepResult.NeedPermission(settings)
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out,
            )
            InstallPrepResult.Ready(uri)
        } catch (e: Exception) {
            out.delete()
            InstallPrepResult.Failed(e.message ?: "download failed")
        }
    }

    fun launchInstaller(activity: Activity, apkUri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun parseLatest(json: String): LatestInfo? {
        val trimmed = json.trim().removePrefix("﻿")
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = JSONObject(trimmed.substring(start, end + 1))
        val code = root.optInt("versionCode", -1)
        if (code < 0) return null
        val name = root.optString("versionName", "").ifBlank { "unknown" }
        val sha = root.optString("sha256", "").ifBlank {
            root.optString("sha256sum", "")
        }
        if (sha.isBlank()) return null
        val apkUrl = root.optString("apkUrl", "")
            .ifBlank { root.optString("apk_url", "") }
            .ifBlank { root.optString("downloadUrl", "") }
            .ifBlank { null }
        val notes = root.optString("notes", "").ifBlank { null }
        return LatestInfo(
            versionCode = code,
            versionName = name,
            apkUrl = apkUrl,
            sha256 = sha.trim().lowercase(),
            notes = notes,
        )
    }

    /** Blocking GitHub releases/latest read for the rescue path (call off the main thread). */
    fun fetchLatestBlocking(): LatestInfo? = fetchGithubLatest()

    private const val KEY_LAST_LAUNCH_CHECK = "last_launch_check_ms"
    const val LAUNCH_CHECK_MIN_GAP_MS = 10L * 60L * 1000L

    /**
     * v0.55: cold-launch check gate. True (and records now) when the last launch check is
     * at least [LAUNCH_CHECK_MIN_GAP_MS] ago, so relaunch loops cannot hammer GitHub.
     */
    fun claimLaunchCheck(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_LAUNCH_CHECK, 0L)
        if (last in 1..now && now - last < LAUNCH_CHECK_MIN_GAP_MS) return false
        p.edit().putLong(KEY_LAST_LAUNCH_CHECK, now).apply()
        return true
    }

    const val GITHUB_LATEST_API = "https://api.github.com/repos/noozo/pol-80/releases/latest"

    /**
     * Anonymous GitHub releases/latest. Prefers a latest.json asset; otherwise derives
     * versionCode from the tag (v0.50 -> 50, v50 -> 50) and needs a sha256 in the body
     * ("sha256: <hex>"). The APK asset browser_download_url becomes apkUrl.
     */
    private fun fetchGithubLatest(): LatestInfo? {
        val conn = openFollow(GITHUB_LATEST_API, "application/vnd.github+json")
        val text = try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} from GitHub")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val root = JSONObject(text)
        val assets = root.optJSONArray("assets")
        var apkUrl: String? = null
        var jsonUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                val url = a.optString("browser_download_url", "")
                if (!url.startsWith("https://")) continue
                if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) apkUrl = url
                if (name.equals("latest.json", ignoreCase = true)) jsonUrl = url
            }
        }
        if (jsonUrl != null) {
            val parsed = runCatching { parseLatest(httpGetString(jsonUrl)) }.getOrNull()
            if (parsed != null) {
                return if (parsed.apkUrl == null && apkUrl != null) parsed.copy(apkUrl = apkUrl) else parsed
            }
        }
        val tag = root.optString("tag_name", "")
        val code = parseTagVersionCode(tag) ?: return null
        val body = root.optString("body", "")
        val sha = Regex("(?i)sha-?256[^0-9a-f]*([0-9a-f]{64})").find(body)?.groupValues?.get(1)
            ?: return null
        return LatestInfo(
            versionCode = code,
            versionName = root.optString("name", "").ifBlank { tag.removePrefix("v") },
            apkUrl = apkUrl ?: return null,
            sha256 = sha.lowercase(),
            notes = null,
        )
    }

    /** "v0.50" -> 50, "v0.50-x" -> 50, "v50" -> 50, "v1.02" -> 102. */
    internal fun parseTagVersionCode(tag: String): Int? {
        val m = Regex("^v?(\\d+)(?:\\.(\\d+))?").find(tag.trim()) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2]
        return if (minor.isEmpty()) major else major * 100 + (minor.toIntOrNull() ?: return null)
    }

    private fun httpGetString(urlSpec: String): String {
        val conn = openFollow(urlSpec)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} for latest.json")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val trimmed = text.trim().removePrefix("\uFEFF")
            if (!trimmed.startsWith("{")) {
                val lower = trimmed.lowercase()
                when {
                    "<html" in lower || "<!doctype" in lower ->
                        throw IllegalStateException("latest.json was HTML ")
                    else ->
                        throw IllegalStateException("latest.json was not JSON")
                }
            }
            return trimmed
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadToFile(
        urlSpec: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)?,
    ) {
        val conn = openFollow(urlSpec)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} downloading APK")
            }
            val ctype = conn.contentType.orEmpty()
            if (ctype.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("APK download returned HTML ")
            }
            val total = conn.contentLengthLong.coerceAtLeast(-1L)
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        output.write(buf, 0, read)
                        done += read
                        onProgress?.invoke(done, total)
                    }
                    output.flush()
                }
            }
            // Sanity: APK zip local header PK\u0003\u0004
            val header = dest.inputStream().use { s ->
                val b = ByteArray(4)
                val n = s.read(b)
                if (n < 4) ByteArray(0) else b
            }
            if (header.size < 4 || header[0] != 0x50.toByte() || header[1] != 0x4b.toByte()) {
                dest.delete()
                throw IllegalStateException("Downloaded file is not an APK")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openFollow(urlSpec: String, accept: String = "*/*"): HttpURLConnection {
        var current = urlSpec
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "POL-80-Updater/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", accept)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank() || ++redirects > 8) {
                    throw IllegalStateException("Too many redirects")
                }
                current = if (loc.startsWith("http")) loc else {
                    URL(URL(current), loc).toString()
                }
                continue
            }
            return conn
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } >= 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
