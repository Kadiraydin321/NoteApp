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

enum class WidgetDesignPreset(
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val darkBgDrawable: Int,
    val lightBgDrawable: Int,
    val fabDrawable: Int,
    val headerTextColorDark: Long,
    val headerTextColorLight: Long,
    val accentColorDark: Long,
    val accentColorLight: Long,
    val itemBgColorDark: Long,
    val itemBgColorLight: Long,
    val itemTitleColorDark: Long,
    val itemTitleColorLight: Long,
    val itemContentColorDark: Long,
    val itemContentColorLight: Long
) {
    MODERN_RED(
        title = "Modern Kırmızı",
        subtitle = "Tutkulu kırmızı detaylar & koyu mat zemin",
        iconEmoji = "🔴",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_modern_red_dark,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_modern_red_light,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_modern_red,
        headerTextColorDark = 0xFFFFFFFF,
        headerTextColorLight = 0xFF1C1B1F,
        accentColorDark = 0xFFE53935,
        accentColorLight = 0xFFD32F2F,
        itemBgColorDark = 0xFF202128,
        itemBgColorLight = 0xFFFFFFFF,
        itemTitleColorDark = 0xFFFFFFFF,
        itemTitleColorLight = 0xFF1C1B1F,
        itemContentColorDark = 0xFFC5C6D0,
        itemContentColorLight = 0xFF49454F
    ),
    FROSTED_GLASS(
        title = "Buzlu Cam",
        subtitle = "Yarı saydam akrilik & estetik cam dokusu",
        iconEmoji = "❄️",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_glass_dark,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_glass_light,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_glass,
        headerTextColorDark = 0xFFFFFFFF,
        headerTextColorLight = 0xFF0D1B2A,
        accentColorDark = 0xFF60A5FA,
        accentColorLight = 0xFF2563EB,
        itemBgColorDark = 0xFF222B3A,
        itemBgColorLight = 0xFFF1F5F9,
        itemTitleColorDark = 0xFFFFFFFF,
        itemTitleColorLight = 0xFF0F172A,
        itemContentColorDark = 0xFFCBD5E1,
        itemContentColorLight = 0xFF475569
    ),
    PURE_BLACK(
        title = "Saf Siyah (AMOLED)",
        subtitle = "Sıfır pil tüketimi, derin siyah & net çizgiler",
        iconEmoji = "🖤",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_pure_black,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_pure_black,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_pure_black,
        headerTextColorDark = 0xFFFFFFFF,
        headerTextColorLight = 0xFFFFFFFF,
        accentColorDark = 0xFFE0E0E0,
        accentColorLight = 0xFFE0E0E0,
        itemBgColorDark = 0xFF121212,
        itemBgColorLight = 0xFF121212,
        itemTitleColorDark = 0xFFFFFFFF,
        itemTitleColorLight = 0xFFFFFFFF,
        itemContentColorDark = 0xFFA0A0A0,
        itemContentColorLight = 0xFFA0A0A0
    ),
    VINTAGE_WARM(
        title = "Vintage Defter",
        subtitle = "Sıcak parşömen kâğıdı & klasik kahve tonları",
        iconEmoji = "📜",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_vintage_dark,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_vintage_light,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_vintage,
        headerTextColorDark = 0xFFFBE9D0,
        headerTextColorLight = 0xFF3D2614,
        accentColorDark = 0xFFDDA15E,
        accentColorLight = 0xFFB06520,
        itemBgColorDark = 0xFF2A2016,
        itemBgColorLight = 0xFFFFF9EE,
        itemTitleColorDark = 0xFFFBE9D0,
        itemTitleColorLight = 0xFF3D2614,
        itemContentColorDark = 0xFFD6C5B4,
        itemContentColorLight = 0xFF695444
    ),
    NEON_OBSIDIAN(
        title = "Neon Siberpunk",
        subtitle = "Karanlık obsidyen & parlak neon mavi elektrik",
        iconEmoji = "⚡",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_neon_dark,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_neon_dark,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_neon,
        headerTextColorDark = 0xFFE0F7FA,
        headerTextColorLight = 0xFFE0F7FA,
        accentColorDark = 0xFF00E5FF,
        accentColorLight = 0xFF00E5FF,
        itemBgColorDark = 0xFF0E1726,
        itemBgColorLight = 0xFF0E1726,
        itemTitleColorDark = 0xFFE0F7FA,
        itemTitleColorLight = 0xFFE0F7FA,
        itemContentColorDark = 0xFF80DEEA,
        itemContentColorLight = 0xFF80DEEA
    ),
    MINIMAL_TRANSPARENT(
        title = "Şeffaf Minimalist",
        subtitle = "Duvar kâğıdını gösteren saydam cam zemin",
        iconEmoji = "🪟",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_transparent,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_transparent,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_transparent,
        headerTextColorDark = 0xFFFFFFFF,
        headerTextColorLight = 0xFFFFFFFF,
        accentColorDark = 0xFFFFFFFF,
        accentColorLight = 0xFFFFFFFF,
        itemBgColorDark = 0x4D000000,
        itemBgColorLight = 0x4D000000,
        itemTitleColorDark = 0xFFFFFFFF,
        itemTitleColorLight = 0xFFFFFFFF,
        itemContentColorDark = 0xFFE0E0E0,
        itemContentColorLight = 0xFFE0E0E0
    ),
    ICE_GLACIAL(
        title = "Buz Mavisi & Cam",
        subtitle = "Yarı saydam arktik cam & ışıltılı gök mavisi",
        iconEmoji = "🧊",
        darkBgDrawable = com.example.noteapp.R.drawable.widget_bg_ice_glass_dark,
        lightBgDrawable = com.example.noteapp.R.drawable.widget_bg_ice_glass_light,
        fabDrawable = com.example.noteapp.R.drawable.widget_fab_ice_glass,
        headerTextColorDark = 0xFFF0F9FF,
        headerTextColorLight = 0xFF082F49,
        accentColorDark = 0xFF38BDF8,
        accentColorLight = 0xFF0284C7,
        itemBgColorDark = 0x99102436,
        itemBgColorLight = 0xB8FFFFFF,
        itemTitleColorDark = 0xFFF0F9FF,
        itemTitleColorLight = 0xFF082F49,
        itemContentColorDark = 0xFFBAE6FD,
        itemContentColorLight = 0xFF334E68
    )
}

