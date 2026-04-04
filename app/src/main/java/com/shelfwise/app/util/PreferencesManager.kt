package com.shelfwise.app.util

import android.content.Context
import android.content.SharedPreferences
import com.shelfwise.app.data.model.SortOrder

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shelfwise_prefs", Context.MODE_PRIVATE)

    var sortOrder: SortOrder
        get() = try {
            SortOrder.valueOf(prefs.getString(KEY_SORT_ORDER, SortOrder.RECENT.name)!!)
        } catch (_: Exception) {
            SortOrder.RECENT
        }
        set(value) = prefs.edit().putString(KEY_SORT_ORDER, value.name).apply()

    var isGridView: Boolean
        get() = prefs.getBoolean(KEY_GRID_VIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_GRID_VIEW, value).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    var readerFont: String
        get() = prefs.getString(KEY_READER_FONT, "literata") ?: "literata"
        set(value) = prefs.edit().putString(KEY_READER_FONT, value).apply()

    var readerFontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 18)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value).apply()

    var readerLineSpacing: Float
        get() = prefs.getFloat(KEY_LINE_SPACING, 1.6f)
        set(value) = prefs.edit().putFloat(KEY_LINE_SPACING, value).apply()

    var readerMargins: Int
        get() = prefs.getInt(KEY_MARGINS, 16)
        set(value) = prefs.edit().putInt(KEY_MARGINS, value).apply()

    var readerTheme: String
        get() = prefs.getString(KEY_READER_THEME, "light") ?: "light"
        set(value) = prefs.edit().putString(KEY_READER_THEME, value).apply()

    var blueLightFilter: Boolean
        get() = prefs.getBoolean(KEY_BLUE_LIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUE_LIGHT, value).apply()

    var blueLightIntensity: Int
        get() = prefs.getInt(KEY_BLUE_LIGHT_INTENSITY, 30)
        set(value) = prefs.edit().putInt(KEY_BLUE_LIGHT_INTENSITY, value).apply()

    var noAnimations: Boolean
        get() = prefs.getBoolean(KEY_NO_ANIMATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NO_ANIMATIONS, value).apply()

    var lowMemoryMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_MEMORY, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_MEMORY, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var serverAutoStopMinutes: Int
        get() = prefs.getInt(KEY_AUTO_STOP, 30)
        set(value) = prefs.edit().putInt(KEY_AUTO_STOP, value).apply()

    fun getTreeUris(): Set<String> {
        return prefs.getStringSet(KEY_TREE_URIS, emptySet()) ?: emptySet()
    }

    fun addTreeUri(uri: String) {
        val uris = getTreeUris().toMutableSet()
        uris.add(uri)
        prefs.edit().putStringSet(KEY_TREE_URIS, uris).apply()
    }

    fun removeTreeUri(uri: String) {
        val uris = getTreeUris().toMutableSet()
        uris.remove(uri)
        prefs.edit().putStringSet(KEY_TREE_URIS, uris).apply()
    }

    companion object {
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2

        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_GRID_VIEW = "grid_view"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_READER_FONT = "reader_font"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_MARGINS = "margins"
        private const val KEY_READER_THEME = "reader_theme"
        private const val KEY_BLUE_LIGHT = "blue_light"
        private const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
        private const val KEY_NO_ANIMATIONS = "no_animations"
        private const val KEY_LOW_MEMORY = "low_memory"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_AUTO_STOP = "auto_stop"
        private const val KEY_TREE_URIS = "tree_uris"
    }
}
