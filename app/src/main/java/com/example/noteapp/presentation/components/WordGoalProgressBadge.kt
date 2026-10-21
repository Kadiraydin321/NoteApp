package com.example.noteapp.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.noteapp.domain.util.WordGoalStatus

/**
 * Not detay ekranında veya tipografi HUD'ında canlı kelime hedefi ilerlemesini
 * dairesel göstergeyle sunan ve düzenleme/kaldırma menüsü içeren rozet bileşeni.
 */
@Composable
fun WordGoalProgressBadge(
    status: WordGoalStatus,
    contentColor: Color,
    onEditGoal: () -> Unit,
    onRemoveGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val targetColor = when {
        status.isCompleted -> Color(0xFF4CAF50) // Yeşil
        status.progress >= 0.75f -> Color(0xFF26A69A) // Canlı Turkuaz
        status.progress > 0f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val badgeColor by animateColorAsState(targetValue = targetColor, label = "WordGoalColor")
    val animatedProgress by animateFloatAsState(targetValue = status.progress, label = "WordGoalProgress")

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { showMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (status.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Hedef Tamamlandı",
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        strokeWidth = 2.5.dp,
                        color = badgeColor,
                        trackColor = badgeColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = if (status.isCompleted) "🎯 %${status.percent}" else "${status.currentWords}/${status.targetWords}",
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
            // Durum Başlığı
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = if (status.isCompleted) "🎉 Hedef Tamamlandı!" else "🎯 Kelime Hedefi Takibi",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (status.isCompleted) {
                                "${status.currentWords} / ${status.targetWords} kelime (%${status.percent})"
                            } else {
                                "${status.currentWords} / ${status.targetWords} kelime (${status.remainingWords} kelime kaldı)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                enabled = false,
                onClick = {}
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Hedefi Düzenle") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showMenu = false
                    onEditGoal()
                }
            )

            DropdownMenuItem(
                text = { Text("Hedefi Kaldır", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onRemoveGoal()
                }
            )
        }
    }
}

/**
 * Not için kelime hedefi belirleme veya düzenleme iletişim penceresi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetWordGoalDialog(
    initialGoal: Int?,
    onDismiss: () -> Unit,
    onSaveGoal: (Int?) -> Unit
) {
    var goalText by remember { mutableStateOf(initialGoal?.toString() ?: "250") }
    val presets = listOf(100, 250, 500, 1000, 2000)

    val parsedGoal = goalText.trim().toIntOrNull()
    val isValid = parsedGoal != null && parsedGoal > 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Kelime Hedefi Belirle",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Bu not için ulaşmak istediğin hedef kelime sayısını seç veya özel bir miktar gir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Hazır Şablon Çipleri
                Text(
                    text = "Önerilen Hedefler",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = parsedGoal == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { goalText = preset.toString() },
                            label = { Text("$preset kelime") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Özel Sayı Girişi
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            goalText = input
                        }
                    },
                    label = { Text("Hedef Kelime Sayısı") },
                    placeholder = { Text("örn: 500") },
                    trailingIcon = {
                        Text(
                            text = "kelime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // Aksiyon Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialGoal != null) {
                        TextButton(
                            onClick = {
                                onSaveGoal(null)
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Hedefi Kaldır")
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("İptal")
                        }
                        Button(
                            onClick = {
                                if (isValid) {
                                    onSaveGoal(parsedGoal)
                                    onDismiss()
                                }
                            },
                            enabled = isValid
                        ) {
                            Text("Kaydet")
                        }
                    }
                }
            }
        }
    }
}
