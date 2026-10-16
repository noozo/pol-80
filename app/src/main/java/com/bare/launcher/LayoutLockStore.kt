package com.bare.launcher

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Layout-edit lock: enabled flag + PIN hash in EncryptedSharedPreferences.
 * Biometric is offered at unlock time when the device has enrolled biometrics;
 * success is session-only (caller clears on leaving edit mode).
 */
class LayoutLockStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { openEncryptedPrefs() }

    private fun openEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Fallback if Keystore/crypto unavailable (emulator edge cases).
            appContext.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false) && hasPin()

    fun hasPin(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Store a new PIN (4–8 digits). Enables lock. */
    fun setPin(pin: String): Boolean {
        val normalized = pin.trim()
        if (!isValidPinFormat(normalized)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(normalized, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = try {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return false
        }
        val actual = hashPin(pin.trim(), salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "bare_layout_lock_enc"
        private const val PREFS_NAME_FALLBACK = "bare_layout_lock_fb"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"

        fun isValidPinFormat(pin: String): Boolean =
            pin.length in 4..8 && pin.all { it.isDigit() }

        private fun hashPin(pin: String, salt: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(pin.toByteArray(Charsets.UTF_8))
            var dig = md.digest()
            repeat(999) {
                val round = MessageDigest.getInstance("SHA-256")
                round.update(dig)
                round.update(salt)
                dig = round.digest()
            }
            return Base64.encodeToString(dig, Base64.NO_WRAP)
        }
    }
}
