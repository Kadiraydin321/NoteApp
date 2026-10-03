package com.example.noteapp.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.noteapp.data.security.NoteCryptoManager
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

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class NotesLayoutMode {
    STAGGERED_GRID, // 2 sütunlu estetik grid
    LIST,           // Geniş detaylı kart listesi
    COMPACT_LIST    // Kompakt tek satırlı hızlı liste
}

data class AppSettings(
    val widgetFilterMode: WidgetFilterMode = WidgetFilterMode.ALL,
    val widgetShowLockedNotes: Boolean = false,
    val widgetShowContent: Boolean = true,
    val defaultNoteColor: Int = 0,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val layoutMode: NotesLayoutMode = NotesLayoutMode.STAGGERED_GRID,
    val masterPin: String? = null,
    val autoLockOnExit: Boolean = true,
    val highContrastNegative: Boolean = true,
    val lastBackupTimestamp: Long? = null,
    val noteFontSize: Float = 16f
)

@Singleton
class AppSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: NoteCryptoManager
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

        val themeModeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        val layoutModeStr = prefs.getString(KEY_LAYOUT_MODE, NotesLayoutMode.STAGGERED_GRID.name) ?: NotesLayoutMode.STAGGERED_GRID.name
        val layoutMode = try {
            NotesLayoutMode.valueOf(layoutModeStr)
        } catch (_: Exception) {
            NotesLayoutMode.STAGGERED_GRID
        }

        val showLocked = prefs.getBoolean(KEY_WIDGET_SHOW_LOCKED, false)
        val widgetShowContent = prefs.getBoolean(KEY_WIDGET_SHOW_CONTENT, true)
        val defaultColor = prefs.getInt(KEY_DEFAULT_COLOR, 0)
        val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        val storedPin = prefs.getString(KEY_MASTER_PIN, null)
        val masterPin = when {
            storedPin.isNullOrBlank() -> null
            cryptoManager.isEncrypted(storedPin) -> cryptoManager.decrypt(storedPin).takeUnless { it.startsWith("[Korumalı") }
            else -> runCatching {
                prefs.edit().putString(KEY_MASTER_PIN, cryptoManager.encrypt(storedPin)).apply()
                storedPin
            }.getOrElse {
                prefs.edit().remove(KEY_MASTER_PIN).apply()
                null
            }
        }
        val autoLock = prefs.getBoolean(KEY_AUTO_LOCK, true)
        val highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, true)
        val lastBackup = if (prefs.contains(KEY_LAST_BACKUP)) prefs.getLong(KEY_LAST_BACKUP, 0L) else null

        val noteFontSize = prefs.getFloat(KEY_NOTE_FONT_SIZE, 16f).coerceIn(12f, 24f)

        return AppSettings(
            widgetFilterMode = filterMode,
            widgetShowLockedNotes = showLocked,
            widgetShowContent = widgetShowContent,
            defaultNoteColor = defaultColor,
            dynamicColor = dynamicColor,
            themeMode = themeMode,
            layoutMode = layoutMode,
            masterPin = masterPin,
            autoLockOnExit = autoLock,
            highContrastNegative = highContrast,
            lastBackupTimestamp = lastBackup,
            noteFontSize = noteFontSize
        )
    }

    fun setNoteFontSize(size: Float) {
        val clamped = size.coerceIn(12f, 24f)
        prefs.edit().putFloat(KEY_NOTE_FONT_SIZE, clamped).apply()
        _settings.value = _settings.value.copy(noteFontSize = clamped)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setLayoutMode(mode: NotesLayoutMode) {
        prefs.edit().putString(KEY_LAYOUT_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(layoutMode = mode)
    }

    fun setMasterPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            prefs.edit().remove(KEY_MASTER_PIN).apply()
            _settings.value = _settings.value.copy(masterPin = null)
        } else {
            prefs.edit().putString(KEY_MASTER_PIN, cryptoManager.encrypt(pin)).apply()
            _settings.value = _settings.value.copy(masterPin = pin)
        }
    }

    fun setAutoLockOnExit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()
        _settings.value = _settings.value.copy(autoLockOnExit = enabled)
    }

    fun setHighContrastNegative(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _settings.value = _settings.value.copy(highContrastNegative = enabled)
    }

    fun setLastBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, timestamp).apply()
        _settings.value = _settings.value.copy(lastBackupTimestamp = timestamp)
    }

    fun setWidgetFilterMode(mode: WidgetFilterMode) {
        prefs.edit().putString(KEY_WIDGET_FILTER_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(widgetFilterMode = mode)
    }

    fun setWidgetShowLockedNotes(show: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_LOCKED, show).apply()
        _settings.value = _settings.value.copy(widgetShowLockedNotes = show)
    }

    fun setWidgetShowContent(show: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_CONTENT, show).apply()
        _settings.value = _settings.value.copy(widgetShowContent = show)
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
        private const val KEY_WIDGET_SHOW_CONTENT = "widget_show_content"
        private const val KEY_DEFAULT_COLOR = "default_note_color"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val KEY_MASTER_PIN = "master_pin"
        private const val KEY_AUTO_LOCK = "auto_lock_on_exit"
        private const val KEY_HIGH_CONTRAST = "high_contrast_negative"
        private const val KEY_LAST_BACKUP = "last_backup_timestamp"
        private const val KEY_NOTE_FONT_SIZE = "key_note_font_size"

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

        fun getWidgetShowContent(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_WIDGET_SHOW_CONTENT, true)
        }

        fun getThemeMode(context: Context): ThemeMode {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val str = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            return try { ThemeMode.valueOf(str) } catch (_: Exception) { ThemeMode.SYSTEM }
        }

        fun isWidgetDarkTheme(context: Context): Boolean {
            val mode = getThemeMode(context)
            val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            return when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isNight
            }
        }
    }
}
