package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.BoardTask
import com.example.noteapp.domain.util.TaskBoardEngine
import com.example.noteapp.domain.util.TaskBoardState
import com.example.noteapp.domain.util.TaskPriority

/**
 * Not İçi Etkileşimli Görev Panosu (Kanban) Alt Sayfası.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBoardBottomSheet(
    content: String,
    onDismissRequest: () -> Unit,
    onContentChange: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) } // 0: Yapılacaklar, 1: Tamamlananlar
    var newTaskTitle by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TaskPriority.NORMAL) }

    val boardState = remember(content) {
        TaskBoardEngine.parseContentToBoard(content)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PendingActions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Görev Panosu (Kanban)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (boardState.totalTaskCount > 0)
                                "${boardState.totalTaskCount} görevden ${boardState.completedTasks.size}'si tamamlandı (%${boardState.completionPercentage})"
                            else "Notta henüz kontrol listesi maddesi yok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(Modifier.height(12.dp))

            // İlerleme Çubuğu
            if (boardState.totalTaskCount > 0) {
                LinearProgressIndicator(
                    progress = { boardState.completionPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(14.dp))
            }

            // Yeni Görev Ekleme Alanı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("Yeni görev ekle...", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                // Öncelik Butonu
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedPriority = when (selectedPriority) {
                                TaskPriority.NORMAL -> TaskPriority.MEDIUM
                                TaskPriority.MEDIUM -> TaskPriority.HIGH
                                TaskPriority.HIGH -> TaskPriority.NORMAL
                            }
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedPriority.badge,
                            fontSize = 20.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            val updated = TaskBoardEngine.addNewTask(content, newTaskTitle, selectedPriority)
                            onContentChange(updated)
                            newTaskTitle = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp),
                    enabled = newTaskTitle.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ekle")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Sütun / Sekme Seçici
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedTab == 0) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏳ Bekleyenler (${boardState.pendingTasks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedTab == 1) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✅ Tamamlananlar (${boardState.completedTasks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Görev Listesi
            val activeTasks = if (selectedTab == 0) boardState.pendingTasks else boardState.completedTasks

            if (activeTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "🎉 Harika! Bekleyen görev yok." else "Henüz tamamlanmış görev yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeTasks, key = { it.id }) { task ->
                        TaskCardItem(
                            task = task,
                            onToggle = {
                                val updated = TaskBoardEngine.toggleTaskStatus(content, task)
                                onContentChange(updated)
                            }
                        )
                    }
                }
            }

            // Hızlı Düzenleme Butonu (Tamamlananları alta taşı)
            if (boardState.completedTasks.isNotEmpty() && boardState.pendingTasks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val reordered = TaskBoardEngine.pushCompletedTasksToBottom(content)
                        onContentChange(reordered)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LowPriority, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tamamlananları Listenin En Altına Taşı")
                }
            }
        }
    }
}

/**
 * Tekil görev kartı bileşeni.
 */
@Composable
private fun TaskCardItem(
    task: BoardTask,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.tags.take(3).forEach { tag ->
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (task.priority != TaskPriority.NORMAL) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (task.priority) {
                        TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        TaskPriority.NORMAL -> Color.Transparent
                    }
                ) {
                    Text(
                        text = "${task.priority.badge} ${task.priority.title}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
