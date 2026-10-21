package com.example.noteapp.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.TaskAnalyticsEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistActionBottomSheet(
    content: String,
    onApplyModifiedContent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val summary = remember(content) { TaskAnalyticsEngine.parseTasks(content) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Görev Listesi İşlemleri",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${summary.completedTasks} / ${summary.totalTasks} görev tamamlandı (%${summary.percentCompleted})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Canlı İlerleme Çubuğu
            LinearProgressIndicator(
                progress = { (summary.percentCompleted / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (summary.percentCompleted == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Hızlı Görev Eylemleri",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(10.dp))

            // Eylem Butonları Izgarası
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskActionButton(
                    icon = Icons.Default.VerticalAlignBottom,
                    title = "Tamamlananları Aşağı Taşı",
                    subtitle = "Yapılan işleri listenin sonuna gruplar, kalanları üstte tutar",
                    onClick = {
                        val sorted = TaskAnalyticsEngine.sortCompletedToBottom(content)
                        onApplyModifiedContent(sorted)
                        Toast.makeText(context, "Tamamlanan görevler aşağı taşındı", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                TaskActionButton(
                    icon = Icons.Default.DoneAll,
                    title = "Tüm Görevleri Tamamla",
                    subtitle = "Listedeki tüm açık kutucukları işaretler (- [x])",
                    onClick = {
                        val allChecked = TaskAnalyticsEngine.setAllTasksState(content, checked = true)
                        onApplyModifiedContent(allChecked)
                        Toast.makeText(context, "Tüm görevler tamamlandı olarak işaretlendi", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                TaskActionButton(
                    icon = Icons.Default.RemoveDone,
                    title = "Tüm Görevleri Sıfırla",
                    subtitle = "Tüm kutucukların işaretini kaldırıp açık hale getirir (- [ ])",
                    onClick = {
                        val allUnchecked = TaskAnalyticsEngine.setAllTasksState(content, checked = false)
                        onApplyModifiedContent(allUnchecked)
                        Toast.makeText(context, "Tüm görevler sıfırlandı", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                TaskActionButton(
                    icon = Icons.Default.DeleteSweep,
                    title = "Tamamlananları Listeden Temizle",
                    subtitle = "İşareti olan maddeleri nottan siler, kalanları korur",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        val cleaned = TaskAnalyticsEngine.removeCompletedTasks(content)
                        onApplyModifiedContent(cleaned)
                        Toast.makeText(context, "Tamamlanan görevler temizlendi", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                TaskActionButton(
                    icon = Icons.Default.ContentCopy,
                    title = "Kalan Görevleri Kopyala",
                    subtitle = "Yalnızca yapılmamış açık maddeleri liste olarak panoya aktarır",
                    onClick = {
                        val plain = TaskAnalyticsEngine.extractOpenTasksAsPlainText(content)
                        if (plain.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Kalan Görevler", plain))
                            Toast.makeText(context, "${summary.openTasks} kalan görev panoya kopyalandı", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Açık kalan görev bulunmuyor", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                )
            }

            // Görev Maddeleri Önizlemesi
            if (summary.items.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Maddeler (${summary.items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.items.take(8).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = item.text.ifBlank { "Boş görev" },
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                    color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (summary.items.size > 8) {
                        Text(
                            text = "+${summary.items.size - 8} görev daha...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
