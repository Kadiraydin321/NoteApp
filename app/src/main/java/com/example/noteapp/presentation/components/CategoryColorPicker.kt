package com.example.noteapp.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.noteapp.domain.model.Category

/**
 * Kategori renk seçici yatay palet bileşeni.
 * 8 estetik palet rengi ve 1 nötr varsayılan seçenek sunar.
 */
@Composable
fun CategoryColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Category.DEFAULT_COLORS.forEach { colorInt ->
            val isSelected = selectedColor == colorInt
            val isDefault = colorInt == 0
            val colorDesc = if (isDefault) "Varsayılan renksiz kategori" else "Kategori rengi: #${Integer.toHexString(colorInt).uppercase()}"

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDefault) MaterialTheme.colorScheme.surfaceVariant
                        else Color(colorInt)
                    )
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .clickable(
                        role = Role.RadioButton,
                        onClickLabel = "Rengi seç",
                        onClick = { onColorSelected(colorInt) }
                    )
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                        contentDescription = "$colorDesc${if (isSelected) " (seçili)" else ""}"
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isDefault && !isSelected) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                }
            }
        }
    }
}

/**
 * Not kartlarında ve arayüzde gösterilen şık renkli kategori rozeti.
 */
@Composable
fun CategoryBadge(
    category: Category,
    modifier: Modifier = Modifier,
    contentColorHint: Color? = null,
    isLocked: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val hasColor = category.color != 0
    val catColor = if (hasColor) Color(category.color) else (contentColorHint ?: MaterialTheme.colorScheme.primary)
    val containerColor = if (hasColor) {
        catColor.copy(alpha = 0.14f)
    } else {
        (contentColorHint ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.08f)
    }
    val borderColor = if (hasColor) {
        catColor.copy(alpha = 0.38f)
    } else {
        (contentColorHint ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.18f)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kilitli Kategori",
                    tint = catColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(11.dp)
                )
            } else if (hasColor) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
            }
            Text(
                text = "#${category.name}",
                style = MaterialTheme.typography.labelSmall,
                color = catColor.copy(alpha = 0.95f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
