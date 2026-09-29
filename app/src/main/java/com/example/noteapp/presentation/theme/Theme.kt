package com.example.noteapp.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.noteapp.data.settings.ThemeMode

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
    val textColor = if (isDarkBg) Color(0xFFF8F9FA) else Color(0xFF1A1C1E)
    val secondaryText = if (isDarkBg) Color(0xFFD0D4D9) else Color(0xFF43474E)
    val icon = if (isDarkBg) Color(0xFFE2E2E6) else Color(0xFF43474E)

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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
