package com.example.noteapp.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.noteapp.data.settings.AppColorPalette
import com.example.noteapp.data.settings.ThemeMode

// =========================================================================
// 1) SICAK KEHRIBAR & KAHVE (WARM AMBER) - Varsayılan Sıcak Koyu Palet
// =========================================================================
private val WarmAmberDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),          // Sıcak Kehribar Sarısı
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF683D00),
    onPrimaryContainer = Color(0xFFFFDDB3),

    secondary = Color(0xFFFFAB91),        // Sıcak Terracotta / Şeftali
    onSecondary = Color(0xFF5C1A00),
    secondaryContainer = Color(0xFF7E2A00),
    onSecondaryContainer = Color(0xFFFFDBD1),

    tertiary = Color(0xFFE6BE8A),         // Sıcak Kum & Karamel
    onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5B3F18),
    onTertiaryContainer = Color(0xFFFFDEAC),

    background = Color(0xFF171310),       // Çok Koyu Sıcak Kahverengi / Espresso
    onBackground = Color(0xFFF3ECE6),
    surface = Color(0xFF1E1814),          // Derin Sıcak Yüzey
    onSurface = Color(0xFFF3ECE6),
    surfaceVariant = Color(0xFF2E241E),   // Sıcak Ahşap / Çikolata Kart Yüzeyi
    onSurfaceVariant = Color(0xFFD6C4B8),
    surfaceContainerHighest = Color(0xFF3B2E26),

    outline = Color(0xFF8A7366),
    outlineVariant = Color(0xFF54443B)
)

private val WarmAmberLightScheme = lightColorScheme(
    primary = Color(0xFF8C5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2D1600),

    secondary = Color(0xFF9E4218),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3B0F00),

    tertiary = Color(0xFF76572F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEAC),
    onTertiaryContainer = Color(0xFF2B1700),

    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF231A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF231A14),
    surfaceVariant = Color(0xFFF3DFD2),
    onSurfaceVariant = Color(0xFF51443B),
    surfaceContainerHighest = Color(0xFFE8D4C7),

    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD6C4B8)
)

// =========================================================================
// 2) GECE GÜLÜ (MIDNIGHT ROSE) - Sıcak Mürdüm & Bordo Gece Tonları
// =========================================================================
private val MidnightRoseDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB0CD),          // Gül Kurusu & Pembe Işıltı
    onPrimary = Color(0xFF5C1134),
    primaryContainer = Color(0xFF7B294A),
    onPrimaryContainer = Color(0xFFFFD9E2),

    secondary = Color(0xFFE4BDC8),        // Yumuşak Leylak / Şarap
    onSecondary = Color(0xFF442932),
    secondaryContainer = Color(0xFF5D3F48),
    onSecondaryContainer = Color(0xFFFFD9E2),

    tertiary = Color(0xFFF2B7A8),         // Sıcak Kiremit / Şeftali
    onTertiary = Color(0xFF4C251C),
    tertiaryContainer = Color(0xFF673B30),
    onTertiaryContainer = Color(0xFFFFDAD4),

    background = Color(0xFF161013),       // Çok Koyu Mürdüm
    onBackground = Color(0xFFF3ECEE),
    surface = Color(0xFF1E1519),
    onSurface = Color(0xFFF3ECEE),
    surfaceVariant = Color(0xFF2E2026),   // Koyu Vişne Kart Yüzeyi
    onSurfaceVariant = Color(0xFFD5C1C7),
    surfaceContainerHighest = Color(0xFF3B2A32),

    outline = Color(0xFF9E8B92),
    outlineVariant = Color(0xFF534349)
)

private val MidnightRoseLightScheme = lightColorScheme(
    primary = Color(0xFF984062),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001F),

    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),

    tertiary = Color(0xFF815349),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF33120B),

    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    surfaceContainerHighest = Color(0xFFE8D1D7),

    outline = Color(0xFF837378),
    outlineVariant = Color(0xFFD5C1C7)
)

// =========================================================================
// 3) ZÜMRÜT ORMANI (FOREST EMERALD) - Sıcak Çam & Adaçayı Tonları
// =========================================================================
private val ForestEmeraldDarkScheme = darkColorScheme(
    primary = Color(0xFF81D89D),          // Taze Adaçayı & Nane
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005328),
    onPrimaryContainer = Color(0xFF9DF5B7),

    secondary = Color(0xFFB7CCB7),        // Sisli Çam Grisi
    onSecondary = Color(0xFF233526),
    secondaryContainer = Color(0xFF394B3C),
    onSecondaryContainer = Color(0xFFD3E8D3),

    tertiary = Color(0xFFA2CED9),         // Durgun Göl Mavisi
    onTertiary = Color(0xFF03363F),
    tertiaryContainer = Color(0xFF214D56),
    onTertiaryContainer = Color(0xFFBEEAF5),

    background = Color(0xFF101511),       // Çok Koyu Orman Yeşili
    onBackground = Color(0xFFEAEFEA),
    surface = Color(0xFF141C16),
    onSurface = Color(0xFFEAEFEA),
    surfaceVariant = Color(0xFF202A22),   // Yosun Kart Yüzeyi
    onSurfaceVariant = Color(0xFFC0CCC1),
    surfaceContainerHighest = Color(0xFF2C392F),

    outline = Color(0xFF8A958B),
    outlineVariant = Color(0xFF424E43)
)

