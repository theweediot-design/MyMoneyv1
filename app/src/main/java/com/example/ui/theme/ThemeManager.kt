package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System Default")
}

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mymoney_theme", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
    )
    val currentTheme: StateFlow<AppThemeMode> = _currentTheme.asStateFlow()

    fun setTheme(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _currentTheme.value = mode
    }
}
