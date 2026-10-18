package com.bare.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * v0.55: update path for the plain-View crash screen. No Compose, plain threads,
 * every entry point catches Throwable so a broken update path can never crash the rescue UI.
 * Results are delivered on the main thread.
 */
object RescueUpdater {

    sealed class Check {
        data class Newer(val info: UpdateChecker.LatestInfo) : Check()
        data object NoneNewer : Check()
        data class Failed(val message: String) : Check()
    }

    private val main = Handler(Looper.getMainLooper())

    private fun post(block: () -> Unit) {
        main.post {
            try {
                block()
            } catch (_: Throwable) {
            }
        }
    }

    /** GitHub releases/latest vs this build's versionCode. */
    fun checkAsync(onResult: (Check) -> Unit) {
        try {
            Thread({
                val result = try {
                    val info = UpdateChecker.fetchLatestBlocking()
                    if (info != null && info.versionCode > BuildConfig.VERSION_CODE &&
                        !info.apkUrl.isNullOrBlank()
                    ) {
                        Check.Newer(info)
                    } else {
                        Check.NoneNewer
                    }
                } catch (t: Throwable) {
                    Check.Failed(t.message ?: t.javaClass.simpleName)
                }
                post { onResult(result) }
            }, "pol80-rescue-check").start()
        } catch (t: Throwable) {
            post { onResult(Check.Failed(t.message ?: "check failed")) }
        }
    }

    fun canInstall(context: Context): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    } catch (_: Throwable) {
        false
    }

    /** "Install unknown apps" settings for this package. */
    fun openInstallSettings(activity: Activity): Boolean = try {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Download, verify sha256 and open the system installer. [onStatus] gets progress text;
     * [onDone] gets null on success (installer opened) or an error message.
     */
    fun downloadAndInstallAsync(
        activity: Activity,
        info: UpdateChecker.LatestInfo,
        onStatus: (String) -> Unit,
        onDone: (String?) -> Unit,
    ) {
        try {
            Thread({
                val err: String? = try {
                    var lastPct = -1
                    val prep = UpdateChecker.downloadAndVerifyBlocking(activity, info) { done, total ->
                        if (total > 0L) {
                            val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                post { onStatus("downloading $pct%") }
                            }
                        }
                    }
                    when (prep) {
                        is UpdateChecker.InstallPrepResult.Ready -> {
                            post {
                                if (UpdateChecker.launchInstaller(activity, prep.uri)) {
                                    onDone(null)
                                } else {
                                    onDone("could not open the installer")
                                }
                            }
                            return@Thread
                        }
                        is UpdateChecker.InstallPrepResult.NeedPermission ->
                            "allow installs for POL-80 first"
                        is UpdateChecker.InstallPrepResult.Failed -> prep.message
                    }
                } catch (t: Throwable) {
                    t.message ?: t.javaClass.simpleName
                }
                post { onDone(err) }
            }, "pol80-rescue-download").start()
        } catch (t: Throwable) {
            post { onDone(t.message ?: "download failed") }
        }
    }
}
