package com.example.noteapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.ChecklistStats

/**
 * Not detay ekranında veya araç çubuğunda canlı kontrol listesi tamamlanma oranını
 * dairesel göstergeyle sunan ve hızlı aksiyon menüsü içeren rozet bileşeni.
 */
@Composable
fun ChecklistProgressRing(
    stats: ChecklistStats,
    contentColor: Color,
    onMoveToBottom: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onRemoveCompleted: () -> Unit,
    onOpenHub: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val ringColor = when {
        stats.isAllCompleted -> Color(0xFF4CAF50)
        stats.progress > 0f -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ringColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ringColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { showMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    progress = { stats.progress },
                    strokeWidth = 2.5.dp,
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = "${stats.completedCount}/${stats.totalCount}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = contentColor
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (onOpenHub != null) {
                DropdownMenuItem(
                    text = { Text("📋 Görev Eylem Merkezi") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onOpenHub()
                    }
                )
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = { Text("Tamamlananları Alta Taşı") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showMenu = false
                    onMoveToBottom()
                }
            )

            if (!stats.isAllCompleted) {
                DropdownMenuItem(
                    text = { Text("Tümünü Tamamla") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleAll(true)
                    }
                )
            }

            if (stats.completedCount > 0) {
                DropdownMenuItem(
                    text = { Text("Tüm İşaretleri Kaldır") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleAll(false)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Tamamlananları Sil", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onRemoveCompleted()
                    }
                )
            }
        }
    }
}
