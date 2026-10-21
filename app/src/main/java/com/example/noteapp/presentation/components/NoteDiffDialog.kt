package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.noteapp.domain.model.NoteHistory
import com.example.noteapp.domain.util.NoteDiffEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Not Versiyon Karşılaştırma & Görsel Değişiklik İnceleyici Dialogu.
 * Eski bir geçmiş sürümü ile mevcut not arasındaki eklemeleri (+ yeşil) ve silmeleri (- kırmızı)
 * satır bazında vurgulayarak sunar.
 */
@Composable
fun NoteDiffDialog(
    historyVersion: NoteHistory,
    currentTitle: String,
    currentContent: String,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    val diffResult = remember(historyVersion, currentTitle, currentContent) {
        NoteDiffEngine.computeDiff(
            oldTitle = historyVersion.title,
            oldContent = historyVersion.content,
            newTitle = currentTitle,
            newContent = currentContent
        )
    }

    val historyDateFormatted = remember(historyVersion.timestamp) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(historyVersion.timestamp))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Başlık Çubuğu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Versiyon Karşılaştırması",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kayıt: $historyDateFormatted ➔ Mevcut Not",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Değişiklik İstatistikleri Rozetleri (+X / -Y / Z)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiffStatBadge(
                        label = "+${diffResult.additionsCount} Satır",
                        containerColor = Color(0xFF1B5E20).copy(alpha = 0.15f),
                        contentColor = Color(0xFF2E7D32)
                    )
                    DiffStatBadge(
                        label = "-${diffResult.deletionsCount} Satır",
                        containerColor = Color(0xFFB71C1C).copy(alpha = 0.15f),
                        contentColor = Color(0xFFC62828)
                    )
                    DiffStatBadge(
                        label = "${diffResult.unchangedCount} Değişmedi",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Başlık Farkı (Varsa)
                if (diffResult.titleChanged) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Başlık Değişikliği:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "- ${historyVersion.title.ifBlank { "(Boş)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "+ ${currentTitle.ifBlank { "(Boş)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Diff İçerik Listesi
                if (diffResult.lines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bu versiyon ile mevcut içerik arasında fark bulunamadı.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(diffResult.lines) { line ->
                            DiffLineRow(line)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Alt Eylem Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kapat")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onRestore()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bu Sürüme Geri Dön")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffStatBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DiffLineRow(line: NoteDiffEngine.DiffLine) {
    val bgColor = when (line.type) {
        NoteDiffEngine.DiffType.ADDED -> Color(0xFF4CAF50).copy(alpha = 0.16f)
        NoteDiffEngine.DiffType.DELETED -> Color(0xFFF44336).copy(alpha = 0.16f)
        NoteDiffEngine.DiffType.UNCHANGED -> Color.Transparent
    }

    val gutterColor = when (line.type) {
        NoteDiffEngine.DiffType.ADDED -> Color(0xFF2E7D32)
        NoteDiffEngine.DiffType.DELETED -> Color(0xFFC62828)
        NoteDiffEngine.DiffType.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    val symbol = when (line.type) {
        NoteDiffEngine.DiffType.ADDED -> "+"
        NoteDiffEngine.DiffType.DELETED -> "-"
        NoteDiffEngine.DiffType.UNCHANGED -> " "
    }

    val lineNumber = line.newLineNumber ?: line.oldLineNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Satır Numarası
        Text(
            text = lineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Sembol (+ / - /  )
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = gutterColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Satır Metni (Gerektiğinde yatay kaydırmalı)
        Text(
            text = line.text.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall,
            color = when (line.type) {
                NoteDiffEngine.DiffType.ADDED -> Color(0xFF1B5E20)
                NoteDiffEngine.DiffType.DELETED -> Color(0xFFB71C1C)
                NoteDiffEngine.DiffType.UNCHANGED -> MaterialTheme.colorScheme.onSurface
            },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        )
    }
}