private val ForestEmeraldLightScheme = lightColorScheme(
    primary = Color(0xFF156C3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF5B7),
    onPrimaryContainer = Color(0xFF00210C),

    secondary = Color(0xFF506352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D3),
    onSecondaryContainer = Color(0xFF0E1F12),

    tertiary = Color(0xFF39656E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF5),
    onTertiaryContainer = Color(0xFF001F25),

    background = Color(0xFFF7FAF6),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF7FAF6),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404942),
    surfaceContainerHighest = Color(0xFFD0DCD1),

    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC0CCC1)
)

// =========================================================================
// 4) DERİN OKYANUS (DEEP OCEAN) - Sıcak Safir & Gece Mavisi
// =========================================================================
private val DeepOceanDarkScheme = darkColorScheme(
    primary = Color(0xFF97CCF8),          // Okyanus Işıltısı
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004B76),
    onPrimaryContainer = Color(0xFFCDE5FF),

    secondary = Color(0xFFB9C8DA),        // Fırtına Grisi
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F6),

    tertiary = Color(0xFFD4BEE6),         // Alacakaranlık Moru
    onTertiary = Color(0xFF392A4A),
    tertiaryContainer = Color(0xFF504061),
    onTertiaryContainer = Color(0xFFEEDBFF),

    background = Color(0xFF0F1419),       // Gece Yarısı Derin Mavi
    onBackground = Color(0xFFE9EDF2),
    surface = Color(0xFF141920),
    onSurface = Color(0xFFE9EDF2),
    surfaceVariant = Color(0xFF202731),   // Koyu Lacivert Kart Yüzeyi
    onSurfaceVariant = Color(0xFFC1C8D2),
    surfaceContainerHighest = Color(0xFF2C3440),

    outline = Color(0xFF8B929B),
    outlineVariant = Color(0xFF424750)
)

private val DeepOceanLightScheme = lightColorScheme(
    primary = Color(0xFF196499),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),

    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F6),
    onSecondaryContainer = Color(0xFF0E1D2A),

    tertiary = Color(0xFF68587A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEDBFF),
    onTertiaryContainer = Color(0xFF231533),

    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171C21),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF171C21),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerHighest = Color(0xFFD1D8E1),

    outline = Color(0xFF72787E),
    outlineVariant = Color(0xFFC1C8D2)
)

// =========================================================================
// 5) KOYU GRAFİT (SLATE MINIMAL) - Nötr Koyu Antrasit & Platin
// =========================================================================
private val SlateMinimalDarkScheme = darkColorScheme(
    primary = Color(0xFFD0D3D9),
    onPrimary = Color(0xFF22262B),
    primaryContainer = Color(0xFF393E46),
    onPrimaryContainer = Color(0xFFEEF0F4),

    secondary = Color(0xFFC4C7CC),
    onSecondary = Color(0xFF2E3135),
    secondaryContainer = Color(0xFF44474D),
    onSecondaryContainer = Color(0xFFE0E2E8),

    tertiary = Color(0xFFD8D2C9),
    onTertiary = Color(0xFF35312C),
    tertiaryContainer = Color(0xFF4D4741),
    onTertiaryContainer = Color(0xFFF5EFE7),

    background = Color(0xFF121315),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF181A1D),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF26292E),
    onSurfaceVariant = Color(0xFFCACDD4),
    surfaceContainerHighest = Color(0xFF33373E),

    outline = Color(0xFF90939A),
    outlineVariant = Color(0xFF464950)
)

private val SlateMinimalLightScheme = lightColorScheme(
    primary = Color(0xFF323943),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE2E8),
    onPrimaryContainer = Color(0xFF181C21),

    secondary = Color(0xFF4A5059),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE2E8),
    onSecondaryContainer = Color(0xFF191C20),

    tertiary = Color(0xFF554E48),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE5DC),
    onTertiaryContainer = Color(0xFF201B15),

    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E4EA),
    onSurfaceVariant = Color(0xFF45474D),
    surfaceContainerHighest = Color(0xFFD6D9E0),

    outline = Color(0xFF75777E),
    outlineVariant = Color(0xFFC5C7CE)
)

