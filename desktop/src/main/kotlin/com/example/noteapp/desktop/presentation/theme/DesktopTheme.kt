package com.example.noteapp.desktop.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val NotePresets = listOf(
    0 to "Varsayılan",
    0xFF1E293B.toInt() to "Gece Mavisi",
    0xFF2A2035.toInt() to "Mürdüm / Lavanta",
    0xFF1F2E2B.toInt() to "Zümrüt Yeşil",
    0xFF332A1E.toInt() to "Sıcak Amber",
    0xFF361E24.toInt() to "Gül Kurusu",
    0xFF1E2E38.toInt() to "Okyanus Teal",
    0xFFFFF9C4.toInt() to "Açık Sarı",
    0xFFFFE0B2.toInt() to "Açık Şeftali",
    0xFFFFCDD2.toInt() to "Açık Mercan",
    0xFFF8BBD0.toInt() to "Açık Pembe",
    0xFFE1BEE7.toInt() to "Açık Mor",
    0xFFBBDEFB.toInt() to "Açık Mavi",
    0xFFC8E6C9.toInt() to "Açık Nane"
)

@Immutable
data class NoteColorSpec(
    val backgroundColor: Color,
    val contentColor: Color,
    val secondaryColor: Color,
    val borderColor: Color
)

fun getNoteColorSpec(
    rawColor: Int,
    isDarkTheme: Boolean
): NoteColorSpec {
    val defaultBg = if (isDarkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
    val defaultBorder = if (isDarkTheme) Color(0xFF263345) else Color(0xFFE2E8F0)

    if (rawColor == 0) {
        val textColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
        val secText = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
        return NoteColorSpec(defaultBg, textColor, secText, defaultBorder)
    }

    val bgColor = Color(rawColor)
    val isDarkBg = bgColor.luminance() < 0.45f
    val textColor = if (isDarkBg) Color(0xFFF8F9FA) else Color(0xFF1A1C1E)
    val secText = if (isDarkBg) Color(0xFFCBD5E1) else Color(0xFF475569)
    val border = if (isDarkBg) Color(0x33FFFFFF) else Color(0x22000000)

    return NoteColorSpec(bgColor, textColor, secText, border)
}

@Composable
fun DesktopNoteTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = Color(0xFF3B82F6),            // Canlı ama göz yormayan modern mavi
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1E293B),   // Çok daha asil ve koyu container
            onPrimaryContainer = Color(0xFF93C5FD), // Yumuşak açık mavi metin
            secondary = Color(0xFF818CF8),          // Şık indigo ikincil renk
            surface = Color(0xFF0F172A),            // Slate 900 ana yüzey
            surfaceVariant = Color(0xFF1E293B),     // Slate 800 kart ve panel yüzeyi
            background = Color(0xFF0B1120),         // En derin koyu arka plan
            onSurface = Color(0xFFF8FAFC),          // Net beyazımsı metin
            onSurfaceVariant = Color(0xFF94A3B8),   // İkincil yumuşak metin
            outline = Color(0xFF334155),            // Zarif sınır çizgileri
            outlineVariant = Color(0xFF1E293B),
            error = Color(0xFFF87171)               // Pastel kırmızı
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEFF6FF),
            onPrimaryContainer = Color(0xFF1D4ED8),
            secondary = Color(0xFF6366F1),
            surface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFFF1F5F9),
            background = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF64748B),
            outline = Color(0xFFE2E8F0),
            error = Color(0xFFDC2626)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
