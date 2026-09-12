package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import java.security.MessageDigest

enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    UNSUPPORTED
}

class SecurityManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mymoney_security", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "user_pin_hash"
        private const val KEY_PIN_LEGACY = "user_pin"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_TIMER = "auto_lock_timer"
        private const val KEY_HIDE_AMOUNTS = "hide_amounts_recent"
        private const val PIN_SALT = "MyMoney_Sec_Salt_v1"
    }

    init {
        migrateLegacyPin()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "$pin:$PIN_SALT"
        val hashBytes = digest.digest(salted.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun migrateLegacyPin() {
        val legacyPin = prefs.getString(KEY_PIN_LEGACY, null)
        if (!legacyPin.isNullOrBlank()) {
            val hashed = hashPin(legacyPin)
            prefs.edit()
                .putString(KEY_PIN_HASH, hashed)
                .remove(KEY_PIN_LEGACY)
                .apply()
        }
    }

    var pin: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) {
            if (value.isNullOrBlank()) {
                removePin()
            } else {
                val hashed = hashPin(value)
                prefs.edit().putString(KEY_PIN_HASH, hashed).apply()
            }
        }

    val isPinSet: Boolean
        get() = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return storedHash == hashPin(enteredPin)
    }

    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_LEGACY)
            .apply()
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var autoLockTimer: String
        get() = prefs.getString(KEY_AUTO_LOCK_TIMER, "1 minute") ?: "1 minute"
        set(value) = prefs.edit().putString(KEY_AUTO_LOCK_TIMER, value).apply()

    fun getAutoLockTimeoutMillis(): Long {
        return when (autoLockTimer) {
            "1 minute" -> 60_000L
            "5 minutes" -> 300_000L
            "15 minutes" -> 900_000L
            "Never" -> Long.MAX_VALUE
            else -> 60_000L
        }
    }

    var isHideAmountsInRecentApps: Boolean
        get() = prefs.getBoolean(KEY_HIDE_AMOUNTS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_AMOUNTS, value).apply()

    fun checkBiometricAvailability(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HW_UNAVAILABLE
            else -> BiometricAvailability.UNSUPPORTED
        }
    }
}
