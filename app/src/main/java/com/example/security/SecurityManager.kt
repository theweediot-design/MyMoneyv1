package com.example.security

import android.content.Context
import android.content.SharedPreferences

class SecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mymoney_security", Context.MODE_PRIVATE)

    var pin: String?
        get() = prefs.getString("user_pin", null)
        set(value) = prefs.edit().putString("user_pin", value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", true)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

    var autoLockTimer: String
        get() = prefs.getString("auto_lock_timer", "1 minute") ?: "1 minute"
        set(value) = prefs.edit().putString("auto_lock_timer", value).apply()

    var isHideAmountsInRecentApps: Boolean
        get() = prefs.getBoolean("hide_amounts_recent", false)
        set(value) = prefs.edit().putBoolean("hide_amounts_recent", value).apply()

    val isPinSet: Boolean
        get() = !pin.isNullOrBlank()

    fun verifyPin(enteredPin: String): Boolean {
        return pin != null && pin == enteredPin
    }

    fun removePin() {
        prefs.edit().remove("user_pin").apply()
    }
}