// =========================================================================
// 6) BUZ & CAM MAVİSİ (GLACIAL ICE) - Kristal Cam, Şeffaflık & Ferah Buzul Tonları
// =========================================================================
private val GlacialIceDarkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),          // Parlak Işıltılı Buz Mavisi (Sky-400)
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFC2E8FF),

    secondary = Color(0xFF7DD3FC),        // Yarı Saydam Buz Akriliği
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF163C4E),
    onSecondaryContainer = Color(0xFFBCE7FD),

    tertiary = Color(0xFF99F6E4),         // Akuamarin Cam Parıltısı
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0B4D45),
    onTertiaryContainer = Color(0xFFB5F4E9),

    background = Color(0xFF0B131B),       // Çok Koyu Arktik Gece Camı
    onBackground = Color(0xFFE2F1F8),
    surface = Color(0xFF101B24),          // Derin Buzul Yüzeyi
    onSurface = Color(0xFFE2F1F8),
    surfaceVariant = Color(0xFF182A3A),   // Yarı Saydam Hissiyatlı Cam Kart
    onSurfaceVariant = Color(0xFFB8CBD6),
    surfaceContainerHighest = Color(0xFF22374A),

    outline = Color(0xFF38BDF8),          // Cam Çerçeve Işıltısı
    outlineVariant = Color(0xFF1F384A)
)

private val GlacialIceLightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),          // Canlı Cam Mavisi (Sky-600)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE), // Çok Ferah Açık Buz Kristali
    onPrimaryContainer = Color(0xFF034A6E),

    secondary = Color(0xFF0EA5E9),        // Berrak Gökyüzü / Cam
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF004D5A),

    tertiary = Color(0xFF0D9488),         // Zümrüt Buzul Mavisi
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF004D40),

    background = Color(0xFFF0F9FF),       // Çok Ferah Açık Arktik Zemin
    onBackground = Color(0xFF082F49),
    surface = Color(0xFFF0F9FF),
    onSurface = Color(0xFF082F49),
    surfaceVariant = Color(0xFFE0F2FE),   // Yarı Saydam Temiz Cam Efekti
    onSurfaceVariant = Color(0xFF334E68),
    surfaceContainerHighest = Color(0xFFBAE6FD),

    outline = Color(0xFF38BDF8),
    outlineVariant = Color(0xFF7DD3FC)
)

@Immutable
data class NoteColorSpec(
    val backgroundColor: Color,
    val contentColor: Color,
    val secondaryColor: Color,
    val iconTint: Color
)

fun getNoteColorSpec(
    rawColor: Int,
    defaultSurface: Color
): NoteColorSpec {
    val bgColor = if (rawColor != 0) {
        Color(rawColor)
    } else {
        defaultSurface
    }

    val isDarkBg = bgColor.luminance() < 0.45f
    val textColor = if (isDarkBg) Color(0xFFF9EFE7) else Color(0xFF211A15)
    val secondaryText = if (isDarkBg) Color(0xFFD8C7BC) else Color(0xFF5A4D44)
    val icon = if (isDarkBg) Color(0xFFEFE2D8) else Color(0xFF52453D)

    return NoteColorSpec(
        backgroundColor = bgColor,
        contentColor = textColor,
        secondaryColor = secondaryText,
        iconTint = icon
    )
}

@Composable
fun NoteAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    colorPalette: AppColorPalette = AppColorPalette.WARM_AMBER,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (colorPalette) {
            AppColorPalette.WARM_AMBER -> if (darkTheme) WarmAmberDarkScheme else WarmAmberLightScheme
            AppColorPalette.MIDNIGHT_ROSE -> if (darkTheme) MidnightRoseDarkScheme else MidnightRoseLightScheme
            AppColorPalette.FOREST_EMERALD -> if (darkTheme) ForestEmeraldDarkScheme else ForestEmeraldLightScheme
            AppColorPalette.DEEP_OCEAN -> if (darkTheme) DeepOceanDarkScheme else DeepOceanLightScheme
            AppColorPalette.SLATE_MINIMAL -> if (darkTheme) SlateMinimalDarkScheme else SlateMinimalLightScheme
            AppColorPalette.GLACIAL_ICE -> if (darkTheme) GlacialIceDarkScheme else GlacialIceLightScheme
        }
    }

    val colorScheme = if (darkTheme && amoledDark) {
        baseScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF121215),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF060608),
            surfaceContainer = Color(0xFF0D0D10),
            surfaceContainerHigh = Color(0xFF16161A),
            surfaceContainerHighest = Color(0xFF1F1F25),
            outlineVariant = Color(0xFF2E2E36)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
