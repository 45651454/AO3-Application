package io.github.x45651454.ao3reader.data

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

    /** 是否在浏览列表里显示 NSFW（Explicit / Mature）作品，默认开启，老用户无感知。 */
    var showNsfw: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NSFW, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_NSFW, value).apply()
        }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SHOW_NSFW = "show_nsfw"
    }
}
