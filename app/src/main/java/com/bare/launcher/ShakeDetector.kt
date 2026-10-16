package com.bare.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Accelerometer shake → callback. Cooldown avoids fighting rapid toggles /
 * the dock flashlight icon (both share [FlashlightController]).
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var listening = false

    private var lastShakeAtMs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastSampleAtMs = 0L
    private var primed = false

    fun hasAccelerometer(): Boolean = accelerometer != null

    fun start() {
        if (listening) return
        val sm = sensorManager ?: return
        val sensor = accelerometer ?: return
        listening = sm.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (listening) {
            primed = false
            lastSampleAtMs = 0L
        }
    }

    fun stop() {
        if (!listening) return
        runCatching { sensorManager?.unregisterListener(this) }
        listening = false
        primed = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = SystemClock.elapsedRealtime()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (!primed) {
            lastX = x
            lastY = y
            lastZ = z
            lastSampleAtMs = now
            primed = true
            return
        }
        val dt = now - lastSampleAtMs
        if (dt < MIN_SAMPLE_MS) return
        lastSampleAtMs = now

        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        lastX = x
        lastY = y
        lastZ = z

        val delta = sqrt(dx * dx + dy * dy + dz * dz)
        if (delta < SHAKE_DELTA) return
        if (now - lastShakeAtMs < COOLDOWN_MS) return
        lastShakeAtMs = now
        onShake()
    }

    companion object {
        /** g-force-ish delta between samples (tuned for pocket/phone shake). */
        private const val SHAKE_DELTA = 13.5f
        private const val COOLDOWN_MS = 900L
        private const val MIN_SAMPLE_MS = 40L
    }
}
