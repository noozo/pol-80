package com.bare.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/** Torch toggle via CameraManager.setTorchMode. */
class FlashlightController(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    @Volatile
    private var torchCameraId: String? = null

    @Volatile
    var isOn: Boolean = false
        private set

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasFlashUnit(): Boolean {
        return try {
            resolveTorchCameraId() != null
        } catch (_: Exception) {
            false
        }
    }

    /** Returns new torch state, or null if unavailable / no permission. */
    fun toggle(): Boolean? {
        if (!hasCameraPermission()) return null
        val cm = cameraManager ?: return null
        val id = try {
            resolveTorchCameraId()
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val next = !isOn
            cm.setTorchMode(id, next)
            isOn = next
            next
        } catch (_: Exception) {
            // Camera may have been taken; try to recover by forcing off.
            runCatching { cm.setTorchMode(id, false) }
            isOn = false
            false
        }
    }

    fun turnOff() {
        val cm = cameraManager ?: return
        val id = torchCameraId ?: try {
            resolveTorchCameraId()
        } catch (_: Exception) {
            null
        } ?: return
        runCatching {
            cm.setTorchMode(id, false)
            isOn = false
        }
    }

    private fun resolveTorchCameraId(): String? {
        torchCameraId?.let { return it }
        val cm = cameraManager ?: return null
        val ids = try {
            cm.cameraIdList
        } catch (_: Exception) {
            return null
        }
        for (id in ids) {
            val chars = try {
                cm.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (flash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                torchCameraId = id
                return id
            }
        }
        for (id in ids) {
            val chars = try {
                cm.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }
            if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                torchCameraId = id
                return id
            }
        }
        return null
    }
}
