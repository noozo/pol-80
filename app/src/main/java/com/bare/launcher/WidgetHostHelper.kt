package com.bare.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Thin wrapper around [AppWidgetHost] for classic widget pick / bind / configure.
 * Host views yield horizontal swipes so [androidx.compose.foundation.pager.HorizontalPager]
 * can change pages (stock Pixel / Nova style direction lock).
 */
class WidgetHostHelper(
    private val activity: Activity,
    private val prefs: LauncherPrefs,
) {
    val appWidgetManager: AppWidgetManager =
        AppWidgetManager.getInstance(activity)

    val host: AppWidgetHost = object : AppWidgetHost(activity, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView {
            return PagerFriendlyAppWidgetHostView(context)
        }
    }

    fun startListening() {
        try {
            host.startListening()
        } catch (_: Exception) {
            // Host may already be listening or widgets may be stale
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
            // ignore
        }
        prefs.removeWidgetId(id)
    }

    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        return try {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
            val view = host.createView(context.applicationContext, appWidgetId, info)
            view.setAppWidget(appWidgetId, info)
            applyDefaultSize(view, info)
            view
        } catch (_: Exception) {
            // Stale widget id / provider uninstalled / RemoteViews inflate failure.
            null
        }
    }

    fun applyDefaultSize(view: AppWidgetHostView, info: AppWidgetProviderInfo) {
        val density = activity.resources.displayMetrics.density
        val minW = (info.minWidth.coerceAtLeast(110) * density).toInt()
        val minH = (info.minHeight.coerceAtLeast(40) * density).toInt()
        // Prefer a full-width-ish cell height for stack layout
        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = (minH * 1.2f).toInt().coerceIn(
            (80 * density).toInt(),
            (220 * density).toInt(),
        )
        view.layoutParams = FrameLayout.LayoutParams(width, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = listOf(
                SizeF(minW / density, minH / density),
                SizeF(360f, minH / density),
            )
            view.updateAppWidgetSize(Bundle(), sizes)
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(
                Bundle(),
                (minW / density).toInt(),
                (minH / density).toInt(),
                360,
                (minH / density).toInt() + 40,
            )
        }
    }

    /** Intent for the system widget picker (fallback). */
    fun pickIntent(appWidgetId: Int): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

    /** Intent to request bind permission when [bindAppWidgetIdIfAllowed] fails. */
    fun bindPermissionIntent(appWidgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }

    fun configureIntent(info: AppWidgetProviderInfo, appWidgetId: Int): Intent? {
        if (info.configure == null) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean =
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)

    data class WidgetCandidate(
        val info: AppWidgetProviderInfo,
        val label: String,
        val appLabel: String,
        val packageName: String,
        val preview: Drawable?,
        val icon: Drawable?,
    )

    /** Installed providers with preview/icon, grouped later by [WidgetCandidate.appLabel]. */
    fun listWidgetCandidates(): List<WidgetCandidate> {
        val pm = activity.packageManager
        val providers = appWidgetManager.installedProviders
        return providers.mapNotNull { info ->
            val packageName = info.provider.packageName
            val label = try {
                info.loadLabel(pm)?.toString()?.trim().orEmpty()
            } catch (_: Exception) {
                info.provider.className.substringAfterLast('.')
            }
            if (label.isEmpty()) return@mapNotNull null
            val appLabel = try {
                val ai = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(ai)?.toString()?.trim().orEmpty()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }.ifEmpty { packageName }
            val preview = loadPreviewImage(info, pm)
            val icon = try {
                info.loadIcon(activity, activity.resources.displayMetrics.densityDpi)
            } catch (_: Exception) {
                try {
                    pm.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    null
                }
            }
            WidgetCandidate(
                info = info,
                label = label,
                appLabel = appLabel,
                packageName = packageName,
                preview = preview,
                icon = icon,
            )
        }.sortedWith(
            compareBy(
                { it.appLabel.lowercase() },
                { it.label.lowercase() },
            ),
        )
    }

    private fun loadPreviewImage(info: AppWidgetProviderInfo, pm: PackageManager): Drawable? {
        // API 31+: loadPreviewImage; else previewImage resource / icon fallback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val d = info.loadPreviewImage(activity, activity.resources.displayMetrics.densityDpi)
                if (d != null) return d
            } catch (_: Exception) {
                // fall through
            }
        }
        if (info.previewImage != 0) {
            try {
                val res = pm.getResourcesForApplication(info.provider.packageName)
                @Suppress("DEPRECATION")
                val d = res.getDrawable(info.previewImage, null)
                if (d != null) return d
            } catch (_: Exception) {
                // fall through
            }
        }
        return try {
            info.loadIcon(activity, activity.resources.displayMetrics.densityDpi)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val HOST_ID = 0xBA2E
        const val REQ_PICK = 1001
        const val REQ_BIND = 1002
        const val REQ_CONFIGURE = 1003
    }
}

/**
 * Yields clearly-horizontal moves to the Compose [HorizontalPager] (and Android
 * parents) instead of letting the RemoteViews consume the whole gesture — same
 * idea as Pixel Launcher / Lawnchair workspace intercept.
 */
class PagerFriendlyAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var downX = 0f
    private var downY = 0f
    private var lockedHorizontal = false
    private var lockedVertical = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                lockedHorizontal = false
                lockedVertical = false
                // Allow parents (Compose pager) a chance to intercept later.
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                if (lockedHorizontal || lockedVertical) {
                    return lockedHorizontal
                }
                val dx = abs(ev.rawX - downX)
                val dy = abs(ev.rawY - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    if (dx > dy * 1.15f) {
                        lockedHorizontal = true
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    } else if (dy > dx * 1.15f) {
                        lockedVertical = true
                        // Widget / nested vertical scroll owns the gesture.
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lockedHorizontal = false
                lockedVertical = false
            }
        }
        return lockedHorizontal
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Once locked horizontal, do not consume — let the event fall through to
        // the AndroidComposeView / pager nested-scroll path on subsequent moves.
        if (lockedHorizontal) {
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                lockedHorizontal = false
                lockedVertical = false
            }
            return false
        }
        return super.onTouchEvent(ev)
    }
}