enum class WidgetFabBehavior(
    val title: String,
    val description: String,
    val iconEmoji: String,
    val badgeText: String
) {
    DOCK_TOOLBAR(
        title = "Sabit Hızlı Araç Çubuğu (Önerilen)",
        description = "Aç-kapa beklemeden her an tıklanabilir 5 buton: Metin, Liste, Ses, Görsel ve Çizim doğrudan widget üzerinde!",
        iconEmoji = "⚡",
        badgeText = "Hızlı Araç Çubuğu (5 Buton)"
    ),
    DIRECT_NEW_NOTE(
        title = "Doğrudan Standart Metin Notu",
        description = "Tek dokunuşta doğrudan standart metin editörünü açar (0 gecikme)",
        iconEmoji = "📝",
        badgeText = "Tek Dokunuş: Metin Notu"
    ),
    DIRECT_TODO(
        title = "Doğrudan Yapılacaklar / Görev Listesi",
        description = "Tek dokunuşta doğrudan kontrol listesi (checklist) modunu açar",
        iconEmoji = "📋",
        badgeText = "Tek Dokunuş: Yapılacaklar Listesi"
    ),
    DIRECT_VOICE(
        title = "Doğrudan Sesli Not Kaydı",
        description = "Tek dokunuşta doğrudan ses kayıt modunu başlatır",
        iconEmoji = "🎙️",
        badgeText = "Tek Dokunuş: Sesli Not"
    ),
    DIRECT_IMAGE(
        title = "Doğrudan Fotoğraf / Galeri Notu",
        description = "Tek dokunuşta doğrudan fotoğraf çekme veya galeri seçimini açar",
        iconEmoji = "📷",
        badgeText = "Tek Dokunuş: Fotoğraf Notu"
    ),
    DIRECT_DRAW(
        title = "Doğrudan Çizim / Karalama Tuvali",
        description = "Tek dokunuşta doğrudan serbest çizim ve not tuvalini açar",
        iconEmoji = "🎨",
        badgeText = "Tek Dokunuş: Çizim Notu"
    ),
    POPUP_MENU(
        title = "Açılır Seçenek Menüsü (Klasik)",
        description = "+ butonuna basıldığında açılan mini Keep tarzı seçenek menüsü",
        iconEmoji = "🗂️",
        badgeText = "Açılır Mini Menü"
    )
}

