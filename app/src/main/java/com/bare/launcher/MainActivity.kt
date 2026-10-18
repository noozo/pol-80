package com.bare.launcher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.drawBehind
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
) {
    val key: String get() = appKey(packageName, activityName)
}

enum class SheetMode {
    None,
    AppDrawer,
    PinPicker,
    DockPicker,
    WidgetPicker,
}

enum class LayoutLockDialogMode {
    Setup,
    Unlock,
    Manage,
    DisableConfirm,
}

/** Block home swipe-down from reopening search after a dismiss (same gesture). */
private const val SEARCH_REOPEN_COOLDOWN_MS = 300L

/** Don't re-flash CRT boot splash if Home is spammed — see CrtBootEffect. */
// BOOT_SPLASH_THROTTLE_MS / POWER / HOLD / FADE live in CrtBootEffect.kt

/** Visual row built from flat HomeItem indices. */
private sealed class VisualRow {
    data class Pins(val entries: List<Pair<Int, HomeItem.Pin>>) : VisualRow()
    data class Widget(val index: Int, val item: HomeItem.Widget) : VisualRow()
}

private fun buildVisualRows(items: List<HomeItem>): List<VisualRow> {
    val rows = mutableListOf<VisualRow>()
    var pinBuf = mutableListOf<Pair<Int, HomeItem.Pin>>()
    fun flushPins() {
        if (pinBuf.isNotEmpty()) {
            rows.add(VisualRow.Pins(pinBuf.toList()))
            pinBuf = mutableListOf()
        }
    }
    items.forEachIndexed { index, item ->
        when (item) {
            is HomeItem.Widget -> {
                flushPins()
                rows.add(VisualRow.Widget(index, item))
            }
            is HomeItem.Pin -> {
                pinBuf.add(index to item)
                if (pinBuf.size == 4) flushPins()
            }
        }
    }
    flushPins()
    return rows
}

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var widgetHelper: WidgetHostHelper

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetVersionState = mutableIntStateOf(0)
    private var currentPageState = mutableIntStateOf(0)
    private var statusTickState = mutableIntStateOf(0)
    private lateinit var flashlight: FlashlightController
    private var flashlightOnState = mutableStateOf(false)
    private lateinit var layoutLock: LayoutLockStore
    private var layoutUnlockedSession = false
    private var shakeDetector: ShakeDetector? = null
    private var shakeFlashlightEnabledState = mutableStateOf(false)
    private var portraitLockEnabledState = mutableStateOf(true)
    /** Nightstand edge clock. Sensor-driven; does not change requested orientation. */
    private var edgeClockVisibleState = mutableStateOf(false)
    private var edgeClockSideState = mutableStateOf(EdgeSide.None)
    private var edgeClockSensor: EdgeClockSensor? = null
    /** Non-zero elapsedRealtime id of the latest unplug power-down sting. */
    private var unplugStingRequestIdState = mutableStateOf(0L)

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val on = flashlight.toggle()
                flashlightOnState.value = on == true
            } else {
                Toast.makeText(this, "Camera permission needed for flashlight", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            statusTickState.intValue++
            contactsTickState.intValue++
        }

    private var contactsTickState = mutableIntStateOf(0)

    /** Hoisted so system Home (onNewIntent MAIN+HOME) can reset all overlays. */
    private var searchOpenState = mutableStateOf(false)
    private var sheetModeState = mutableStateOf(SheetMode.None)
    private var statusDrawerExpandedState = mutableStateOf(false)
    private var editModeState = mutableStateOf(false)
    /** Bumped on Home reset so Compose clears menus / transient dialogs. */
    private var homeResetEpochState = mutableIntStateOf(0)
    /** Non-zero elapsedRealtime id of the latest boot-splash request. */
    private var bootSplashRequestIdState = mutableStateOf(0L)
    private var lastBootSplashAtMs = 0L

    /** Phosphor "docked" stamp. Warp replays only on the not-docked → docked edge. */
    private var chargeDockedState = mutableStateOf(false)
    /** null until the first successful battery read — unknown is not a plug-in. */
    private var chargeWasDocked: Boolean? = null
    private var chargeReceiverRegistered = false
    private val chargeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onChargeBroadcast()
        }
    }

    private fun primeChargeRitual() {
        val docked = try {
            ChargeRitual.isDocked(this)
        } catch (_: Exception) {
            null
        }
        // Already charging at process start: stamp only, no extra warp.
        chargeWasDocked = docked
        chargeDockedState.value = docked == true
    }

    private fun onChargeBroadcast() {
        try {
            val docked = ChargeRitual.isDocked(this)
            if (ChargeRitual.shouldReplayWarp(chargeWasDocked, docked)) {
                // Short home warp (~0.5s). force so a recent Home press doesn't eat the plug-in.
                maybeShowBootSplash(force = true)
            } else if (ChargeRitual.shouldPlayUnplugSting(chargeWasDocked, docked)) {
                // Reverse of the plug-in warp: ~200ms power-down. Not on battery ticks.
                maybePlayUnplugSting()
            }
            chargeWasDocked = docked
            chargeDockedState.value = docked
        } catch (_: Exception) {
            // BatteryManager / sticky read failed — leave the stamp as it was.
        }
    }

    private fun registerChargeRitual() {
        if (chargeReceiverRegistered) return
        chargeReceiverRegistered = ChargeRitual.register(this, chargeReceiver)
    }

    private fun unregisterChargeRitual() {
        if (!chargeReceiverRegistered) return
        ChargeRitual.unregister(this, chargeReceiver)
        chargeReceiverRegistered = false
    }

    private fun maybeShowBootSplash(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastBootSplashAtMs < BOOT_SPLASH_THROTTLE_MS) return
        lastBootSplashAtMs = now
        bootSplashRequestIdState.value = now
    }

    private fun maybePlayUnplugSting() {
        unplugStingRequestIdState.value = SystemClock.elapsedRealtime()
    }

    /**
     * Clean home: close search, apps/pickers sheets, status drawer, edit mode,
     * and bump epoch so Compose drops menus. Does not touch wallpaper/pins.
     */
    private fun resetToHomeState(requestSplash: Boolean) {
        searchOpenState.value = false
        sheetModeState.value = SheetMode.None
        statusDrawerExpandedState.value = false
        editModeState.value = false
        layoutUnlockedSession = false
        homeResetEpochState.intValue++
        if (requestSplash) maybeShowBootSplash()
    }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            contactsTickState.intValue++
        }

    private val pickWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (result.resultCode != RESULT_OK) {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgetHelper.deleteId(id)
                }
                return@registerForActivityResult
            }
            val widgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                id,
            ) ?: id
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult

            @Suppress("DEPRECATION")
            val providerExtra: ComponentName? =
                result.data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)

            val info = widgetHelper.appWidgetManager.getAppWidgetInfo(widgetId)
            val provider = info?.provider ?: providerExtra
            if (provider == null) {
                widgetHelper.deleteId(widgetId)
                return@registerForActivityResult
            }
            maybeConfigureOrFinish(widgetId, provider)
        }

    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (result.resultCode != RESULT_OK) {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgetHelper.deleteId(id)
                }
                return@registerForActivityResult
            }
            val info = widgetHelper.appWidgetManager.getAppWidgetInfo(id)
            if (info == null) {
                widgetHelper.deleteId(id)
                return@registerForActivityResult
            }
            maybeConfigureOrFinish(id, info.provider)
        }

    private val configureWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (result.resultCode != RESULT_OK) {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgetHelper.deleteId(id)
                }
                return@registerForActivityResult
            }
            finishAddWidget(id)
        }

    /** v0.54: false while the plain-View crash screen is up; lifecycle hooks skip work. */
    private var launcherStarted = false
    private var startupDonePosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity-level chain (no-op if BareApp already installed). Does not
        // replace the system handler — CrashBeacon keeps the previous one.
        CrashBeacon.install(this)
        // v0.54: last start crashed → show the report (plain Views) before any launcher code.
        val report = CrashBeacon.pendingStartupReport(this)
        if (report != null) {
            CrashScreen.show(this, report) {
                // Activity is already started/resumed: run the launcher's own start/resume work.
                startLauncher()
                launcherOnStart()
                launcherOnResume()
            }
            return
        }
        startLauncher()
    }

    private fun startLauncher() {
        if (launcherStarted) return
        launcherStarted = true
        CrashBeacon.startupPhase = true
        prefs = LauncherPrefs(this)
        widgetHelper = WidgetHostHelper(this, prefs)
        flashlight = FlashlightController(this)
        layoutLock = LayoutLockStore(this)
        shakeFlashlightEnabledState.value = prefs.isShakeFlashlightEnabled()
        shakeDetector = ShakeDetector(this) {
            runOnUiThread { toggleFlashlightFromShake() }
        }
        portraitLockEnabledState.value = prefs.isPortraitLockEnabled()
        applyPortraitLock(portraitLockEnabledState.value)

        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Cold-start CRT power-on line.
        maybeShowBootSplash(force = true)
        // If already plugged in, show "docked" without a second warp.
        primeChargeRitual()
        registerChargeRitual()
        // Tilt clock uses gravity. Portrait lock stays whatever the user set.
        edgeClockSensor = EdgeClockSensor(this) { visible, side ->
            runOnUiThread {
                edgeClockVisibleState.value = visible
                edgeClockSideState.value = side
            }
        }

        setContent {
            BareTheme {
                BareLauncher(
                    prefs = prefs,
                    widgetHelper = widgetHelper,
                    widgetVersion = widgetVersionState.intValue,
                    statusTick = statusTickState.intValue,
                    contactsTick = contactsTickState.intValue,
                    searchOpen = searchOpenState.value,
                    onSearchOpenChange = { open -> searchOpenState.value = open },
                    sheetMode = sheetModeState.value,
                    onSheetModeChange = { mode -> sheetModeState.value = mode },
                    statusDrawerExpanded = statusDrawerExpandedState.value,
                    onStatusDrawerExpandedChange = { open -> statusDrawerExpandedState.value = open },
                    editMode = editModeState.value,
                    onEditModeChange = { on -> editModeState.value = on },
                    homeResetEpoch = homeResetEpochState.intValue,
                    bootSplashRequestId = bootSplashRequestIdState.value,
                    unplugStingRequestId = unplugStingRequestIdState.value,
                    edgeClockVisible = edgeClockVisibleState.value,
                    edgeClockSide = edgeClockSideState.value,
                    chargeDocked = chargeDockedState.value,
                    loadApps = { queryLaunchableApps() },
                    onLaunch = { app -> launchApp(app) },
                    onBindWidgetProvider = { provider -> bindSelectedWidget(provider) },
                    onOpenSystemWidgetPicker = { startAddWidget() },
                    listWidgetCandidates = { widgetHelper.listWidgetCandidates() },
                    onRemoveWidget = { id ->
                        widgetHelper.deleteId(id)
                        widgetVersionState.intValue++
                    },
                    onPageChanged = { page -> currentPageState.intValue = page },
                    onRequestStatusPermissions = { requestStatusPermissions() },
                    onOpenNotificationAccess = { openNotificationAccessSettings() },
                    onRequestCalendarPermission = { requestCalendarPermission() },
                    onRequestLocationPermission = { requestLocationForWifiSsid() },
                    onRequestContactsPermission = { requestContactsPermission() },
                    onOpenStatusTarget = { target -> StatusApps.open(this, target) },
                    flashlightOn = flashlightOnState.value,
                    onToggleFlashlight = { toggleFlashlight() },
                    shakeFlashlightEnabled = shakeFlashlightEnabledState.value,
                    onShakeFlashlightEnabledChange = { enabled ->
                        prefs.setShakeFlashlightEnabled(enabled)
                        shakeFlashlightEnabledState.value = enabled
                        syncShakeDetector()
                    },
                    portraitLockEnabled = portraitLockEnabledState.value,
                    onPortraitLockEnabledChange = { enabled ->
                        prefs.setPortraitLockEnabled(enabled)
                        portraitLockEnabledState.value = enabled
                        applyPortraitLock(enabled)
                    },
                    layoutLock = layoutLock,
                    layoutUnlockedSession = { layoutUnlockedSession },
                    onLayoutSessionUnlocked = { layoutUnlockedSession = true },
                    onLayoutSessionLocked = { layoutUnlockedSession = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!launcherStarted) return
        launcherOnStart()
    }

    private fun launcherOnStart() {
        widgetHelper.startListening()
        statusTickState.intValue++
    }

    /**
     * Launcher singleTask: system Home (gesture swipe-up-from-bottom / Home)
     * re-delivers ACTION_MAIN + CATEGORY_HOME via onNewIntent while we stay
     * resumed. Reset every overlay (search, apps drawer / pickers, status
     * drawer, edit mode) and optionally flash the CRT boot line (throttled).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!launcherStarted) return
        if (isSystemHomeIntent(intent)) {
            resetToHomeState(requestSplash = true)
        }
    }

    private fun isSystemHomeIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MAIN) return false
        return intent.hasCategory(Intent.CATEGORY_HOME)
    }

    override fun onStop() {
        if (launcherStarted) widgetHelper.stopListening()
        super.onStop()
    }

    private fun requestStatusPermissions() {
        val needed = buildList {
            if (!StatusMonitor.hasCallLogPermission(this@MainActivity)) {
                add(Manifest.permission.READ_CALL_LOG)
            }
            if (!StatusMonitor.hasSmsPermission(this@MainActivity)) {
                add(Manifest.permission.READ_SMS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requestCalendarPermission() {
        if (StatusMonitor.hasCalendarPermission(this)) {
            statusTickState.intValue++
            return
        }
        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
    }

    private fun requestLocationForWifiSsid() {
        if (StatusMonitor.hasLocationPermission(this)) {
            statusTickState.intValue++
            return
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    private fun requestContactsPermission() {
        if (ContactsRepo.hasPermission(this)) {
            contactsTickState.intValue++
            return
        }
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    },
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Open Settings → Notification access", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startAddWidget() {
        val id = widgetHelper.allocateId()
        pendingWidgetId = id
        try {
            pickWidgetLauncher.launch(widgetHelper.pickIntent(id))
        } catch (_: Exception) {
            widgetHelper.deleteId(id)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            Toast.makeText(this, "Widget picker unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    /** Custom preview picker → bind provider on current page. */
    private fun bindSelectedWidget(provider: ComponentName) {
        val id = widgetHelper.allocateId()
        pendingWidgetId = id
        if (widgetHelper.bindIfAllowed(id, provider)) {
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            maybeConfigureOrFinish(id, provider)
            return
        }
        try {
            bindWidgetLauncher.launch(widgetHelper.bindPermissionIntent(id, provider))
        } catch (_: Exception) {
            widgetHelper.deleteId(id)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            Toast.makeText(this, "Cannot bind widget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun maybeConfigureOrFinish(widgetId: Int, provider: ComponentName) {
        val info = widgetHelper.appWidgetManager.getAppWidgetInfo(widgetId)
        if (info == null) {
            if (!widgetHelper.bindIfAllowed(widgetId, provider)) {
                pendingWidgetId = widgetId
                try {
                    bindWidgetLauncher.launch(
                        widgetHelper.bindPermissionIntent(widgetId, provider),
                    )
                } catch (_: Exception) {
                    widgetHelper.deleteId(widgetId)
                    Toast.makeText(this, "Cannot bind widget", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        val boundInfo = widgetHelper.appWidgetManager.getAppWidgetInfo(widgetId)
        if (boundInfo?.configure != null) {
            val cfg = widgetHelper.configureIntent(boundInfo, widgetId)
            if (cfg != null) {
                pendingWidgetId = widgetId
                try {
                    configureWidgetLauncher.launch(cfg)
                    return
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }
        finishAddWidget(widgetId)
    }

    private fun finishAddWidget(widgetId: Int) {
        prefs.addWidgetToPage(widgetId, currentPageState.intValue)
        widgetVersionState.intValue++
    }

    private fun toggleFlashlight() {
        if (!flashlight.hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (!flashlight.hasFlashUnit()) {
            Toast.makeText(this, "No flashlight on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val on = flashlight.toggle()
        flashlightOnState.value = on == true
    }

    /** Same torch path as dock icon — shared FlashlightController, no separate state fight. */
    private fun toggleFlashlightFromShake() {
        if (!shakeFlashlightEnabledState.value) return
        if (!flashlight.hasCameraPermission()) {
            // Do not pop permission from a shake; wait for explicit dock tap.
            return
        }
        if (!flashlight.hasFlashUnit()) return
        val on = flashlight.toggle()
        flashlightOnState.value = on == true
    }

    private fun syncShakeDetector() {
        val det = shakeDetector ?: return
        if (shakeFlashlightEnabledState.value) det.start() else det.stop()
    }

    /** Portrait-only when locked (default); UNSPECIFIED when unlocked to allow landscape. */
    private fun applyPortraitLock(locked: Boolean) {
        try {
            requestedOrientation = if (locked) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } catch (_: Exception) {
            // Multi-window / OEM can reject orientation requests — keep current.
        }
    }


    override fun onResume() {
        super.onResume()
        if (!launcherStarted) return
        launcherOnResume()
    }

    private fun launcherOnResume() {
        if (!startupDonePosted) {
            startupDonePosted = true
            window.decorView.postDelayed({ CrashBeacon.startupPhase = false }, 3_000L)
        }
        statusTickState.intValue++
        syncShakeDetector()
        edgeClockSensor?.start()
    }

    override fun onPause() {
        // Keep torch state; do not force-off on brief pauses (user wants dock toggle).
        shakeDetector?.stop()
        edgeClockSensor?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (!launcherStarted) {
            super.onDestroy()
            return
        }
        unregisterChargeRitual()
        edgeClockSensor?.stop()
        edgeClockSensor = null
        shakeDetector?.stop()
        shakeDetector = null
        if (::flashlight.isInitialized) {
            flashlight.turnOff()
            flashlightOnState.value = false
        }
        super.onDestroy()
    }

    private fun queryLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        return resolved
            .mapNotNull { info ->
                try {
                    val ai = info.activityInfo ?: return@mapNotNull null
                    val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) return@mapNotNull null
                    val icon = try {
                        info.loadIcon(packageManager)
                    } catch (_: Exception) {
                        packageManager.defaultActivityIcon
                    }
                    LaunchableApp(
                        label = label,
                        packageName = ai.packageName,
                        activityName = ai.name,
                        icon = icon,
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launchApp(app: LaunchableApp) {
        val launch = try {
            packageManager.getLaunchIntentForPackage(app.packageName)
        } catch (_: Exception) {
            null
        } ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // resolveActivity can return null if package was uninstalled mid-session.
            if (launch.resolveActivity(packageManager) == null &&
                launch.component == null
            ) {
                Toast.makeText(this, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
                return
            }
            startActivity(launch)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }
}

/* ---------- theme aliases (Style D Miami dusk via BareColors) ---------- */

private val BareText = BareColors.TextPrimary
private val BareMuted = BareColors.TextMuted
private val TechHeaderText = BareColors.TextPrimary
private val Scrim = BareColors.GlassSoft
private val ScrimStrong = BareColors.Surface
private val SheetBg = BareColors.Surface
private val Placeholder = BareColors.Placeholder
private val TechGreen = BareColors.SysAccent
private val StatusAmber = BareColors.StatusAmber
private val StatusRed = BareColors.StatusRed
private val StatusMutedValue = BareColors.TextPrimary

/* ---------- root UI ---------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BareLauncher(
    prefs: LauncherPrefs,
    widgetHelper: WidgetHostHelper,
    widgetVersion: Int,
    statusTick: Int,
    contactsTick: Int,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    sheetMode: SheetMode,
    onSheetModeChange: (SheetMode) -> Unit,
    statusDrawerExpanded: Boolean,
    onStatusDrawerExpandedChange: (Boolean) -> Unit,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    homeResetEpoch: Int,
    bootSplashRequestId: Long,
    unplugStingRequestId: Long,
    edgeClockVisible: Boolean,
    edgeClockSide: EdgeSide,
    chargeDocked: Boolean,
    loadApps: () -> List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onBindWidgetProvider: (ComponentName) -> Unit,
    onOpenSystemWidgetPicker: () -> Unit,
    listWidgetCandidates: () -> List<WidgetHostHelper.WidgetCandidate>,
    onRemoveWidget: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onRequestStatusPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenStatusTarget: (StatusApps.Target) -> Unit,
    flashlightOn: Boolean,
    onToggleFlashlight: () -> Unit,
    shakeFlashlightEnabled: Boolean,
    onShakeFlashlightEnabledChange: (Boolean) -> Unit,
    portraitLockEnabled: Boolean,
    onPortraitLockEnabledChange: (Boolean) -> Unit,
    layoutLock: LayoutLockStore,
    layoutUnlockedSession: () -> Boolean,
    onLayoutSessionUnlocked: () -> Unit,
    onLayoutSessionLocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var layout by remember { mutableStateOf(prefs.getLayout()) }
    var dockSlots by remember { mutableStateOf(prefs.getDockSlots()) }
    var now by remember { mutableStateOf(Date()) }
    var dockEditIndex by remember { mutableStateOf(-1) }
    var pageDragging by remember { mutableStateOf(false) }
    var techStatus by remember { mutableStateOf(StatusMonitor.snapshot(context)) }
    var callRadar by remember { mutableStateOf(StatusMonitor.activeRadar(context)) }
    var faultBeacon by remember { mutableStateOf(CrashBeacon.peek(context)) }
    var homeStamps by remember { mutableStateOf(HomeStampStore.load(context)) }
    var stampComposer by remember { mutableStateOf<StampComposerState?>(null) }
    val stampGestureLock = remember { StampGestureLock() }
    var rainDrizzleActive by remember { mutableStateOf(false) }
    var askedStatusPerms by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf<NowPlayingInfo?>(null) }
    val nowPlayingMonitor = remember {
        NowPlayingMonitor(context) { info -> nowPlaying = info }
    }
    // Overlays (search / sheet / status / edit) are hoisted on MainActivity so
    // onNewIntent(HOME) can reset via resetToHomeState(). After search closes,
    // ignore home swipe-down reopen for a short cooldown.
    var searchClosedAtMs by remember { mutableStateOf(0L) }
    var weather by remember { mutableStateOf(WeatherRepo.cached(context)) }
    var phosphorMode by remember { mutableStateOf(prefs.getPhosphorMode()) }
    val phosphorActive = remember(phosphorMode, now.time) {
        PhosphorNight.isActive(context, phosphorMode, now.time)
    }
    var digestTarget by remember { mutableStateOf<StatusApps.Target?>(null) }
    var layoutLockDialog by remember { mutableStateOf<LayoutLockDialogMode?>(null) }
    var layoutLockPin by remember { mutableStateOf("") }
    var layoutLockPinConfirm by remember { mutableStateOf("") }
    var layoutLockError by remember { mutableStateOf<String?>(null) }
    var layoutLockEnabledUi by remember { mutableStateOf(layoutLock.isEnabled()) }
    var updateMenuExpanded by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<UpdateChecker.LatestInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var pendingInstallUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun closeSearch() {
        if (!searchOpen) return
        onSearchOpenChange(false)
        searchClosedAtMs = SystemClock.elapsedRealtime()
    }

    fun openSearch() {
        onSearchOpenChange(true)
    }

    // System Home reset: drop menus / dialogs and arm search reopen cooldown.
    LaunchedEffect(homeResetEpoch) {
        if (homeResetEpoch <= 0) return@LaunchedEffect
        updateMenuExpanded = false
        digestTarget = null
        layoutLockDialog = null
        layoutLockPin = ""
        layoutLockPinConfirm = ""
        layoutLockError = null
        if (!updateBusy) {
            updateDialogInfo = null
        }
        stampComposer = null
        searchClosedAtMs = SystemClock.elapsedRealtime()
    }

    val scope = rememberCoroutineScope()

    fun runUpdateCheck(manual: Boolean) {
        scope.launch {
            if (manual) {
                updateBusy = true
                updateStatusText = "Checking…"
            }
            when (val result = UpdateChecker.check(context, manual = manual)) {
                is UpdateChecker.CheckResult.Available -> {
                    updateDialogInfo = result.info
                    updateStatusText = null
                }
                is UpdateChecker.CheckResult.UpToDate -> {
                    if (manual) {
                        Toast.makeText(context, "POL-80 is up to date", Toast.LENGTH_SHORT).show()
                    }
                    updateStatusText = null
                }
                is UpdateChecker.CheckResult.Snoozed -> {
                    updateStatusText = null
                }
                is UpdateChecker.CheckResult.Error -> {
                    if (manual) {
                        Toast.makeText(
                            context,
                            "Update check failed: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    updateStatusText = null
                }
            }
            if (manual) updateBusy = false
        }
    }

    fun startUpdateDownload(info: UpdateChecker.LatestInfo) {
        scope.launch {
            updateBusy = true
            updateStatusText = "Downloading…"
            when (val prep = UpdateChecker.downloadAndVerify(context, info) { done, total ->
                if (total > 0L) {
                    val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                    updateStatusText = "Downloading $pct%"
                }
            }) {
                is UpdateChecker.InstallPrepResult.Ready -> {
                    updateStatusText = null
                    updateDialogInfo = null
                    UpdateChecker.clearSnooze(context)
                    val act = activity
                    if (act != null) {
                        if (!UpdateChecker.launchInstaller(act, prep.uri)) {
                            pendingInstallUri = prep.uri
                            Toast.makeText(
                                context,
                                "Could not open installer — try again from ⋮",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        pendingInstallUri = prep.uri
                        Toast.makeText(context, "Ready to install — reopen POL-80", Toast.LENGTH_LONG).show()
                    }
                }
                is UpdateChecker.InstallPrepResult.NeedPermission -> {
                    updateStatusText = null
                    pendingInstallUri = null
                    Toast.makeText(
                        context,
                        "Allow install unknown apps, then tap Update again",
                        Toast.LENGTH_LONG,
                    ).show()
                    try {
                        context.startActivity(prep.settingsIntent)
                    } catch (_: Exception) {
                    }
                }
                is UpdateChecker.InstallPrepResult.Failed -> {
                    updateStatusText = null
                    Toast.makeText(context, "Update failed: ${prep.message}", Toast.LENGTH_LONG).show()
                }
            }
            updateBusy = false
        }
    }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { layout.pages.size },
    )

    LaunchedEffect(Unit) {
        apps = loadApps()
        var tick = 0
        while (true) {
            now = Date()
            callRadar = StatusMonitor.activeRadar(context, now.time)
            if (tick % 15 == 0) {
                faultBeacon = CrashBeacon.peek(context, now.time)
            }
            delay(1_000L)
            tick++
            // Backup CALL/SMS poll every ~3s while home is composed/visible.
            if (tick % 3 == 0) {
                techStatus = StatusMonitor.snapshot(context)
            }
            if (tick % 30 == 0) {
                apps = loadApps()
            }
        }
    }

    // Force-refresh when Bare resumes / comes to foreground (statusTick bumped
    // from onStart/onResume). Also used after permission grants.
    LaunchedEffect(statusTick) {
        techStatus = StatusMonitor.snapshot(context)
        faultBeacon = CrashBeacon.peek(context)
        // v0.54: auto-ask Call log / SMS once per install; after that only from the status-line tap.
        if (!askedStatusPerms) {
            askedStatusPerms = true
            if (!prefs.wasStatusPermsAutoAsked()) {
                prefs.setStatusPermsAutoAsked()
                onRequestStatusPermissions()
            }
        }
    }

    LaunchedEffect(statusTick) {
        weather = WeatherRepo.getOrFetch(context)
    }

    // Rain drizzle: after unlock / home resume (statusTick), show thin phosphor
    // overlay ~30s when Open-Meteo says rain. Debounced a few minutes; fetch miss → skip.
    LaunchedEffect(statusTick) {
        if (statusTick <= 0) return@LaunchedEffect
        if (rainDrizzleActive) return@LaunchedEffect
        val start = maybeStartRainDrizzle(context, forceWeatherRefresh = false)
        if (start) rainDrizzleActive = true
    }

    // Auto-check for updates ~every 15 minutes while launcher UI is composed/resumed.
    LaunchedEffect(Unit) {
        runUpdateCheck(manual = false)
        while (true) {
            delay(UpdateChecker.AUTO_CHECK_INTERVAL_MS)
            runUpdateCheck(manual = false)
        }
    }

    DisposableEffect(Unit) {
        nowPlayingMonitor.start()
        onDispose { nowPlayingMonitor.stop() }
    }


    // ContentObserver: SMS mark-as-read / CallLog changes update ASAP.
    // Re-subscribe when permissions flip (statusTick after grant).
    val canReadSms = StatusMonitor.hasSmsPermission(context)
    val canReadCalls = StatusMonitor.hasCallLogPermission(context)
    DisposableEffect(statusTick, canReadSms, canReadCalls) {
        val unregister = StatusMonitor.registerCallSmsObservers(context) {
            techStatus = StatusMonitor.snapshot(context)
            callRadar = StatusMonitor.activeRadar(context)
        }
        onDispose { unregister() }
    }

    // RF signal-weather: refresh on default-route / capability changes.
    DisposableEffect(Unit) {
        val unregister = SignalWeather.registerNetworkCallback(context) {
            techStatus = StatusMonitor.snapshot(context)
        }
        onDispose { unregister() }
    }

    LaunchedEffect(widgetVersion) {
        layout = prefs.getLayout()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(page) }
    }

    LaunchedEffect(editMode) {
        if (!editMode) {
            onLayoutSessionLocked()
            layout = prefs.trimTrailingEmptyPages()
            if (pagerState.currentPage >= layout.pages.size) {
                scope.launch {
                    pagerState.scrollToPage((layout.pages.size - 1).coerceAtLeast(0))
                }
            }
        }
    }

    fun canEnterEditUnlocked(): Boolean {
        if (!layoutLock.isEnabled()) return true
        return layoutUnlockedSession()
    }

    fun requestEnterEdit() {
        if (editMode) {
            onEditModeChange(false)
            return
        }
        if (canEnterEditUnlocked()) {
            onEditModeChange(true)
            return
        }
        layoutLockPin = ""
        layoutLockPinConfirm = ""
        layoutLockError = null
        layoutLockDialog = LayoutLockDialogMode.Unlock
    }

    fun promptBiometricUnlock() {
        val act = activity ?: return
        val bm = BiometricManager.from(context)
        val can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            layoutLockError = "Biometric unavailable"
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            act,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onLayoutSessionUnlocked()
                    layoutLockDialog = null
                    layoutLockPin = ""
                    layoutLockError = null
                    onEditModeChange(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        layoutLockError = errString.toString()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock layout")
            .setSubtitle("Authenticate to edit home layout")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        runCatching { prompt.authenticate(info) }
            .onFailure { layoutLockError = "Biometric failed to start" }
    }

    val appByKey = remember(apps) { apps.associateBy { it.key } }
    val pinnedKeys = remember(layout) { prefs.getAllPinnedKeys() }
    val dockApps = remember(dockSlots, apps) {
        dockSlots.map { key -> if (key.isBlank()) null else appByKey[key] }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val edgeHmFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val edgeSecFmt = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE dd MMM yyyy", Locale.US) }

    fun reloadLayout() {
        layout = prefs.getLayout()
    }

    BackHandler(enabled = searchOpen || sheetMode != SheetMode.None || editMode || statusDrawerExpanded || digestTarget != null || layoutLockDialog != null) {
        when {
            stampComposer != null -> stampComposer = null
            layoutLockDialog != null -> {
                layoutLockDialog = null
                layoutLockPin = ""
                layoutLockPinConfirm = ""
                layoutLockError = null
            }
            digestTarget != null -> digestTarget = null
            searchOpen -> closeSearch()
            sheetMode != SheetMode.None -> onSheetModeChange(SheetMode.None)
            statusDrawerExpanded -> onStatusDrawerExpandedChange(false)
            editMode -> onEditModeChange(false)
        }
    }

    val crtBoot = rememberCrtBoot(bootSplashRequestId)
    val crtUnplug = rememberCrtPowerDown(unplugStingRequestId)
    // Overlay-only sting (0.46+). Never route unplug through crtHomeWarp — that left
    // AGSL/collapse stuck as a mid-screen rainbow band on 0.45.
    val unplugActive = crtUnplug.showing || crtUnplug.overlayAlpha > 0.01f
    val homeWarp = crtBoot

    // Direction-lock vertical swipes (Pixel/Nova style): only claims the
    // gesture after touch-slop when movement is clearly vertical.
    // Swipe up → app drawer; swipe down → fuzzy search overlay.
    //
    // v0.23: while searchOpen, DISABLE this home recognizer (overlay is on
    // top). Search dismiss is ONLY via Android system Home (onNewIntent
    // MAIN+CATEGORY_HOME) — no overlay vertical-drag that steals list scroll.
    // Cooldown still blocks accidental swipe-down reopen after close.
    val searchClosedAtState by rememberUpdatedState(searchClosedAtMs)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(editMode, pageDragging, sheetMode, searchOpen, stampComposer != null) {
                if (editMode || pageDragging || sheetMode != SheetMode.None || searchOpen || stampComposer != null) {
                    return@pointerInput
                }
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // v0.53: taps on a stamp never create a new stamp underneath it.
                        if (stampGestureLock.busy()) return@detectTapGestures
                        val box = Offset(size.width.toFloat(), size.height.toFloat())
                        if (box.x <= 1f || box.y <= 1f) return@detectTapGestures
                        val (xf, yf) = stampFractionsFromTap(offset, box)
                        stampComposer = StampComposerState(
                            editId = null,
                            draft = "",
                            xFrac = xf,
                            yFrac = yf,
                        )
                    },
                )
            }
            .pointerInput(editMode, pageDragging, sheetMode, searchOpen) {
                if (editMode || pageDragging || sheetMode != SheetMode.None || searchOpen) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // v0.53: a gesture that starts on a home stamp (drag, long-press, drop on
                    // bin) belongs to the stamp. The chip's Main pass runs first and sets the
                    // lock, so bail here; awaitEachGesture then waits for all pointers up.
                    if (stampGestureLock.active) return@awaitEachGesture
                    var totalX = 0f
                    var totalY = 0f
                    var lockedVertical: Int = 0 // -1 up, +1 down
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // A stamp gesture is live (set by the chip on this sequence): drop the
                        // whole gesture without acting on release. Not using isConsumed here:
                        // widgets / pager may consume moves and home swipes must still work there.
                        if (stampGestureLock.active) return@awaitEachGesture
                        val delta = change.position - change.previousPosition
                        totalX += delta.x
                        totalY += delta.y
                        val dist = kotlin.math.sqrt(totalX * totalX + totalY * totalY)
                        if (lockedVertical == 0) {
                            if (dist > slop) {
                                // Horizontal or diagonal-horizontal → bail; pager owns it.
                                if (kotlin.math.abs(totalX) >= kotlin.math.abs(totalY) * 0.85f) {
                                    return@awaitEachGesture
                                }
                                lockedVertical = if (totalY < 0f) -1 else 1
                                change.consume()
                            }
                        } else {
                            change.consume()
                        }
                    }
                    if (lockedVertical != 0 &&
                        kotlin.math.abs(totalY) > 100f &&
                        kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.25f
                    ) {
                        val sinceClose =
                            SystemClock.elapsedRealtime() - searchClosedAtState
                        if (sinceClose < SEARCH_REOPEN_COOLDOWN_MS) {
                            // Same physical gesture that just dismissed search —
                            // do not open drawer or bounce search back open.
                        } else if (lockedVertical < 0 && totalY < 0f) {
                            onSheetModeChange(SheetMode.AppDrawer)
                        } else if (lockedVertical > 0 && totalY > 0f) {
                            openSearch()
                        }
                    }
                }
            },
    ) {
        // Whole home (wallpaper plate + header + icons + dock) sits inside CRT tube warp.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .crtHomeWarp(homeWarp),
        ) {
            CrtWallpaperPlate(
                active = homeWarp.showing || homeWarp.warpAmount > 0.01f,
            )
            HomeStampLayer(
                stamps = homeStamps,
                enabled = !editMode && sheetMode == SheetMode.None && !searchOpen && stampComposer == null,
                onEdit = { stamp ->
                    stampComposer = StampComposerState(
                        editId = stamp.id,
                        draft = stamp.text,
                        xFrac = stamp.xFrac,
                        yFrac = stamp.yFrac,
                    )
                },
                onDelete = { id ->
                    homeStamps = HomeStampStore.remove(context, homeStamps, id)
                },
                onMove = { id, xf, yf ->
                    homeStamps = HomeStampStore.move(context, homeStamps, id, xf, yf)
                },
                onShare = { stamp ->
                    shareHomeStamp(context, stamp)
                },
                gestureLock = stampGestureLock,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
            TechHeader(
                time = timeFmt.format(now),
                date = dateFmt.format(now).uppercase(Locale.US),
                weatherLine = weather?.display,
                status = techStatus,
                nowPlaying = nowPlaying,
                onMediaPrev = { nowPlayingMonitor.skipPrevious() },
                onMediaPlayPause = { nowPlayingMonitor.playPause() },
                onMediaNext = { nowPlayingMonitor.skipNext() },
                onMediaOpen = { nowPlayingMonitor.openMediaApp() },
                editMode = editMode,
                // v0.49: header drawer is always expanded; no toggle / no persisted state.
                drawerExpanded = true,
                onToggleDrawer = {},
                onCollapseDrawer = { onStatusDrawerExpandedChange(false) },
                onToggleEdit = { requestEnterEdit() },
                onOpenApps = { onSheetModeChange(SheetMode.AppDrawer) },
                onAddPin = { onSheetModeChange(SheetMode.PinPicker) },
                onAddWidget = { onSheetModeChange(SheetMode.WidgetPicker) },
                onStatusHintClick = {
                    if (!techStatus.notificationAccess) {
                        onOpenNotificationAccess()
                    } else {
                        onRequestStatusPermissions()
                    }
                },
                onOpenStatusTarget = onOpenStatusTarget,
                onRequestCalendarPermission = onRequestCalendarPermission,
                onRequestLocationPermission = onRequestLocationPermission,
                updateMenuExpanded = updateMenuExpanded,
                onUpdateMenuExpandedChange = { updateMenuExpanded = it },
                onCheckForUpdates = { runUpdateCheck(manual = true) },
                updateBusy = updateBusy,
                phosphorMode = phosphorMode,
                onCyclePhosphorMode = {
                    phosphorMode = prefs.cyclePhosphorMode()
                },
                onStatusLongPress = { target -> digestTarget = target },
                shakeFlashlightEnabled = shakeFlashlightEnabled,
                onToggleShakeFlashlight = {
                    onShakeFlashlightEnabledChange(!shakeFlashlightEnabled)
                },
                portraitLockEnabled = portraitLockEnabled,
                onTogglePortraitLock = {
                    onPortraitLockEnabledChange(!portraitLockEnabled)
                },
                layoutLockEnabled = layoutLockEnabledUi,
                callRadarActive = callRadar != null,
                onCallRadarTap = {
                    val hit = callRadar
                    if (hit != null) {
                        StatusMonitor.acknowledgeRadar(context, hit.id)
                        callRadar = null
                    }
                    StatusApps.dialNumber(context, hit?.number)
                },
                onCallRadarLongPress = {
                    val hit = callRadar
                    if (hit != null) {
                        StatusMonitor.acknowledgeRadar(context, hit.id)
                        callRadar = null
                    }
                    StatusApps.smsOnMyWay(context, hit?.number)
                },
                faultCode = faultBeacon?.code,
                onFaultClick = {
                    val copied = try {
                        CrashBeacon.copyAndClear(context)
                    } catch (_: Exception) {
                        false
                    }
                    if (copied) {
                        faultBeacon = null
                        Toast.makeText(context, "copied", Toast.LENGTH_SHORT).show()
                    }
                },
                onLayoutLockMenu = {
                    updateMenuExpanded = false
                    layoutLockPin = ""
                    layoutLockPinConfirm = ""
                    layoutLockError = null
                    layoutLockDialog = if (layoutLock.isEnabled()) {
                        LayoutLockDialogMode.Manage
                    } else {
                        LayoutLockDialogMode.Setup
                    }
                },
            )

            DockedPhosphorStamp(visible = chargeDocked)



            updateDialogInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = {
                        if (!updateBusy) {
                            UpdateChecker.snooze(context, info.versionCode)
                            updateDialogInfo = null
                        }
                    },
                    title = {
                        Text(
                            text = "Update available",
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    text = {
                        val notes = info.notes?.takeIf { it.isNotBlank() }
                        Text(
                            text = buildString {
                                append("POL-80 ${info.versionName} (code ${info.versionCode})")
                                append("\nInstalled: ${UpdateChecker.localVersionName()}")
                                if (notes != null) {
                                    append("\n\n")
                                    append(notes)
                                }
                                updateStatusText?.let {
                                    append("\n\n")
                                    append(it)
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { startUpdateDownload(info) },
                            enabled = !updateBusy,
                        ) {
                            Text("Update", fontFamily = FontFamily.Monospace)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                UpdateChecker.snooze(context, info.versionCode)
                                updateDialogInfo = null
                            },
                            enabled = !updateBusy,
                        ) {
                            Text("Later", fontFamily = FontFamily.Monospace)
                        }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Disable only while an edit-mode icon/widget drag is active so
                // edge cross-page moves still work; otherwise pager must own
                // horizontal swipes on first try.
                userScrollEnabled = !pageDragging,
                beyondViewportPageCount = 1,
            ) { pageIndex ->
                val page = layout.pages.getOrElse(pageIndex) { HomePage() }
                HomePageContent(
                    page = page,
                    appByKey = appByKey,
                    editMode = editMode,
                    phosphorActive = phosphorActive,
                    widgetHelper = widgetHelper,
                    onLaunch = onLaunch,
                    onEnterEditMode = { requestEnterEdit() },
                    onUnpin = { key ->
                        prefs.removePin(key)
                        reloadLayout()
                    },
                    onRemoveWidget = { id ->
                        onRemoveWidget(id)
                        reloadLayout()
                    },
                    onAddPin = { onSheetModeChange(SheetMode.PinPicker) },
                    onDraggingChanged = { pageDragging = it },
                    onReorder = { from, to ->
                        layout = prefs.reorderWithinPage(pageIndex, from, to)
                    },
                    onMoveToAdjacentPage = { itemIndex, direction ->
                        val targetPage = pageIndex + direction
                        if (targetPage < 0) return@HomePageContent
                        var current = prefs.getLayout()
                        if (targetPage >= current.pages.size) {
                            current = prefs.addPage()
                        }
                        if (pageIndex !in current.pages.indices) return@HomePageContent
                        if (itemIndex !in current.pages[pageIndex].items.indices) {
                            return@HomePageContent
                        }
                        val safeDest = targetPage.coerceAtMost(current.pages.lastIndex)
                        val destIndex = if (direction > 0) {
                            0
                        } else {
                            current.pages[safeDest].items.size
                        }
                        layout = prefs.moveItem(pageIndex, itemIndex, safeDest, destIndex)
                        scope.launch {
                            pagerState.animateScrollToPage(safeDest)
                        }
                    },
                )
            }

            PageIndicatorRow(
                pageCount = layout.pages.size,
                currentPage = pagerState.currentPage,
                editMode = editMode,
                onAddPage = {
                    layout = prefs.addPage()
                    scope.launch {
                        pagerState.animateScrollToPage(layout.pages.lastIndex)
                    }
                },
                onRemoveCurrentPage = {
                    if (layout.pages.size <= 1) return@PageIndicatorRow
                    val removeAt = pagerState.currentPage
                    layout = prefs.removePage(removeAt)
                    scope.launch {
                        pagerState.scrollToPage(
                            removeAt.coerceAtMost(layout.pages.lastIndex),
                        )
                    }
                },
            )

            DockBar(
                slots = dockApps,
                editMode = editMode,
                phosphorActive = phosphorActive,
                flashlightOn = flashlightOn,
                onLaunch = onLaunch,
                onSlotClick = { index, app ->
                    if (editMode || app == null) {
                        dockEditIndex = index
                        onSheetModeChange(SheetMode.DockPicker)
                    } else {
                        onLaunch(app)
                    }
                },
                onSlotLongClick = { index ->
                    dockEditIndex = index
                    onSheetModeChange(SheetMode.DockPicker)
                },
                onToggleFlashlight = onToggleFlashlight,
                onOpenApps = { onSheetModeChange(SheetMode.AppDrawer) },
                onOpenSearch = { openSearch() },
            )
            }
        }

        // Tap outside the header collapses the status drawer (below header z-order).
        if (statusDrawerExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
                    .padding(top = 200.dp)
                    .clickable(
                        interactionSource = remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                        indication = null,
                        onClick = { onStatusDrawerExpandedChange(false) },
                    ),
            )
        }
    }

    stampComposer?.let { composer ->
        HomeStampComposerDialog(
            state = composer,
            onDraftChange = { draft ->
                stampComposer = composer.copy(draft = draft)
            },
            onDismiss = { stampComposer = null },
            onSave = {
                val trimmed = composer.draft.trim()
                if (trimmed.isEmpty()) {
                    stampComposer = null
                    return@HomeStampComposerDialog
                }
                val stamp = HomeStampStore.Stamp(
                    id = composer.editId ?: HomeStampStore.newId(),
                    text = trimmed.take(HomeStampStore.MAX_TEXT_LEN),
                    xFrac = composer.xFrac,
                    yFrac = composer.yFrac,
                    createdAt = if (composer.editId != null) {
                        homeStamps.firstOrNull { it.id == composer.editId }?.createdAt
                            ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    },
                )
                homeStamps = HomeStampStore.upsert(context, homeStamps, stamp)
                stampComposer = null
            },
        )
    }

    if (searchOpen) {
        AppSearchOverlay(
            apps = apps,
            pinnedKeys = pinnedKeys,
            prefs = prefs,
            contactsTick = contactsTick,
            onLaunch = { app ->
                prefs.addSearchHistory(app.key)
                closeSearch()
                onLaunch(app)
            },
            onRequestContactsPermission = onRequestContactsPermission,
            onClose = { closeSearch() },
        )
    }

    if (sheetMode != SheetMode.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onSheetModeChange(SheetMode.None) },
            sheetState = sheetState,
            containerColor = SheetBg,
            contentColor = BareText,
        ) {
            when (sheetMode) {
                SheetMode.AppDrawer -> {
                    AppListSheet(
                        title = "APPS",
                        apps = apps,
                        selectedKeys = emptySet(),
                        selectionMode = false,
                        onAppClick = { app ->
                            onSheetModeChange(SheetMode.None)
                            onLaunch(app)
                        },
                    )
                }
                SheetMode.PinPicker -> {
                    AppListSheet(
                        title = "PIN TO HOME",
                        apps = apps,
                        selectedKeys = pinnedKeys,
                        selectionMode = true,
                        onAppClick = { app ->
                            prefs.togglePin(app.key, pagerState.currentPage)
                            reloadLayout()
                        },
                        onDone = {
                            onSheetModeChange(SheetMode.None)
                            onEditModeChange(false)
                        },
                    )
                }
                SheetMode.DockPicker -> {
                    val idx = dockEditIndex
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "DOCK SLOT ${idx + 1}",
                                color = TechGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                            )
                            TextButton(
                                onClick = {
                                    if (idx in 0 until LauncherPrefs.DOCK_SIZE) {
                                        prefs.setDockSlot(idx, null)
                                        dockSlots = prefs.getDockSlots()
                                    }
                                    onSheetModeChange(SheetMode.None)
                                },
                            ) {
                                Text("Clear", color = BareMuted, fontSize = 12.sp)
                            }
                        }
                        AppListSheet(
                            title = null,
                            apps = apps,
                            selectedKeys = dockSlots.getOrNull(idx)
                                ?.takeIf { it.isNotBlank() }
                                ?.let { setOf(it) }
                                ?: emptySet(),
                            selectionMode = true,
                            onAppClick = { app ->
                                if (idx in 0 until LauncherPrefs.DOCK_SIZE) {
                                    prefs.setDockSlot(idx, app.key)
                                    dockSlots = prefs.getDockSlots()
                                }
                                onSheetModeChange(SheetMode.None)
                            },
                        )
                    }
                }
                SheetMode.WidgetPicker -> {
                    WidgetPickerSheet(
                        candidates = remember { listWidgetCandidates() },
                        onSelect = { candidate ->
                            onSheetModeChange(SheetMode.None)
                            onBindWidgetProvider(candidate.info.provider)
                        },
                        onSystemPicker = {
                            onSheetModeChange(SheetMode.None)
                            onOpenSystemWidgetPicker()
                        },
                        onDone = { onSheetModeChange(SheetMode.None) },
                    )
                }
                SheetMode.None -> Unit
            }
        }
    }

    digestTarget?.let { target ->
        val digestSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val digests = remember(target, statusTick, techStatus) {
            BareNotificationListener.recentDigests(target)
        }
        ModalBottomSheet(
            onDismissRequest = { digestTarget = null },
            sheetState = digestSheetState,
            containerColor = SheetBg,
            contentColor = BareText,
        ) {
            StatusDigestSheet(
                target = target,
                digests = digests,
                notificationAccess = techStatus.notificationAccess,
                onOpenApp = {
                    digestTarget = null
                    onOpenStatusTarget(target)
                },
                onClose = { digestTarget = null },
            )
        }
    }


    layoutLockDialog?.let { mode ->
        val biometricOk = remember(mode) {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
        AlertDialog(
            onDismissRequest = {
                layoutLockDialog = null
                layoutLockPin = ""
                layoutLockPinConfirm = ""
                layoutLockError = null
            },
            title = {
                Text(
                    text = when (mode) {
                        LayoutLockDialogMode.Setup -> "Lock layout"
                        LayoutLockDialogMode.Unlock -> "Unlock layout"
                        LayoutLockDialogMode.Manage -> "Layout lock"
                        LayoutLockDialogMode.DisableConfirm -> "Disable lock"
                    },
                    fontFamily = FontFamily.Monospace,
                )
            },
            text = {
                Column {
                    Text(
                        text = when (mode) {
                            LayoutLockDialogMode.Setup ->
                                "Set a 4–8 digit PIN. Biometric unlock is offered when available."
                            LayoutLockDialogMode.Unlock ->
                                "Enter PIN or use biometric to edit the home layout."
                            LayoutLockDialogMode.Manage ->
                                "Layout edit is protected. Disable requires PIN/biometric."
                            LayoutLockDialogMode.DisableConfirm ->
                                "Enter PIN to turn off layout lock."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = BareMuted,
                    )
                    if (mode != LayoutLockDialogMode.Manage) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = layoutLockPin,
                            onValueChange = {
                                layoutLockPin = it.filter { ch -> ch.isDigit() }.take(8)
                                layoutLockError = null
                            },
                            label = { Text("PIN", fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (mode == LayoutLockDialogMode.Setup) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = layoutLockPinConfirm,
                                onValueChange = {
                                    layoutLockPinConfirm = it.filter { ch -> ch.isDigit() }.take(8)
                                    layoutLockError = null
                                },
                                label = { Text("Confirm PIN", fontFamily = FontFamily.Monospace) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    layoutLockError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = StatusRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                when (mode) {
                    LayoutLockDialogMode.Setup -> {
                        TextButton(
                            onClick = {
                                when {
                                    !LayoutLockStore.isValidPinFormat(layoutLockPin) ->
                                        layoutLockError = "PIN must be 4–8 digits"
                                    layoutLockPin != layoutLockPinConfirm ->
                                        layoutLockError = "PINs do not match"
                                    else -> {
                                        if (layoutLock.setPin(layoutLockPin)) {
                                            layoutLockEnabledUi = true
                                            layoutLockDialog = null
                                            layoutLockPin = ""
                                            layoutLockPinConfirm = ""
                                            Toast.makeText(
                                                context,
                                                "Layout lock enabled",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            layoutLockError = "Could not save PIN"
                                        }
                                    }
                                }
                            },
                        ) { Text("Enable", fontFamily = FontFamily.Monospace) }
                    }
                    LayoutLockDialogMode.Unlock -> {
                        TextButton(
                            onClick = {
                                if (layoutLock.verifyPin(layoutLockPin)) {
                                    onLayoutSessionUnlocked()
                                    layoutLockDialog = null
                                    layoutLockPin = ""
                                    layoutLockError = null
                                    onEditModeChange(true)
                                } else {
                                    layoutLockError = "Wrong PIN"
                                }
                            },
                        ) { Text("Unlock", fontFamily = FontFamily.Monospace) }
                    }
                    LayoutLockDialogMode.Manage -> {
                        TextButton(
                            onClick = {
                                layoutLockPin = ""
                                layoutLockError = null
                                layoutLockDialog = LayoutLockDialogMode.DisableConfirm
                            },
                        ) { Text("Disable…", fontFamily = FontFamily.Monospace) }
                    }
                    LayoutLockDialogMode.DisableConfirm -> {
                        TextButton(
                            onClick = {
                                if (layoutLock.verifyPin(layoutLockPin)) {
                                    layoutLock.clear()
                                    layoutLockEnabledUi = false
                                    onLayoutSessionLocked()
                                    layoutLockDialog = null
                                    layoutLockPin = ""
                                    Toast.makeText(
                                        context,
                                        "Layout lock disabled",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    layoutLockError = "Wrong PIN"
                                }
                            },
                        ) { Text("Disable", fontFamily = FontFamily.Monospace) }
                    }
                }
            },
            dismissButton = {
                Row {
                    if ((mode == LayoutLockDialogMode.Unlock ||
                            mode == LayoutLockDialogMode.DisableConfirm) && biometricOk
                    ) {
                        TextButton(onClick = {
                            if (mode == LayoutLockDialogMode.Unlock) {
                                promptBiometricUnlock()
                            } else {
                                val act = activity ?: return@TextButton
                                val executor = ContextCompat.getMainExecutor(context)
                                val prompt = BiometricPrompt(
                                    act,
                                    executor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(
                                            result: BiometricPrompt.AuthenticationResult,
                                        ) {
                                            layoutLock.clear()
                                            layoutLockEnabledUi = false
                                            onLayoutSessionLocked()
                                            layoutLockDialog = null
                                            layoutLockPin = ""
                                            Toast.makeText(
                                                context,
                                                "Layout lock disabled",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                                val info = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Disable layout lock")
                                    .setNegativeButtonText("Cancel")
                                    .setAllowedAuthenticators(
                                        BiometricManager.Authenticators.BIOMETRIC_WEAK,
                                    )
                                    .build()
                                runCatching { prompt.authenticate(info) }
                            }
                        }) { Text("Biometric", fontFamily = FontFamily.Monospace) }
                    }
                    TextButton(
                        onClick = {
                            layoutLockDialog = null
                            layoutLockPin = ""
                            layoutLockPinConfirm = ""
                            layoutLockError = null
                        },
                    ) { Text("Cancel", fontFamily = FontFamily.Monospace) }
                }
            },
        )
    }

    if (!unplugActive) {
        CrtBootSplashOverlay(anim = crtBoot)
    }
    CrtPowerDownFlicker(anim = crtUnplug)
    RainDrizzleOverlay(
        active = rainDrizzleActive,
        onCleared = { rainDrizzleActive = false },
    )
    EdgeClockOverlay(
        visible = edgeClockVisible,
        side = edgeClockSide,
        timeHm = edgeHmFmt.format(now),
        seconds = edgeSecFmt.format(now),
    )
}

@Composable
private fun PageIndicatorRow(
    pageCount: Int,
    currentPage: Int,
    editMode: Boolean,
    onAddPage: () -> Unit,
    onRemoveCurrentPage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode && pageCount > 1) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Scrim)
                    .clickable(onClick = onRemoveCurrentPage),
                contentAlignment = Alignment.Center,
            ) {
                Text("−", color = BareMuted, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(10.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { i ->
                val selected = i == currentPage
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else 6.dp)
                        .then(
                            if (selected) {
                                Modifier.drawBehind {
                                    drawCircle(
                                        color = BareColors.NeonCyan.copy(alpha = 0.40f),
                                        radius = size.maxDimension * 0.85f,
                                    )
                                    drawCircle(
                                        color = BareColors.NeonMagenta.copy(alpha = 0.22f),
                                        radius = size.maxDimension * 1.05f,
                                    )
                                }
                            } else Modifier
                        )
                        .padding(if (selected) 2.dp else 0.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) BareColors.NeonCyan
                            else BareColors.TextMuted.copy(alpha = 0.40f),
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    BareColors.NeonMagenta.copy(alpha = 0.80f),
                                    CircleShape,
                                )
                            } else Modifier
                        ),
                )
            }
        }
        if (editMode) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Scrim)
                    .clickable(onClick = onAddPage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Add page",
                    tint = BareMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun HomePageContent(
    page: HomePage,
    appByKey: Map<String, LaunchableApp>,
    editMode: Boolean,
    phosphorActive: Boolean,
    widgetHelper: WidgetHostHelper,
    onLaunch: (LaunchableApp) -> Unit,
    onEnterEditMode: () -> Unit,
    onUnpin: (String) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onAddPin: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveToAdjacentPage: (itemIndex: Int, direction: Int) -> Unit,
) {
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 40.dp.toPx() }
    val edgeThresholdPx = with(density) { 72.dp.toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var accumSwapY by remember { mutableFloatStateOf(0f) }
    var accumSwapX by remember { mutableFloatStateOf(0f) }
    var edgeTicks by remember { mutableIntStateOf(0) }
    // Red − badge must NOT be tappable until well after the long-press that
    // entered edit mode has fully ended (finger up + quiet period). Otherwise
    // release-on-badge unpins. Prefer ≥600ms; 700ms leaves margin on Pixel.
    var badgeArmed by remember { mutableStateOf(false) }
    LaunchedEffect(editMode) {
        if (editMode) {
            badgeArmed = false
            delay(700L)
            badgeArmed = true
        } else {
            badgeArmed = false
        }
    }

    val items = page.items
    val rows = remember(items) { buildVisualRows(items) }
    val scrollState = rememberScrollState()
    val itemsRef = rememberUpdatedState(items)
    val onReorderRef = rememberUpdatedState(onReorder)
    val onMoveRef = rememberUpdatedState(onMoveToAdjacentPage)
    val onDraggingRef = rememberUpdatedState(onDraggingChanged)

    fun dragModifierFor(index: Int): Modifier {
        if (!editMode) return Modifier
        val isDragging = draggingIndex == index
        return Modifier
            .zIndex(if (isDragging) 2f else 0f)
            .offset {
                if (isDragging) {
                    IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                } else {
                    IntOffset.Zero
                }
            }
            .scale(if (isDragging) 1.06f else 1f)
            .alpha(if (isDragging) 0.9f else 1f)
            // Key only on index + editMode so reorder does not cancel the active gesture.
            .pointerInput(index, editMode) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingIndex = index
                        dragOffset = Offset.Zero
                        accumSwapY = 0f
                        accumSwapX = 0f
                        edgeTicks = 0
                        onDraggingRef.value(true)
                    },
                    onDragCancel = {
                        draggingIndex = null
                        dragOffset = Offset.Zero
                        accumSwapY = 0f
                        accumSwapX = 0f
                        edgeTicks = 0
                        onDraggingRef.value(false)
                    },
                    onDragEnd = {
                        draggingIndex = null
                        dragOffset = Offset.Zero
                        accumSwapY = 0f
                        accumSwapX = 0f
                        edgeTicks = 0
                        onDraggingRef.value(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        accumSwapY += dragAmount.y
                        accumSwapX += dragAmount.x

                        var from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        val last = itemsRef.value.lastIndex

                        when {
                            accumSwapY > swapThresholdPx && from < last -> {
                                onReorderRef.value(from, from + 1)
                                draggingIndex = from + 1
                                from += 1
                                accumSwapY = 0f
                                dragOffset = Offset(dragOffset.x, 0f)
                            }
                            accumSwapY < -swapThresholdPx && from > 0 -> {
                                onReorderRef.value(from, from - 1)
                                draggingIndex = from - 1
                                from -= 1
                                accumSwapY = 0f
                                dragOffset = Offset(dragOffset.x, 0f)
                            }
                            accumSwapX > swapThresholdPx && from < last -> {
                                onReorderRef.value(from, from + 1)
                                draggingIndex = from + 1
                                from += 1
                                accumSwapX = 0f
                                dragOffset = Offset(0f, dragOffset.y)
                            }
                            accumSwapX < -swapThresholdPx && from > 0 -> {
                                onReorderRef.value(from, from - 1)
                                draggingIndex = from - 1
                                from -= 1
                                accumSwapX = 0f
                                dragOffset = Offset(0f, dragOffset.y)
                            }
                        }

                        if (kotlin.math.abs(dragOffset.x) > edgeThresholdPx * 1.6f) {
                            edgeTicks++
                            if (edgeTicks > 14) {
                                val dir = if (dragOffset.x > 0) 1 else -1
                                onMoveRef.value(from, dir)
                                draggingIndex = null
                                dragOffset = Offset.Zero
                                accumSwapY = 0f
                                accumSwapX = 0f
                                edgeTicks = 0
                                onDraggingRef.value(false)
                            }
                        } else {
                            edgeTicks = 0
                        }
                    },
                )
            }
    }

    // Nested scroll: never consume horizontal deltas so HorizontalPager wins
    // page swipes (Pixel workspace direction-lock equivalent). Vertical scroll
    // still works for tall pin/widget stacks.
    val pagerPriorityNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Do not steal horizontal from the pager in pre-scroll.
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Leave leftover horizontal for the pager's nested scroll.
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pagerPriorityNestedScroll)
            .verticalScroll(scrollState, enabled = draggingIndex == null)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) {
            EmptyHomeHint(editMode = editMode, onAdd = onAddPin)
        }

        rows.forEach { row ->
            when (row) {
                is VisualRow.Widget -> {
                    Box(modifier = dragModifierFor(row.index).fillMaxWidth()) {
                        WidgetSlot(
                            widgetHelper = widgetHelper,
                            appWidgetId = row.item.widgetId,
                            editMode = editMode,
                            onRemove = { onRemoveWidget(row.item.widgetId) },
                        )
                    }
                }
                is VisualRow.Pins -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.entries.forEach { (flatIndex, pin) ->
                            val app = appByKey[pin.key]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(dragModifierFor(flatIndex)),
                            ) {
                                if (app != null) {
                                    AppGridItem(
                                        app = app,
                                        badge = if (editMode) "−" else null,
                                        phosphorActive = phosphorActive,
                                        onClick = {
                                            if (!editMode) onLaunch(app)
                                        },
                                        onLongClick = {
                                            // Outside edit: enter edit mode ONLY.
                                            // Never togglePin / removePin / onUnpin from icon long-press.
                                            if (!editMode) {
                                                onEnterEditMode()
                                            }
                                            // In edit mode: no-op here (drag + badge handle actions).
                                        },
                                        onBadgeClick = if (editMode && badgeArmed) {
                                            { onUnpin(pin.key) }
                                        } else {
                                            null
                                        },
                                        clicksEnabled = !editMode || draggingIndex == null,
                                    )
                                }
                            }
                        }
                        repeat(4 - row.entries.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (editMode && items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, Placeholder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddPin),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add pin",
                        tint = BareMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TechHeader(
    time: String,
    date: String,
    weatherLine: String?,
    status: TechStatus,
    nowPlaying: NowPlayingInfo?,
    onMediaPrev: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaOpen: () -> Unit,
    editMode: Boolean,
    drawerExpanded: Boolean,
    onToggleDrawer: () -> Unit,
    onCollapseDrawer: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpenApps: () -> Unit,
    onAddPin: () -> Unit,
    onAddWidget: () -> Unit,
    onStatusHintClick: () -> Unit,
    onOpenStatusTarget: (StatusApps.Target) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    updateMenuExpanded: Boolean = false,
    onUpdateMenuExpandedChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    updateBusy: Boolean = false,
    phosphorMode: PhosphorMode = PhosphorMode.Auto,
    onCyclePhosphorMode: () -> Unit = {},
    onStatusLongPress: (StatusApps.Target) -> Unit = {},
    shakeFlashlightEnabled: Boolean = false,
    onToggleShakeFlashlight: () -> Unit = {},
    portraitLockEnabled: Boolean = true,
    onTogglePortraitLock: () -> Unit = {},
    layoutLockEnabled: Boolean = false,
    onLayoutLockMenu: () -> Unit = {},
    callRadarActive: Boolean = false,
    onCallRadarTap: () -> Unit = {},
    onCallRadarLongPress: () -> Unit = {},
    faultCode: String? = null,
    onFaultClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(10.dp)
    // Layer order: palm bitmap → light readable scrim → chrome text.
    // v0.14 used Glass (~90%) + ~85–91% black overlay which crushed the art.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .border(1.dp, BareColors.HeaderRim, shape),
    ) {
        Image(
            painter = painterResource(R.drawable.header_palms),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = 1f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x4D050510), // ~30% — readable top chrome
                            Color(0x26050510), // ~15% mid — preserve dusk + fronds
                            Color(0x45050510), // ~27% at the palm-heavy bottom
                        ),
                    ),
                )
                .drawBehind {
                    // Subtle scanlines ~5% — header only.
                    val step = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = BareColors.TextPrimary.copy(alpha = 0.05f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                        y += step
                    }
                },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // SYS can ellipsize; stamp keeps intrinsic width so "POL-80 0.xx" never clips.
                    Text(
                        text = "SYS  $time",
                        color = BareColors.SysAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        lineHeight = 28.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "POL-80 ${BuildConfig.VERSION_NAME.substringBefore('-')}",
                        color = BareColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = date,
                        color = BareColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp,
                        lineHeight = 18.sp,
                    )
                    if (weatherLine != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = weatherLine,
                            color = BareColors.NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    SignalWeatherChip(reading = status.signalWeather)
                    if (!status.notificationAccess) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "NLS?",
                            color = BareColors.NeonMagentaSoft.copy(alpha = 0.95f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.clickable(onClick = onStatusHintClick),
                        )
                    }
                }
                // v0.48 context chip: TMR > CHG > ALM > BAT; absent when nothing relevant.
                ContextChip(refreshKey = status.hashCode())
            }
            if (editMode) {
                IconButton(onClick = onAddWidget, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Widgets, contentDescription = "Add widget", tint = BareColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onAddPin, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Pin app", tint = BareColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Done", tint = BareColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
            } else {
                Box {
                    IconButton(
                        onClick = { onUpdateMenuExpandedChange(true) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Menu",
                            tint = BareColors.TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = updateMenuExpanded,
                        onDismissRequest = { onUpdateMenuExpandedChange(false) },
                    ) {
                        // v0.49: pencil moved here; onToggleEdit -> requestEnterEdit() keeps PIN/biometric lock path.
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Edit layout",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onUpdateMenuExpandedChange(false)
                                onToggleEdit()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (updateBusy) "Checking…" else "Check for updates",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onUpdateMenuExpandedChange(false)
                                onCheckForUpdates()
                            },
                            enabled = !updateBusy,
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = phosphorMode.menuLabel,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onCyclePhosphorMode()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (shakeFlashlightEnabled) {
                                        "Shake flashlight: On"
                                    } else {
                                        "Shake flashlight: Off"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onToggleShakeFlashlight()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (portraitLockEnabled) {
                                        "Portrait lock: On"
                                    } else {
                                        "Portrait lock: Off"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onTogglePortraitLock()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (layoutLockEnabled) {
                                        "Lock layout: On"
                                    } else {
                                        "Lock layout: Off"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                onUpdateMenuExpandedChange(false)
                                onLayoutLockMenu()
                            },
                        )
                    }
                }
            }
        }

        faultCode?.let { code ->
            FaultBeaconLine(code = code, onClick = onFaultClick)
        }

        // Phone / SMS / WhatsApp / Proton / Slack — icons + counts on palms header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMetric(
                icon = Icons.Outlined.Phone,
                contentDescription = "Missed calls",
                value = status.missedCalls,
                pulse = callRadarActive,
                onClick = {
                    if (callRadarActive) onCallRadarTap() else onOpenStatusTarget(StatusApps.Target.CALL)
                },
                onLongClick = {
                    if (callRadarActive) onCallRadarLongPress() else onStatusLongPress(StatusApps.Target.CALL)
                },
            )
            StatusMetric(
                icon = Icons.Outlined.Sms,
                contentDescription = "Unread SMS",
                value = status.unreadSms,
                onClick = { onOpenStatusTarget(StatusApps.Target.SMS) },
                onLongClick = { onStatusLongPress(StatusApps.Target.SMS) },
            )
            StatusMetric(
                iconRes = R.drawable.ic_status_whatsapp,
                contentDescription = "WhatsApp unread",
                value = status.unreadWhatsApp,
                onClick = { onOpenStatusTarget(StatusApps.Target.WA) },
                onLongClick = { onStatusLongPress(StatusApps.Target.WA) },
            )
            StatusMetric(
                icon = Icons.Outlined.Email,
                contentDescription = "Proton Mail unread",
                value = status.unreadProton,
                onClick = { onOpenStatusTarget(StatusApps.Target.PR) },
                onLongClick = { onStatusLongPress(StatusApps.Target.PR) },
            )
            StatusMetric(
                iconRes = R.drawable.ic_status_slack,
                contentDescription = "Slack unread",
                value = status.unreadSlack,
                onClick = { onOpenStatusTarget(StatusApps.Target.SL) },
                onLongClick = { onStatusLongPress(StatusApps.Target.SL) },
            )
        }

        if (nowPlaying != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BareColors.NeonCyan.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(6.dp))
            NowPlayingStrip(
                info = nowPlaying,
                onPrev = onMediaPrev,
                onPlayPause = onMediaPlayPause,
                onNext = onMediaNext,
                onOpen = onMediaOpen,
            )
        }

        if (drawerExpanded) {
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BareColors.NeonMagenta.copy(alpha = 0.30f)),
            )
            Spacer(Modifier.height(4.dp))
            StatusDrawerPanel(
                status = status,
                onRequestCalendarPermission = onRequestCalendarPermission,
                onRequestLocationPermission = onRequestLocationPermission,
            )
        }
    }
    }
}

@Composable
private fun NowPlayingStrip(
    info: NowPlayingInfo,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onOpen)
                .padding(end = 4.dp),
        ) {
            Text(
                text = "NOW",
                color = BareColors.NeonMagenta,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = info.displayLine,
                color = BareColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.SkipPrevious,
                contentDescription = "Previous",
                tint = BareColors.NeonCyan,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp)) {
            Icon(
                if (info.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (info.isPlaying) "Pause" else "Play",
                tint = BareColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.SkipNext,
                contentDescription = "Next",
                tint = BareColors.NeonCyan,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusMetric(
    value: Int?,
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    onLongClick: (() -> Unit)? = null,
    pulse: Boolean = false,
) {
    val valueText = if (value == null) "—" else value.toString()
    val valueColor = when {
        value == null || value <= 0 -> StatusMutedValue
        value >= StatusMonitor.STATUS_RED_AT -> StatusRed
        value >= StatusMonitor.STATUS_AMBER_AT -> StatusAmber
        else -> TechGreen
    }
    // Bright icon tint so glyphs read on palm dusk art (same as former text labels).
    val iconTint = BareColors.TextPrimary
    val clickMod = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box {
        if (pulse) {
            val radarTransition = rememberInfiniteTransition(label = "callRadar")
            val radarAlpha by radarTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "callRadarAlpha",
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        val radius = (size.minDimension / 2f) - stroke / 2f
                        drawCircle(
                            color = BareColors.TechGreen.copy(alpha = radarAlpha * 0.95f),
                            radius = radius.coerceAtLeast(stroke),
                            style = Stroke(width = stroke),
                        )
                    },
            )
        }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(clickMod)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            iconRes != null -> Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = valueText,
            color = valueColor.copy(alpha = 1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
    }
}

@Composable
private fun FaultBeaconLine(code: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "faultBeacon")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "faultAlpha",
    )
    Text(
        text = code,
        color = BareColors.StatusAmber.copy(alpha = alpha),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}

@Composable
private fun StatusDigestSheet(
    target: StatusApps.Target,
    digests: List<NotifDigest>,
    notificationAccess: Boolean,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val title = when (target) {
        StatusApps.Target.CALL -> "CALL"
        StatusApps.Target.SMS -> "SMS"
        StatusApps.Target.WA -> "WHATSAPP"
        StatusApps.Target.PR -> "PROTON"
        StatusApps.Target.SL -> "SLACK"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$title DIGEST",
                color = BareColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenApp)
                    .padding(vertical = 4.dp),
            )
            TextButton(onClick = onClose) {
                Text("Close", color = BareMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            !notificationAccess -> {
                Text(
                    text = "Enable notification access to see titles.\nTap header chip or Open to launch the app.",
                    color = BareMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenApp)
                        .padding(vertical = 12.dp),
                )
            }
            digests.isEmpty() -> {
                Text(
                    text = "No recent notifications",
                    color = BareMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenApp)
                        .padding(vertical = 16.dp),
                )
            }
            else -> {
                digests.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenApp)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = item.title,
                            color = BareColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.text.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.text,
                                color = BareColors.NeonCyan.copy(alpha = 0.85f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < digests.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BareColors.NeonCyan.copy(alpha = 0.18f)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onOpenApp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Open $title",
                color = BareColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}



@Composable
private fun SignalWeatherChip(reading: SignalWeatherReading) {
    val context = LocalContext.current
    Text(
        text = reading.chipLabel,
        color = signalWeatherColor(reading.state),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(BareColors.Bg.copy(alpha = 0.35f))
            .border(0.5.dp, signalWeatherColor(reading.state).copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .clickable {
                Toast.makeText(context, reading.detailLine, Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private fun signalWeatherColor(state: SignalWx): Color = when (state) {
    SignalWx.CLEAR -> BareColors.TechGreen
    SignalWx.LIGHT -> BareColors.NeonCyan
    SignalWx.STATIC -> BareColors.StatusAmber
    SignalWx.STORM -> BareColors.StatusRed
    SignalWx.UNKNOWN -> BareColors.TextMuted
}

@Composable
private fun StatusDrawerPanel(
    status: TechStatus,
    onRequestCalendarPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    // v0.49 compact HUD: line 1 = BAT · NET · RF, line 2 = CAL (1 line, ellipsized).
    // RF token is strength-only (dBm or bars): the CLEAR/STATIC state already lives in the chip.
    val pct = status.batteryPercent
    val batText = when {
        pct == null -> "BAT —"
        status.batteryCharging -> "BAT $pct%⚡"
        else -> "BAT $pct%"
    }
    val batColor = when {
        pct == null -> StatusMutedValue
        status.batteryCharging -> TechGreen
        pct <= 15 -> StatusRed
        pct <= 30 -> StatusAmber
        else -> TechGreen
    }
    val rawNet = status.networkLabel
    val netShort = when {
        rawNet == null -> "—"
        rawNet.startsWith("Wi") -> {
            val ssid = rawNet.substringAfter(" ", "").trim()
            if (ssid.isEmpty()) "WIFI" else "WIFI " + (if (ssid.length > 10) ssid.take(9) + "…" else ssid)
        }
        rawNet == "Mobile" -> "CELL"
        rawNet == "Ethernet" -> "ETH"
        rawNet == "Bluetooth" -> "BT"
        rawNet == "OFFLINE" -> "OFF"
        rawNet == "Online" -> "ON"
        else -> rawNet.uppercase()
    }
    val netColor = if (rawNet == null || rawNet == "OFFLINE") StatusRed else TechGreen
    val sw = status.signalWeather
    val rfText = "RF " + when {
        sw.dbm != null -> "${sw.dbm}dBm"
        sw.bars != null -> "${sw.bars}/4"
        else -> "—"
    }
    val calText = when {
        !status.calendarPermission -> "CAL tap to grant"
        status.nextEvent == null -> "CAL —"
        else -> "CAL ${StatusMonitor.formatEventTime(status.nextEvent.startMillis)} · ${status.nextEvent.title}"
    }
    val tokenStyle: @Composable (String, Color, Modifier) -> Unit = { t, c, m ->
        Text(
            text = t,
            color = c,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = m,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tokenStyle(batText, batColor, Modifier)
            tokenStyle(" · ", BareColors.TextMuted, Modifier)
            tokenStyle(
                "NET $netShort",
                netColor,
                Modifier
                    .weight(1f, fill = false)
                    .clickable {
                        if (rawNet == "Wi‑Fi" || rawNet == null) onRequestLocationPermission()
                    },
            )
            tokenStyle(" · ", BareColors.TextMuted, Modifier)
            tokenStyle(rfText, signalWeatherColor(sw.state), Modifier)
        }
        tokenStyle(
            calText,
            if (!status.calendarPermission) StatusAmber else TechGreen,
            Modifier
                .fillMaxWidth()
                .clickable { if (!status.calendarPermission) onRequestCalendarPermission() },
        )
    }
}

@Composable
private fun EmptyHomeHint(editMode: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .clickable(onClick = onAdd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (editMode) "+ PIN APPS" else "TAP EDIT TO PIN APPS",
            color = BareMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "home is empty",
            color = BareMuted.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: LaunchableApp,
    badge: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBadgeClick: (() -> Unit)? = null,
    clicksEnabled: Boolean = true,
    phosphorActive: Boolean = false,
) {
    val bitmap = remember(app.packageName, app.activityName) {
        runCatching { app.icon.toBitmap(144, 144).asImageBitmap() }.getOrNull()
    }
    val phosphorFilter = PhosphorNight.rememberIconColorFilter()
    val labelColor = if (phosphorActive) BareColors.NeonCyan else BareColors.TextPrimary

    // Contract: long-press is NEVER an unpin. Unpin is only via onBadgeClick (red −).
    // Drop combinedClickable whenever a badge is visible (edit UI) OR a badge
    // handler is present — so the entering long-press cannot release onto a
    // clickable −, and parent detectDragGesturesAfterLongPress owns the gesture.
    val clickMod = when {
        !clicksEnabled -> Modifier
        badge != null -> Modifier
        onBadgeClick != null -> Modifier
        else -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickMod)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    colorFilter = if (phosphorActive) phosphorFilter else null,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(ScrimStrong, RoundedCornerShape(12.dp)),
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(BareColors.BadgeRed, CircleShape)
                        .then(
                            if (onBadgeClick != null) {
                                Modifier.clickable(onClick = onBadgeClick)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, color = Color.White, fontSize = 10.sp, lineHeight = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = app.label.uppercase(Locale.getDefault()),
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockBar(
    slots: List<LaunchableApp?>,
    editMode: Boolean,
    phosphorActive: Boolean = false,
    flashlightOn: Boolean,
    onLaunch: (LaunchableApp) -> Unit,
    onSlotClick: (Int, LaunchableApp?) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onToggleFlashlight: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val dockShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
    // Palm art first, light scrim second — no solid Surface fill (that hid dusk).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(dockShape)
            .border(1.dp, BareColors.NeonCyan.copy(alpha = 0.28f), dockShape),
    ) {
        Image(
            painter = painterResource(R.drawable.dock_palms),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = 1f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x59050510), // ~35%
                            Color(0x33050510), // ~20%
                            Color(0x4D050510), // ~30%
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
        slots.forEachIndexed { index, app ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { onSlotClick(index, app) },
                        onLongClick = { onSlotLongClick(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (app != null) {
                    val bitmap = remember(app.key) {
                        runCatching { app.icon.toBitmap(128, 128).asImageBitmap() }.getOrNull()
                    }
                    val phosphorFilter = PhosphorNight.rememberIconColorFilter()
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = app.label,
                            colorFilter = if (phosphorActive) phosphorFilter else null,
                            modifier = Modifier.size(44.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Placeholder, RoundedCornerShape(12.dp)),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, Placeholder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (editMode) {
                            Text(
                                "+",
                                color = BareMuted,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
        // Always-on flashlight (extra dock control; Apps stays reachable).
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggleFlashlight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (flashlightOn) BareColors.NeonCyan.copy(alpha = 0.28f)
                        else Placeholder,
                        RoundedCornerShape(12.dp),
                    )
                    .then(
                        if (flashlightOn) {
                            Modifier.border(1.dp, BareColors.NeonCyan, RoundedCornerShape(12.dp))
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (flashlightOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    contentDescription = if (flashlightOn) "Flashlight on" else "Flashlight",
                    tint = if (flashlightOn) BareColors.NeonCyan else BareText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onOpenApps,
                    onLongClick = onOpenSearch,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Placeholder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Apps,
                    contentDescription = "All apps",
                    tint = BareText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    }
        // Magenta/coral dusk accent underline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(2.dp)
                .background(BareColors.DockUnderline, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun WidgetSlot(
    widgetHelper: WidgetHostHelper,
    appWidgetId: Int,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Scrim),
    ) {
        AndroidView(
            factory = { ctx ->
                val frame = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                val hostView = widgetHelper.createView(ctx, appWidgetId)
                if (hostView != null) {
                    frame.addView(
                        hostView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            hostView.layoutParams?.height
                                ?: (120 * ctx.resources.displayMetrics.density).toInt(),
                        ),
                    )
                } else {
                    frame.minimumHeight = (80 * ctx.resources.displayMetrics.density).toInt()
                }
                frame
            },
            update = { frame ->
                if (frame.childCount == 0) {
                    widgetHelper.createView(context, appWidgetId)?.let { hv ->
                        frame.addView(
                            hv,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                hv.layoutParams?.height
                                    ?: (120 * context.resources.displayMetrics.density).toInt(),
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(BareColors.BadgeRed, CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove widget",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppListSheet(
    title: String?,
    apps: List<LaunchableApp>,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    onAppClick: (LaunchableApp) -> Unit,
    onDone: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = TechGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
                if (onDone != null) {
                    TextButton(onClick = onDone) {
                        Text("Done", color = TechGreen, fontSize = 12.sp)
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(apps, key = { it.key }) { app ->
                val selected = selectedKeys.contains(app.key)
                Box {
                    AppGridItem(
                        app = app,
                        badge = when {
                            selectionMode && selected -> "✓"
                            else -> null
                        },
                        onClick = { onAppClick(app) },
                    )
                }
            }
        }
    }
}


@Composable
private fun WidgetPickerSheet(
    candidates: List<WidgetHostHelper.WidgetCandidate>,
    onSelect: (WidgetHostHelper.WidgetCandidate) -> Unit,
    onSystemPicker: () -> Unit,
    onDone: () -> Unit,
) {
    val grouped = remember(candidates) {
        candidates.groupBy { it.appLabel }
            .toList()
            .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WIDGETS",
                color = TechGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSystemPicker) {
                    Text("System", color = BareMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onDone) {
                    Text("Done", color = TechGreen, fontSize = 12.sp)
                }
            }
        }
        Text(
            text = "Tap a preview to place on this page",
            color = BareMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        if (grouped.isEmpty()) {
            Text(
                text = "No widgets found",
                color = BareMuted,
                modifier = Modifier.padding(24.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                grouped.forEach { (appLabel, widgets) ->
                    item(key = "hdr-$appLabel") {
                        Text(
                            text = appLabel.uppercase(Locale.getDefault()),
                            color = TechGreen.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
                        )
                    }
                    item(key = "grid-$appLabel") {
                        // 2-column preview grid within the app group
                        val rows = widgets.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowWidgets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowWidgets.forEach { candidate ->
                                        WidgetPreviewCard(
                                            candidate = candidate,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onSelect(candidate) },
                                        )
                                    }
                                    if (rowWidgets.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    candidate: WidgetHostHelper.WidgetCandidate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val previewBmp = remember(candidate.info.provider) {
        val src = candidate.preview ?: candidate.icon
        src?.let {
            runCatching {
                val w = it.intrinsicWidth.coerceIn(48, 512).takeIf { d -> d > 0 } ?: 256
                val h = it.intrinsicHeight.coerceIn(48, 512).takeIf { d -> d > 0 } ?: 160
                it.toBitmap(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
    val iconBmp = remember(candidate.info.provider) {
        candidate.icon?.let {
            runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Scrim)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ScrimStrong),
            contentAlignment = Alignment.Center,
        ) {
            if (previewBmp != null) {
                Image(
                    bitmap = previewBmp,
                    contentDescription = candidate.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                )
            } else if (iconBmp != null) {
                Image(
                    bitmap = iconBmp,
                    contentDescription = candidate.label,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = BareMuted,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = candidate.label,
            color = BareText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/* ---------- fuzzy app search overlay (swipe down) ---------- */

private fun fuzzyScore(query: String, label: String): Int {
    if (query.isBlank()) return 1
    val q = query.lowercase(Locale.getDefault()).trim()
    val t = label.lowercase(Locale.getDefault())
    if (t == q) return 1000
    if (t.startsWith(q)) return 800 - (t.length - q.length).coerceAtMost(100)
    if (t.contains(q)) return 600 - t.indexOf(q).coerceAtMost(100)
    // subsequence / soft fuzzy
    var qi = 0
    var gaps = 0
    var last = -1
    for (i in t.indices) {
        if (qi < q.length && t[i] == q[qi]) {
            if (last >= 0) gaps += i - last - 1
            last = i
            qi++
        }
    }
    if (qi == q.length) return 400 - gaps.coerceAtMost(200)
    // token prefix (multi-word labels)
    val tokens = t.split(' ', '-', '_', '.')
    if (tokens.any { it.startsWith(q) }) return 500
    return -1
}

private fun filterAppsFuzzy(
    apps: List<LaunchableApp>,
    pinnedKeys: Set<String>,
    query: String,
): List<LaunchableApp> {
    val scored = apps.mapNotNull { app ->
        val s = fuzzyScore(query, app.label)
        if (s < 0) null else app to (s + if (pinnedKeys.contains(app.key)) 50 else 0)
    }
    return scored.sortedWith(
        compareByDescending<Pair<LaunchableApp, Int>> { it.second }
            .thenBy { it.first.label.lowercase(Locale.getDefault()) },
    ).map { it.first }
}

@Composable
private fun AppSearchOverlay(
    apps: List<LaunchableApp>,
    pinnedKeys: Set<String>,
    prefs: LauncherPrefs,
    contactsTick: Int,
    onLaunch: (LaunchableApp) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var historyItems by remember { mutableStateOf(prefs.getSearchHistory()) }
    val filteredApps = remember(apps, pinnedKeys, query) {
        filterAppsFuzzy(apps, pinnedKeys, query)
    }
    val hasContactsPerm = remember(contactsTick) { ContactsRepo.hasPermission(context) }
    val allContacts = remember(contactsTick, hasContactsPerm) {
        if (hasContactsPerm) ContactsRepo.loadContacts(context) else emptyList()
    }
    val filteredContacts = remember(allContacts, query) {
        ContactsRepo.filterContacts(allContacts, query)
    }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var askedContacts by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Graceful contacts request once per search open (if missing).
    LaunchedEffect(Unit) {
        if (!hasContactsPerm && !askedContacts) {
            askedContacts = true
            onRequestContactsPermission()
        }
    }

    fun refreshHistory() {
        historyItems = prefs.getSearchHistory()
    }

    fun recordPersonAndAct(contact: ContactMatch, action: SearchHistoryPersonAction) {
        prefs.addSearchHistoryPerson(
            contactId = contact.id,
            number = contact.number,
            name = contact.name,
            action = action,
        )
        refreshHistory()
        when (action) {
            SearchHistoryPersonAction.Phone -> ContactsRepo.dial(context, contact.number)
            SearchHistoryPersonAction.WhatsApp -> ContactsRepo.openWhatsApp(context, contact.number)
        }
        onClose()
    }

    fun replayHistory(item: SearchHistoryItem) {
        when (item) {
            is SearchHistoryItem.App -> {
                val app = appsByKey[item.key] ?: return
                onLaunch(app)
            }
            is SearchHistoryItem.Person -> {
                prefs.addSearchHistoryPerson(
                    contactId = item.contactId,
                    number = item.number,
                    name = item.name,
                    action = item.action,
                )
                refreshHistory()
                when (item.action) {
                    SearchHistoryPersonAction.Phone ->
                        ContactsRepo.dial(context, item.number)
                    SearchHistoryPersonAction.WhatsApp ->
                        ContactsRepo.openWhatsApp(context, item.number)
                }
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f)
            .background(Color(0xE6050510))
            // v0.23: no overlay vertical-drag / pointerInput consume — LazyColumn
            // lists must scroll normally. Dismiss = system Home (Activity
            // onNewIntent) or Back / close button / tap empty chrome.
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .clickable(
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                indication = null,
                onClick = onClose,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clickable(
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BareColors.Surface)
                    .border(1.dp, BareColors.NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = BareColors.NeonCyan,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = BareColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = SolidColor(BareColors.NeonCyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val first = filteredApps.firstOrNull()
                            if (first != null) onLaunch(first)
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search apps & people…",
                                color = BareMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = BareMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Mixed MRU: apps + people Phone/WA under the search bar.
            if (historyItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RECENT",
                        color = BareMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        historyItems.forEach { item ->
                            when (item) {
                                is SearchHistoryItem.App -> {
                                    val app = appsByKey[item.key]
                                    if (app != null) {
                                        SearchHistoryAppChip(
                                            app = app,
                                            onClick = { replayHistory(item) },
                                        )
                                    }
                                }
                                is SearchHistoryItem.Person -> {
                                    SearchHistoryPersonChip(
                                        item = item,
                                        onClick = { replayHistory(item) },
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            prefs.clearSearchHistory()
                            refreshHistory()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "Clear history",
                            tint = BareMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "CLEAR",
                            color = BareMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (query.isBlank()) {
                    "APPS · PEOPLE  ·  type to filter both  ·  Enter launches top app"
                } else {
                    "${filteredApps.size} APP  ·  ${filteredContacts.size} PEOPLE  ·  Enter → ${filteredApps.firstOrNull()?.label ?: "—"}"
                },
                color = BareMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(4.dp))

            // Two columns: LEFT apps, RIGHT people — same query, no @ prefix.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Text(
                        text = "APPS",
                        color = BareColors.NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (query.isNotBlank() && filteredApps.isEmpty()) {
                            item {
                                SearchEmptyHint("No matching apps")
                            }
                        } else {
                            lazyItems(filteredApps, key = { it.key }) { app ->
                                SearchResultRow(
                                    app = app,
                                    pinned = pinnedKeys.contains(app.key),
                                    onClick = { onLaunch(app) },
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(BareColors.NeonMagenta.copy(alpha = 0.25f)),
                )

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Text(
                        text = "PEOPLE",
                        color = BareColors.NeonMagenta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when {
                            !hasContactsPerm -> {
                                item {
                                    ContactsPermissionEmpty(
                                        onGrant = onRequestContactsPermission,
                                    )
                                }
                            }
                            query.isBlank() -> {
                                item {
                                    SearchEmptyHint("Type to find contacts")
                                }
                            }
                            filteredContacts.isEmpty() -> {
                                item {
                                    SearchEmptyHint("No matching people")
                                }
                            }
                            else -> {
                                lazyItems(
                                    filteredContacts,
                                    key = { "${it.id}|${it.number}" },
                                ) { contact ->
                                    ContactResultRow(
                                        contact = contact,
                                        onPhone = {
                                            recordPersonAndAct(
                                                contact,
                                                SearchHistoryPersonAction.Phone,
                                            )
                                        },
                                        onWhatsApp = {
                                            recordPersonAndAct(
                                                contact,
                                                SearchHistoryPersonAction.WhatsApp,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryAppChip(app: LaunchableApp, onClick: () -> Unit) {
    val bitmap = remember(app.key) {
        runCatching { app.icon.toBitmap(64, 64).asImageBitmap() }.getOrNull()
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BareColors.Surface)
            .border(
                1.dp,
                BareColors.NeonMagenta.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = app.label,
            color = BareColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )
    }
}

@Composable
private fun SearchHistoryPersonChip(item: SearchHistoryItem.Person, onClick: () -> Unit) {
    val actionLabel = when (item.action) {
        SearchHistoryPersonAction.Phone -> "TEL"
        SearchHistoryPersonAction.WhatsApp -> "WA"
    }
    val accent = when (item.action) {
        SearchHistoryPersonAction.Phone -> BareColors.NeonCyan
        SearchHistoryPersonAction.WhatsApp -> BareColors.NeonMagenta
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BareColors.Surface)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = actionLabel,
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = item.name,
            color = BareColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )
    }
}

@Composable
private fun SearchEmptyHint(message: String) {
    Text(
        text = message,
        color = BareMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
    )
}

@Composable
private fun ContactsPermissionEmpty(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Contacts permission needed",
            color = BareMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onGrant,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BareColors.NeonMagenta.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
        ) {
            Text(
                "GRANT",
                color = BareColors.NeonMagenta,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ContactResultRow(
    contact: ContactMatch,
    onPhone: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BareColors.Surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = contact.name,
            color = BareColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = contact.number,
            color = BareMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onPhone,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BareColors.NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = BareColors.NeonCyan,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "Phone",
                    color = BareColors.NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
            TextButton(
                onClick = onWhatsApp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BareColors.NeonMagenta.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
            ) {
                Text(
                    "WA",
                    color = BareColors.NeonMagenta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    app: LaunchableApp,
    pinned: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = remember(app.key) {
        runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Placeholder, RoundedCornerShape(10.dp)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = BareColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pinned) {
                Text(
                    text = "PINNED",
                    color = BareColors.NeonMagenta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
