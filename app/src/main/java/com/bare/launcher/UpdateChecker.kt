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
 * Google Drive / HTTPS OTA updates for POL-80.
 *
 * Fetches latest.json from every mirror in [latestJsonCandidates] (Doc export, Drive
 * JSON, then immutable paste.rs fallbacks) and keeps the **maximum** versionCode.
 * First-success is unsafe: 0.39 stopped on paste.rs copies stuck at versionCode 38 and
 * reported up to date. When the winning remote versionCode > local, downloads the APK
 * (apkUrl or Drive apkDriveFileId), verifies sha256, and launches the package installer.
 *
 * Snooze: tapping Later stores the remote versionCode + 24h; prompts resume only when
 * a newer versionCode appears or the 24h window elapses.
 */
object UpdateChecker {

    data class LatestInfo(
        val versionCode: Int,
        val versionName: String,
        val apkDriveFileId: String?,
        val apkUrl: String?,
        val sha256: String,
        val notes: String?,
    ) {
        fun downloadUrl(): String? = downloadUrlCandidates().firstOrNull()

        /** Prefer direct https apkUrl, then Drive usercontent / uc mirrors. */
        fun downloadUrlCandidates(): List<String> {
            val out = linkedSetOf<String>()
            apkUrl?.trim()?.takeIf { it.startsWith("https://") }?.let { out += it }
            val id = apkDriveFileId?.trim().orEmpty()
            if (id.isNotEmpty()) {
                out += driveDownloadUrl(id)
                out += "https://drive.google.com/uc?export=download&id=$id&confirm=t"
            }
            return out.toList()
        }
    }

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

    fun latestJsonCandidates(): List<String> {
        val out = linkedSetOf<String>()
        BuildConfig.UPDATE_JSON_URL.trim().takeIf { it.startsWith("https://") }?.let { out += it }
        BuildConfig.UPDATE_JSON_DRIVE_FILE_ID.trim().takeIf { it.isNotEmpty() }?.let { id ->
            out += driveDownloadUrl(id)
            // Legacy uc? host (some networks resolve usercontent poorly).
            out += "https://drive.google.com/uc?export=download&id=$id&confirm=t"
        }
        BuildConfig.UPDATE_JSON_FALLBACK_URL.trim().takeIf { it.startsWith("https://") }?.let { out += it }
        // Doc export is repeated in case BuildConfig primary changes. paste.rs IDs are immutable
        // and several still serve versionCode 38; they are fallbacks only (max() ignores them
        // when a later or earlier mirror is higher).
        out += "https://docs.google.com/document/d/1Qn_N7-ZIB3yZzOSiNKzIU0AT3ZzTJxUb3PN2itGXPzg/export?format=txt"
        out += "https://paste.rs/9KGsl"
        out += "https://paste.rs/uPYdi"
        out += "https://paste.rs/RZvIm"
        out += "https://paste.rs/p2JaR"
        out += "https://paste.rs/iU5eQ"
        return out.toList()
    }

    fun driveDownloadUrl(fileId: String): String =
        "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"

    fun localVersionCode(): Int = BuildConfig.VERSION_CODE

    fun localVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * Highest versionCode across every reachable mirror.
     * A stuck immutable paste (e.g. version 38) must not hide a later Doc/Drive mirror at 41.
     * Failures are skipped; a lower-but-valid JSON is kept only until a higher one is seen.
     */
    suspend fun check(context: Context, manual: Boolean): CheckResult =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            var best: LatestInfo? = null
            for (url in latestJsonCandidates()) {
                try {
                    val parsed = fetchLatest(url) ?: continue
                    val current = best
                    if (current == null || parsed.versionCode > current.versionCode) {
                        best = parsed
                    }
                } catch (e: Exception) {
                    errors += "${e.message ?: "error"} @ ${url.take(64)}"
                }
            }
            val info = best
            if (info == null) {
                return@withContext CheckResult.Error(
                    errors.lastOrNull() ?: "Could not read latest.json",
                )
            }
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
        val urls = info.downloadUrlCandidates()
        if (urls.isEmpty()) {
            return@withContext InstallPrepResult.Failed("No apkUrl / apkDriveFileId")
        }
        val expected = info.sha256.trim().lowercase()
        if (expected.length != 64 || expected.any { it !in "0123456789abcdef" }) {
            return@withContext InstallPrepResult.Failed("Invalid sha256 in latest.json")
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "POL-80-${info.versionName}.apk")
        try {
            if (out.exists()) out.delete()
            var lastErr: Exception? = null
            var downloaded = false
            for (url in urls) {
                try {
                    if (out.exists()) out.delete()
                    downloadToFile(url, out, onProgress)
                    downloaded = true
                    break
                } catch (e: Exception) {
                    lastErr = e
                    out.delete()
                }
            }
            if (!downloaded) {
                return@withContext InstallPrepResult.Failed(
                    lastErr?.message ?: "APK download failed",
                )
            }
            val actual = sha256Hex(out)
            if (actual != expected) {
                out.delete()
                return@withContext InstallPrepResult.Failed(
                    "SHA-256 mismatch (got ${actual.take(12)}…)",
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settings = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                    return@withContext InstallPrepResult.NeedPermission(settings)
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
        val driveId = root.optString("apkDriveFileId", "")
            .ifBlank { root.optString("apk_drive_file_id", "") }
            .ifBlank { null }
        val apkUrl = root.optString("apkUrl", "")
            .ifBlank { root.optString("apk_url", "") }
            .ifBlank { root.optString("downloadUrl", "") }
            .ifBlank { null }
        val notes = root.optString("notes", "").ifBlank { null }
        return LatestInfo(
            versionCode = code,
            versionName = name,
            apkDriveFileId = driveId,
            apkUrl = apkUrl,
            sha256 = sha.trim().lowercase(),
            notes = notes,
        )
    }

    private suspend fun fetchLatest(url: String): LatestInfo? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val body = httpGetString(url)
            parseLatest(body)
        }

    private fun httpGetString(urlSpec: String): String {
        val conn = openFollow(urlSpec)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} for latest.json")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // Drive sometimes returns an HTML interstitial / login wall; JSON must start with {
            val trimmed = text.trim().removePrefix("\uFEFF")
            if (!trimmed.startsWith("{")) {
                val lower = trimmed.lowercase()
                when {
                    "accounts.google.com" in lower || "sign in" in lower ->
                        throw IllegalStateException(
                            "Drive latest.json needs sign-in — Share → Anyone with the link",
                        )
                    "<html" in lower || "<!doctype" in lower ->
                        throw IllegalStateException("latest.json was HTML (not public / wrong id)")
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
                throw IllegalStateException("APK download returned HTML (file not public?)")
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

    private fun openFollow(urlSpec: String): HttpURLConnection {
        var current = urlSpec
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "POL-80-Updater/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "*/*")
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
                // Login wall → not publicly shared
                if (current.contains("accounts.google.com")) {
                    throw IllegalStateException(
                        "Drive file requires sign-in — set sharing to Anyone with the link",
                    )
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