enum class WidgetCornerRadius(val title: String, val radiusDp: Int) {
    LARGE("Modern Oval", 24),
    MEDIUM("Standart", 16),
    SHARP("Keskin Hatlar", 8)
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

enum class DrawerCollapseMode(val title: String, val description: String) {
    AUTO_ACCORDION("Otomatik Akordiyon (5+ ise)", "5'ten fazla öge olduğunda başlık akordiyon şeklinde katlanır"),
    INLINE_EXPAND("Kompakt (İlk 5 + Fazlası)", "İlk 5 öge listelenir, fazlası 'Daha Fazla' butonuyla açılır"),
    ALWAYS_ACCORDION("Her Zaman Akordiyon", "Tüm kategori ve etiket başlıkları katlanabilir akordiyon olur"),
    EXPANDED_ALL("Tümünü Göster (Klasik)", "Hiçbir daraltma olmadan tüm liste doğrudan açık durur")
}

enum class TrashRetentionPolicy(val days: Int, val title: String, val description: String) {
    DAYS_30(30, "30 Gün (Önerilen)", "30 günden eski silinmiş notlar otomatik temizlenir"),
    DAYS_14(14, "14 Gün", "14 günden eski silinmiş notlar otomatik temizlenir"),
    DAYS_7(7, "7 Gün", "7 günden eski silinmiş notlar otomatik temizlenir"),
    NEVER(0, "Asla (Manuel)", "Silinen notlar sadece siz çöp kutusunu boşalttığınızda silinir")
}

enum class LockTimeoutPolicy(val timeoutMs: Long, val title: String, val description: String) {
    IMMEDIATE(0L, "Anında (Önerilen)", "Not veya uygulama kapatıldığı anda kilitlenir"),
    ONE_MINUTE(60_000L, "1 Dakika", "Uygulamadan ayrıldıktan sonra 1 dakika boyunca kilit sormaz"),
    FIVE_MINUTES(300_000L, "5 Dakika", "Uygulamadan ayrıldıktan sonra 5 dakika boyunca kilit sormaz"),
    FIFTEEN_MINUTES(900_000L, "15 Dakika", "15 dakika boyunca kilit açık kalır")
}

enum class CardCornerStyle(val title: String, val radiusDp: Int) {
    ROUNDED("Modern Yuvarlak", 18),
    SHARP("Keskin & Minimal", 6),
    SQUIRCLE("Süper Oval / Sabun", 28)
}

enum class CardElevationStyle(val title: String) {
    ELEVATED("Yumuşak Gölgeli"),
    BORDERED("Zarif Çerçeveli"),
    GLASS("Yarı Saydam / Cam")
}

enum class QuickActionLocation(val title: String, val description: String) {
    DRAWER_HEADER("Sol Menü Başlığı", "Çark ve senkronizasyon simgeleri 'Not Defterim' yanında"),
    TOP_APP_BAR("Ana Ekran Üst Çubuğu", "Çark ve senkronizasyon simgeleri ana sayfa sağ üst çubuğunda")
}

enum class SearchBarShape(val title: String, val cornerDp: Int) {
    PILL("Modern Oval Hap", 24),
    ROUNDED_RECT("Kavisli Dikdörtgen", 12)
}

enum class DesignPreset(
    val title: String,
    val subtitle: String,
    val iconEmoji: String
) {
    CUSTOM("Özel Ayarlar", "Kişiselleştirilmiş tasarım kombinasyonu", "🎨"),
    MODERN_MATERIAL("Modern Defterim", "Dengeli, yumuşak kavisli ve gölgeli", "🌟"),
    MINIMAL_NOTION("Minimal & Notion", "Keskin köşeler, zarif ince çerçeveler", "💎"),
    STUDIO_GLASS("Stüdyo Cam", "Yarı saydam cam, ekstra oval modern estetik", "🔮"),
    VINTAGE_JOURNAL("Vintage Defter", "Klasik not defteri, sıcak amber ve geniş liste", "📜"),
    COMPACT_POWER("Kompakt Güç", "Yoğun bilgi, hızlı erişim ve keskin hatlar", "⚡")
}

data class AppSettings(
    val widgetFilterMode: WidgetFilterMode = WidgetFilterMode.ALL,
    val widgetShowLockedNotes: Boolean = false,
    val widgetShowContent: Boolean = true,
    val widgetDesignPreset: WidgetDesignPreset = WidgetDesignPreset.MODERN_RED,
    val widgetFabBehavior: WidgetFabBehavior = WidgetFabBehavior.DIRECT_NEW_NOTE,
    val widgetCornerRadius: WidgetCornerRadius = WidgetCornerRadius.LARGE,
    val defaultNoteColor: Int = 0,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val layoutMode: NotesLayoutMode = NotesLayoutMode.STAGGERED_GRID,
    val masterPin: String? = null,
    val autoLockOnExit: Boolean = true,
    val highContrastNegative: Boolean = true,
    val lastBackupTimestamp: Long? = null,
    val noteFontSize: Float = 16f,
    val lastSyncHost: String = "",
    val lastSyncPin: String = "",
    val colorPalette: AppColorPalette = AppColorPalette.WARM_AMBER,
    val drawerCollapseMode: DrawerCollapseMode = DrawerCollapseMode.AUTO_ACCORDION,
    val cardCornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    val cardElevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    val quickActionLocation: QuickActionLocation = QuickActionLocation.DRAWER_HEADER,
    val searchBarShape: SearchBarShape = SearchBarShape.PILL,
    val designPreset: DesignPreset = DesignPreset.MODERN_MATERIAL,
    val dailyWordGoal: Int = 300,
    val trashRetentionPolicy: TrashRetentionPolicy = TrashRetentionPolicy.DAYS_30,
    val lockTimeoutPolicy: LockTimeoutPolicy = LockTimeoutPolicy.IMMEDIATE
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
        val syncHost = prefs.getString(KEY_LAST_SYNC_HOST, "") ?: ""
        val syncPin = prefs.getString(KEY_LAST_SYNC_PIN, "") ?: ""

        val paletteStr = prefs.getString(KEY_COLOR_PALETTE, AppColorPalette.WARM_AMBER.name) ?: AppColorPalette.WARM_AMBER.name
        val colorPalette = try {
            AppColorPalette.valueOf(paletteStr)
        } catch (_: Exception) {
            AppColorPalette.WARM_AMBER
        }

        val drawerCollapseStr = prefs.getString(KEY_DRAWER_COLLAPSE_MODE, DrawerCollapseMode.AUTO_ACCORDION.name) ?: DrawerCollapseMode.AUTO_ACCORDION.name
        val drawerCollapseMode = try {
            DrawerCollapseMode.valueOf(drawerCollapseStr)
        } catch (_: Exception) {
            DrawerCollapseMode.AUTO_ACCORDION
        }

        val cardCornerStr = prefs.getString(KEY_CARD_CORNER_STYLE, CardCornerStyle.ROUNDED.name) ?: CardCornerStyle.ROUNDED.name
        val cardCornerStyle = try { CardCornerStyle.valueOf(cardCornerStr) } catch (_: Exception) { CardCornerStyle.ROUNDED }

        val cardElevationStr = prefs.getString(KEY_CARD_ELEVATION_STYLE, CardElevationStyle.BORDERED.name) ?: CardElevationStyle.BORDERED.name
        val cardElevationStyle = try { CardElevationStyle.valueOf(cardElevationStr) } catch (_: Exception) { CardElevationStyle.BORDERED }

        val quickActionLocationStr = prefs.getString(KEY_QUICK_ACTION_LOCATION, QuickActionLocation.DRAWER_HEADER.name) ?: QuickActionLocation.DRAWER_HEADER.name
        val quickActionLocation = try { QuickActionLocation.valueOf(quickActionLocationStr) } catch (_: Exception) { QuickActionLocation.DRAWER_HEADER }

        val searchBarShapeStr = prefs.getString(KEY_SEARCH_BAR_SHAPE, SearchBarShape.PILL.name) ?: SearchBarShape.PILL.name
        val searchBarShape = try { SearchBarShape.valueOf(searchBarShapeStr) } catch (_: Exception) { SearchBarShape.PILL }

        val designPresetStr = prefs.getString(KEY_DESIGN_PRESET, DesignPreset.MODERN_MATERIAL.name) ?: DesignPreset.MODERN_MATERIAL.name
        val designPreset = try { DesignPreset.valueOf(designPresetStr) } catch (_: Exception) { DesignPreset.MODERN_MATERIAL }

        val widgetDesignPresetStr = prefs.getString(KEY_WIDGET_DESIGN_PRESET, WidgetDesignPreset.MODERN_RED.name) ?: WidgetDesignPreset.MODERN_RED.name
        val widgetDesignPreset = try { WidgetDesignPreset.valueOf(widgetDesignPresetStr) } catch (_: Exception) { WidgetDesignPreset.MODERN_RED }

        val widgetFabBehaviorStr = prefs.getString(KEY_WIDGET_FAB_BEHAVIOR, WidgetFabBehavior.DOCK_TOOLBAR.name) ?: WidgetFabBehavior.DOCK_TOOLBAR.name
        val widgetFabBehavior = try { WidgetFabBehavior.valueOf(widgetFabBehaviorStr) } catch (_: Exception) { WidgetFabBehavior.DOCK_TOOLBAR }

        val widgetCornerRadiusStr = prefs.getString(KEY_WIDGET_CORNER_RADIUS, WidgetCornerRadius.LARGE.name) ?: WidgetCornerRadius.LARGE.name
        val widgetCornerRadius = try { WidgetCornerRadius.valueOf(widgetCornerRadiusStr) } catch (_: Exception) { WidgetCornerRadius.LARGE }

        val trashRetentionStr = prefs.getString(KEY_TRASH_RETENTION_POLICY, TrashRetentionPolicy.DAYS_30.name) ?: TrashRetentionPolicy.DAYS_30.name
        val trashRetentionPolicy = try { TrashRetentionPolicy.valueOf(trashRetentionStr) } catch (_: Exception) { TrashRetentionPolicy.DAYS_30 }

        val lockTimeoutStr = prefs.getString(KEY_LOCK_TIMEOUT_POLICY, LockTimeoutPolicy.IMMEDIATE.name) ?: LockTimeoutPolicy.IMMEDIATE.name
        val lockTimeoutPolicy = try { LockTimeoutPolicy.valueOf(lockTimeoutStr) } catch (_: Exception) { LockTimeoutPolicy.IMMEDIATE }

        return AppSettings(
            widgetFilterMode = filterMode,
            widgetShowLockedNotes = showLocked,
            widgetShowContent = widgetShowContent,
            widgetDesignPreset = widgetDesignPreset,
            widgetFabBehavior = widgetFabBehavior,
            widgetCornerRadius = widgetCornerRadius,
            defaultNoteColor = defaultColor,
            dynamicColor = dynamicColor,
            themeMode = themeMode,
            layoutMode = layoutMode,
            masterPin = masterPin,
            autoLockOnExit = autoLock,
            highContrastNegative = highContrast,
            lastBackupTimestamp = lastBackup,
            noteFontSize = noteFontSize,
            lastSyncHost = syncHost,
            lastSyncPin = syncPin,
            colorPalette = colorPalette,
            drawerCollapseMode = drawerCollapseMode,
            cardCornerStyle = cardCornerStyle,
            cardElevationStyle = cardElevationStyle,
            quickActionLocation = quickActionLocation,
            searchBarShape = searchBarShape,
            designPreset = designPreset,
            dailyWordGoal = prefs.getInt(KEY_DAILY_WORD_GOAL, 300),
            trashRetentionPolicy = trashRetentionPolicy,
            lockTimeoutPolicy = lockTimeoutPolicy
        )
    }

