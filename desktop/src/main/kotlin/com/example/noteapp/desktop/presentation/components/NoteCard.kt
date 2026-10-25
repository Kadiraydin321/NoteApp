package com.example.noteapp.desktop.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
import com.example.noteapp.desktop.presentation.theme.getNoteColorSpec
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteCard(
    note: Note,
    category: Category?,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    isVaultUnlocked: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    isMultiSelectMode: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorSpec = getNoteColorSpec(note.color, isDarkTheme)
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val dateText = dateFormat.format(Date(if (note.updatedAt != 0L) note.updatedAt else note.timestamp))

    val displayContent = when {
        note.isLocked && !isVaultUnlocked -> "🔒 Gizli Not (Kasayı açarak görüntüleyin)"
        note.content.isBlank() -> "(İçerik yok)"
        else -> note.content
    }

    val cardBg = if (isSelected) {
        if (note.color != 0) colorSpec.backgroundColor else MaterialTheme.colorScheme.surfaceVariant
    } else {
        if (note.color != 0) colorSpec.backgroundColor else MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, colorSpec.borderColor)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = cardBorder,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Üst Satır: Başlık, Kategori, Pin / Kilit / Hatırlatıcı İkonları, Çoklu Seçim
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.size(24.dp).padding(end = 6.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Sabitlendi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp).padding(end = 4.dp)
                    )
                }
                if (note.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Kilitli",
                        tint = if (isVaultUnlocked) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp).padding(end = 4.dp)
                    )
                }
                if (note.reminderTime != null && note.reminderTime > 0) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Hatırlatıcı",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp).padding(end = 4.dp)
                    )
                }

                Text(
                    text = if (note.title.isBlank()) "Başlıksız Not" else note.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = colorSpec.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (category != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = if (category.color != 0) Color(category.color).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (category.color != 0) Color(category.color).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            if (category.color != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(category.color))
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = colorSpec.contentColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // İçerik Özeti
            Text(
                text = displayContent,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colorSpec.secondaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Alt Satır: Tarih & Hızlı Eylemler
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colorSpec.secondaryColor.copy(alpha = 0.6f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (note.isDeleted) {
                        if (onRestore != null) {
                            IconButton(onClick = onRestore, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Restore, contentDescription = "Geri Yükle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            }
                        }
                        if (onDeletePermanently != null) {
                            IconButton(onClick = onDeletePermanently, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Kalıcı Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            }
                        }
                    } else {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Sabitle",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else colorSpec.secondaryColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(onClick = onMoveToTrash, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Çöpe At", tint = colorSpec.secondaryColor.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
