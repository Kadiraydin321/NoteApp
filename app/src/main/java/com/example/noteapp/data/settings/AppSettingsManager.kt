package com.example.noteapp.data.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class WidgetFilterMode {
    ALL,
    FAVORITES
}

data class AppSettings(
    val widgetFilterMode: WidgetFilterMode = WidgetFilterMode.ALL,
    val widgetShowLockedNotes: Boolean = false,
    val defaultNoteColor: Int = 0,
    val dynamicColor: Boolean = true
)

@Singleton
class AppSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val filterModeStr = prefs.getString(KEY_WIDGET_FILTER_MODE, WidgetFilterMode.ALL.name) ?: WidgetFilterMode.ALL.name
        val filterMode = try {
            WidgetFilterMode.valueOf(filterModeStr)
        } catch (_: Exception) {
            WidgetFilterMode.ALL
        }

        val showLocked = prefs.getBoolean(KEY_WIDGET_SHOW_LOCKED, false)
        val defaultColor = prefs.getInt(KEY_DEFAULT_COLOR, 0)
        val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

        return AppSettings(
            widgetFilterMode = filterMode,
            widgetShowLockedNotes = showLocked,
            defaultNoteColor = defaultColor,
            dynamicColor = dynamicColor
        )
    }

    fun setWidgetFilterMode(mode: WidgetFilterMode) {
        prefs.edit().putString(KEY_WIDGET_FILTER_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(widgetFilterMode = mode)
    }

    fun setWidgetShowLockedNotes(show: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_LOCKED, show).apply()
        _settings.value = _settings.value.copy(widgetShowLockedNotes = show)
    }

    fun setDefaultNoteColor(color: Int) {
        prefs.edit().putInt(KEY_DEFAULT_COLOR, color).apply()
        _settings.value = _settings.value.copy(defaultNoteColor = color)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }

    companion object {
        private const val KEY_WIDGET_FILTER_MODE = "widget_filter_mode"
        private const val KEY_WIDGET_SHOW_LOCKED = "widget_show_locked"
        private const val KEY_DEFAULT_COLOR = "default_note_color"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"

        // Helper for Widget Service to access synchronously without injection
        fun getWidgetFilterMode(context: Context): WidgetFilterMode {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val str = prefs.getString(KEY_WIDGET_FILTER_MODE, WidgetFilterMode.ALL.name) ?: WidgetFilterMode.ALL.name
            return try { WidgetFilterMode.valueOf(str) } catch (_: Exception) { WidgetFilterMode.ALL }
        }

        fun getWidgetShowLocked(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_WIDGET_SHOW_LOCKED, false)
        }
    }
}