    fun setCardCornerStyle(style: CardCornerStyle) {
        prefs.edit().putString(KEY_CARD_CORNER_STYLE, style.name).putString(KEY_DESIGN_PRESET, DesignPreset.CUSTOM.name).apply()
        _settings.value = _settings.value.copy(cardCornerStyle = style, designPreset = DesignPreset.CUSTOM)
    }

    fun setCardElevationStyle(style: CardElevationStyle) {
        prefs.edit().putString(KEY_CARD_ELEVATION_STYLE, style.name).putString(KEY_DESIGN_PRESET, DesignPreset.CUSTOM.name).apply()
        _settings.value = _settings.value.copy(cardElevationStyle = style, designPreset = DesignPreset.CUSTOM)
    }

    fun setQuickActionLocation(location: QuickActionLocation) {
        prefs.edit().putString(KEY_QUICK_ACTION_LOCATION, location.name).putString(KEY_DESIGN_PRESET, DesignPreset.CUSTOM.name).apply()
        _settings.value = _settings.value.copy(quickActionLocation = location, designPreset = DesignPreset.CUSTOM)
    }

    fun setSearchBarShape(shape: SearchBarShape) {
        prefs.edit().putString(KEY_SEARCH_BAR_SHAPE, shape.name).putString(KEY_DESIGN_PRESET, DesignPreset.CUSTOM.name).apply()
        _settings.value = _settings.value.copy(searchBarShape = shape, designPreset = DesignPreset.CUSTOM)
    }

