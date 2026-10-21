package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Tüm notlarda kullanılan #etiketleri listeleyen, kullanım sayısını gösteren,
 * toplu yeniden adlandırma (rename) ve silme işlemleri sunan modern alt sayfa (bottom sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerBottomSheet(
    allTags: List<String>,
    tagCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelectTag: (String) -> Unit,
    onRenameTag: (oldTag: String, newTag: String) -> Unit,
    onDeleteTag: (tag: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var tagToRename by remember { mutableStateOf<String?>(null) }
    var tagToDelete by remember { mutableStateOf<String?>(null) }

    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) allTags
        else allTags.filter { it.contains(searchQuery.trim().removePrefix("#"), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Başlık Çubuğu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Etiketleri Yönet",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${allTags.size} farklı etiket mevcut",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Arama Çubuğu (Eğer 4'ten fazla etiket varsa)
            if (allTags.size > 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Etiket ara...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
            }

            // Etiket Listesi
            if (filteredTags.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Eşleşen etiket bulunamadı" else "Henüz etiket eklenmemiş",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Notlarının içerisine veya başlığına #etiket yazarak etiket oluşturabilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    items(filteredTags, key = { it }) { tag ->
                        val count = tagCounts[tag] ?: 0
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectTag(tag)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "$count not",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { tagToRename = tag },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Yeniden Adlandır",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { tagToDelete = tag },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Etiketi Sil",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Yeniden Adlandırma Dialogu
    tagToRename?.let { oldTag ->
        var newTagName by remember { mutableStateOf(oldTag) }
        val cleanNew = newTagName.removePrefix("#").trim()
        val isValid = cleanNew.isNotBlank() && !cleanNew.equals(oldTag, ignoreCase = true)

        Dialog(onDismissRequest = { tagToRename = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Etiketi Yeniden Adlandır",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "'#$oldTag' etiketi bu etiketi içeren tüm notlarda otomatik güncellenecektir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it.removePrefix("#").replace("\\s+".toRegex(), "") },
                        prefix = { Text("#", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        label = { Text("Yeni Etiket Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { tagToRename = null }) {
                            Text("İptal")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (isValid) {
                                    onRenameTag(oldTag, cleanNew)
                                    tagToRename = null
                                }
                            },
                            enabled = isValid
                        ) {
                            Text("Güncelle")
                        }
                    }
                }
            }
        }
    }

    // Etiketi Silme Onay Dialogu
    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Etiketi Kaldır?") },
            text = {
                Text("'$tag' etiketi tüm notlardan kaldırılacaktır. Notların diğer içerikleri korunacaktır.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTag(tag)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Etiketi Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }
}
