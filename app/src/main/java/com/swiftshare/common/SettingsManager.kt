package com.swiftshare.common

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode(val code: String, val titleEn: String, val titleAr: String) {
    SYSTEM("system", "Follow System", "حسب النظام"),
    DARK("dark", "Dark Mode", "الوضع الداكن"),
    LIGHT("light", "Light Mode", "الوضع الفاتح")
}

object SettingsManager {
    private const val PREFS_NAME = "swiftshare_app_settings"

    private const val KEY_THEME = "theme_mode"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_DESTINATION_PATH = "custom_destination_path"

    var customPrefsForTest: SharedPreferences? = null
    private var initialized = false

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _destinationPath = MutableStateFlow(getDefaultDestinationPath())
    val destinationPath: StateFlow<String> = _destinationPath.asStateFlow()

    fun init(context: Context?) {
        val prefs = getPrefs(context)
        val savedTheme = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.code) ?: ThemeMode.SYSTEM.code
        _themeMode.value = when (savedTheme) {
            ThemeMode.DARK.code -> ThemeMode.DARK
            ThemeMode.LIGHT.code -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
        _notificationsEnabled.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        _soundEnabled.value = prefs.getBoolean(KEY_SOUND, true)
        _destinationPath.value = prefs.getString(KEY_DESTINATION_PATH, getDefaultDestinationPath()) ?: getDefaultDestinationPath()
        initialized = true
    }

    private fun getPrefs(context: Context?): SharedPreferences {
        customPrefsForTest?.let { return it }
        requireNotNull(context) { "Context cannot be null when customPrefsForTest is not provided" }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setThemeMode(context: Context?, mode: ThemeMode) {
        _themeMode.value = mode
        getPrefs(context).edit().putString(KEY_THEME, mode.code).apply()
    }

    fun setNotificationsEnabled(context: Context?, enabled: Boolean) {
        _notificationsEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun setSoundEnabled(context: Context?, enabled: Boolean) {
        _soundEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setDestinationPath(context: Context?, path: String) {
        val f = File(path)
        if (!f.exists()) {
            f.mkdirs()
        }
        _destinationPath.value = f.absolutePath
        getPrefs(context).edit().putString(KEY_DESTINATION_PATH, f.absolutePath).apply()
    }

    fun getDefaultDestinationPath(): String {
        return try {
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(pub, "SwiftShare").absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0/Download/SwiftShare"
        }
    }
}

