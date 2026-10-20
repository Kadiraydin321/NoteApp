package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SaveTemplateDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, iconEmoji: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle.ifBlank { "Özel Şablonum" }) }
    var description by remember { mutableStateOf("Sık kullanılan not formatı") }
    var selectedEmoji by remember { mutableStateOf("📝") }

    val emojis = listOf("📝", "🎯", "🚀", "💡", "💼", "📚", "🏃", "🎨", "✈️", "🛒", "💻", "🔥")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Şablon Olarak Kaydet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Bu notun yapısını daha sonra tek tıkla tekrar kullanmak için özel şablonlarınıza ekleyin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Simge / Emoji Seçimi
                Text(
                    text = "Simge Seçin:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.take(6).forEach { emoji ->
                        EmojiOption(
                            emoji = emoji,
                            isSelected = selectedEmoji == emoji,
                            onSelect = { selectedEmoji = emoji }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.drop(6).forEach { emoji ->
                        EmojiOption(
                            emoji = emoji,
                            isSelected = selectedEmoji == emoji,
                            onSelect = { selectedEmoji = emoji }
                        )
                    }
                }

                // Şablon Başlığı
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Şablon Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Açıklama
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Kısa Açıklama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), description.trim(), selectedEmoji)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun EmojiOption(
    emoji: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}
