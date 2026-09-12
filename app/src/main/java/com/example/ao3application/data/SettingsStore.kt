package com.example.ao3application.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 界面偏好的落盘处。跟收藏/历史那些业务数据分开，不进 library.db。 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) }
            ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