    fun applyDesignPreset(preset: DesignPreset) {
        if (preset == DesignPreset.CUSTOM) return

        val corner: CardCornerStyle
        val elevation: CardElevationStyle
        val quickLoc: QuickActionLocation
        val searchShape: SearchBarShape
        val layout: NotesLayoutMode
        val palette: AppColorPalette
        val collapse: DrawerCollapseMode

        when (preset) {
            DesignPreset.MODERN_MATERIAL -> {
                corner = CardCornerStyle.ROUNDED
                elevation = CardElevationStyle.BORDERED
                quickLoc = QuickActionLocation.DRAWER_HEADER
                searchShape = SearchBarShape.PILL
                layout = NotesLayoutMode.STAGGERED_GRID
                palette = AppColorPalette.WARM_AMBER
                collapse = DrawerCollapseMode.AUTO_ACCORDION
            }
            DesignPreset.MINIMAL_NOTION -> {
                corner = CardCornerStyle.SHARP
                elevation = CardElevationStyle.BORDERED
                quickLoc = QuickActionLocation.TOP_APP_BAR
                searchShape = SearchBarShape.ROUNDED_RECT
                layout = NotesLayoutMode.LIST
                palette = AppColorPalette.SLATE_MINIMAL
                collapse = DrawerCollapseMode.INLINE_EXPAND
            }
            DesignPreset.STUDIO_GLASS -> {
                corner = CardCornerStyle.SQUIRCLE
                elevation = CardElevationStyle.GLASS
                quickLoc = QuickActionLocation.DRAWER_HEADER
                searchShape = SearchBarShape.PILL
                layout = NotesLayoutMode.STAGGERED_GRID
                palette = AppColorPalette.MIDNIGHT_ROSE
                collapse = DrawerCollapseMode.ALWAYS_ACCORDION
            }
            DesignPreset.VINTAGE_JOURNAL -> {
                corner = CardCornerStyle.ROUNDED
                elevation = CardElevationStyle.ELEVATED
                quickLoc = QuickActionLocation.DRAWER_HEADER
                searchShape = SearchBarShape.PILL
                layout = NotesLayoutMode.LIST
                palette = AppColorPalette.WARM_AMBER
                collapse = DrawerCollapseMode.EXPANDED_ALL
            }
            DesignPreset.COMPACT_POWER -> {
                corner = CardCornerStyle.SHARP
                elevation = CardElevationStyle.BORDERED
                quickLoc = QuickActionLocation.TOP_APP_BAR
                searchShape = SearchBarShape.ROUNDED_RECT
                layout = NotesLayoutMode.COMPACT_LIST
                palette = AppColorPalette.DEEP_OCEAN
                collapse = DrawerCollapseMode.AUTO_ACCORDION
            }
            DesignPreset.CUSTOM -> return
        }

        prefs.edit()
            .putString(KEY_CARD_CORNER_STYLE, corner.name)
            .putString(KEY_CARD_ELEVATION_STYLE, elevation.name)
            .putString(KEY_QUICK_ACTION_LOCATION, quickLoc.name)
            .putString(KEY_SEARCH_BAR_SHAPE, searchShape.name)
            .putString(KEY_LAYOUT_MODE, layout.name)
            .putString(KEY_COLOR_PALETTE, palette.name)
            .putString(KEY_DRAWER_COLLAPSE_MODE, collapse.name)
            .putString(KEY_DESIGN_PRESET, preset.name)
            .apply()

        _settings.value = _settings.value.copy(
            cardCornerStyle = corner,
            cardElevationStyle = elevation,
            quickActionLocation = quickLoc,
            searchBarShape = searchShape,
            layoutMode = layout,
            colorPalette = palette,
            drawerCollapseMode = collapse,
            designPreset = preset
        )
    }

