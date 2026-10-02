package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Google Keep tarzında interaktif onay kutusu ikonu.
 * - İşaretsiz durum: Temiz, yuvarlatılmış kare çerçeve (kutucuk).
 * - İşaretli durum: İçi belirgin 'X' simgesi ile dolan ve renklenen kutucuk.
 * - Yazı boyutuna (fontSize) göre otomatik ve orantılı olarak dinamik ölçeklenir.
 */
@Composable
fun KeepCheckboxIcon(
    isChecked: Boolean,
    fontSize: TextUnit,
    contentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size = (fontSize.value * 1.25f).coerceIn(16f, 32f).dp
    val cornerRadius = (size.value * 0.22f).dp
    val strokeWidth = (size.value * 0.08f).coerceAtLeast(1.5f).dp

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size),
                onClick = onToggle
            )
            .then(
                if (isChecked) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            RoundedCornerShape(cornerRadius)
                        )
                        .border(
                            strokeWidth,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(cornerRadius)
                        )
                } else {
                    Modifier.border(
                        strokeWidth,
                        contentColor.copy(alpha = 0.55f),
                        RoundedCornerShape(cornerRadius)
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isChecked) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Tamamlandı (X)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((size.value * 0.72f).dp)
            )
        }
    }
}