    fun setDrawerCollapseMode(mode: DrawerCollapseMode) {
        prefs.edit().putString(KEY_DRAWER_COLLAPSE_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(drawerCollapseMode = mode)
    }

    fun setColorPalette(palette: AppColorPalette) {
        prefs.edit().putString(KEY_COLOR_PALETTE, palette.name).apply()
        _settings.value = _settings.value.copy(colorPalette = palette)
    }

    fun setLastSyncHost(host: String) {
        prefs.edit().putString(KEY_LAST_SYNC_HOST, host).apply()
        _settings.value = _settings.value.copy(lastSyncHost = host)
    }

    fun setLastSyncPin(pin: String) {
        prefs.edit().putString(KEY_LAST_SYNC_PIN, pin).apply()
        _settings.value = _settings.value.copy(lastSyncPin = pin)
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

    fun setWidgetDesignPreset(preset: WidgetDesignPreset) {
        prefs.edit().putString(KEY_WIDGET_DESIGN_PRESET, preset.name).apply()
        _settings.value = _settings.value.copy(widgetDesignPreset = preset)
    }

    fun setWidgetFabBehavior(behavior: WidgetFabBehavior) {
        prefs.edit().putString(KEY_WIDGET_FAB_BEHAVIOR, behavior.name).apply()
        _settings.value = _settings.value.copy(widgetFabBehavior = behavior)
    }

    fun setWidgetCornerRadius(radius: WidgetCornerRadius) {
        prefs.edit().putString(KEY_WIDGET_CORNER_RADIUS, radius.name).apply()
        _settings.value = _settings.value.copy(widgetCornerRadius = radius)
    }

    fun setDailyWordGoal(goal: Int) {
        prefs.edit().putInt(KEY_DAILY_WORD_GOAL, goal).apply()
        _settings.value = _settings.value.copy(dailyWordGoal = goal)
    }

    fun setTrashRetentionPolicy(policy: TrashRetentionPolicy) {
        prefs.edit().putString(KEY_TRASH_RETENTION_POLICY, policy.name).apply()
        _settings.value = _settings.value.copy(trashRetentionPolicy = policy)
    }

    fun setLockTimeoutPolicy(policy: LockTimeoutPolicy) {
        prefs.edit().putString(KEY_LOCK_TIMEOUT_POLICY, policy.name).apply()
        _settings.value = _settings.value.copy(lockTimeoutPolicy = policy)
    }

    companion object {
        private const val KEY_WIDGET_FILTER_MODE = "widget_filter_mode"
        private const val KEY_WIDGET_SHOW_LOCKED = "widget_show_locked"
        private const val KEY_WIDGET_SHOW_CONTENT = "widget_show_content"
        private const val KEY_WIDGET_DESIGN_PRESET = "widget_design_preset"
        private const val KEY_WIDGET_FAB_BEHAVIOR = "widget_fab_behavior"
        private const val KEY_WIDGET_CORNER_RADIUS = "widget_corner_radius"
        private const val KEY_DEFAULT_COLOR = "default_note_color"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val KEY_MASTER_PIN = "master_pin"
        private const val KEY_AUTO_LOCK = "auto_lock_on_exit"
        private const val KEY_HIGH_CONTRAST = "high_contrast_negative"
        private const val KEY_LAST_BACKUP = "last_backup_timestamp"
        private const val KEY_NOTE_FONT_SIZE = "key_note_font_size"
        private const val KEY_LAST_SYNC_HOST = "last_sync_host"
        private const val KEY_LAST_SYNC_PIN = "last_sync_pin"
        private const val KEY_COLOR_PALETTE = "app_color_palette"
        private const val KEY_DRAWER_COLLAPSE_MODE = "drawer_collapse_mode"
        private const val KEY_CARD_CORNER_STYLE = "card_corner_style"
        private const val KEY_CARD_ELEVATION_STYLE = "card_elevation_style"
        private const val KEY_QUICK_ACTION_LOCATION = "quick_action_location"
        private const val KEY_SEARCH_BAR_SHAPE = "search_bar_shape"
        private const val KEY_DESIGN_PRESET = "design_preset"
        private const val KEY_DAILY_WORD_GOAL = "daily_word_goal"
        private const val KEY_TRASH_RETENTION_POLICY = "trash_retention_policy"
        private const val KEY_LOCK_TIMEOUT_POLICY = "lock_timeout_policy"

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

        fun getWidgetDesignPreset(context: Context): WidgetDesignPreset {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val str = prefs.getString(KEY_WIDGET_DESIGN_PRESET, WidgetDesignPreset.MODERN_RED.name) ?: WidgetDesignPreset.MODERN_RED.name
            return try { WidgetDesignPreset.valueOf(str) } catch (_: Exception) { WidgetDesignPreset.MODERN_RED }
        }

        fun getWidgetFabBehavior(context: Context): WidgetFabBehavior {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val str = prefs.getString(KEY_WIDGET_FAB_BEHAVIOR, WidgetFabBehavior.DOCK_TOOLBAR.name) ?: WidgetFabBehavior.DOCK_TOOLBAR.name
            return try { WidgetFabBehavior.valueOf(str) } catch (_: Exception) { WidgetFabBehavior.DOCK_TOOLBAR }
        }

        fun getWidgetCornerRadius(context: Context): WidgetCornerRadius {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val str = prefs.getString(KEY_WIDGET_CORNER_RADIUS, WidgetCornerRadius.LARGE.name) ?: WidgetCornerRadius.LARGE.name
            return try { WidgetCornerRadius.valueOf(str) } catch (_: Exception) { WidgetCornerRadius.LARGE }
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
