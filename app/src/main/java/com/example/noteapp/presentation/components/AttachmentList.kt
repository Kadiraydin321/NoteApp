package com.example.noteapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun AttachmentList(
    attachments: List<String>,
    onDeleteAttachment: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    isPlayingAudio: Boolean,
    currentPlayingPath: String?,
    onImageClick: (String) -> Unit = {},
    onRevertImage: ((String) -> Unit)? = null,
    canRevertImage: ((String) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { path ->
            val isAudio = path.endsWith(".mp4") || path.endsWith(".m4a") || path.endsWith(".mp3")
            if (isAudio) {
                // Ses Kaydı Oynatıcı Kartı
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPlayAudio(path) }) {
                                Icon(
                                    imageVector = if (isPlayingAudio && currentPlayingPath == path) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Oynat / Durdur",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Ses Kaydı (${File(path).name.take(15)}...)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        IconButton(onClick = { onDeleteAttachment(path) }) {
                            Icon(Icons.Default.Close, contentDescription = "Sil")
                        }
                    }
                }
            } else {
                // Görsel Kartı (Düzenleme için Tıklanabilir)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(path) }
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Not Görseli",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Düzenleme İpucu Rozeti
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val isEdited = canRevertImage?.invoke(path) == true
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEdited) "Düzenlendi (Geri alınabilir)" else "Düzenlemek için dokun",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Üst Butonlar (Sil ve Geri Al)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (canRevertImage?.invoke(path) == true && onRevertImage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = CircleShape,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(36.dp)
                            ) {
                                IconButton(
                                    onClick = { onRevertImage(path) },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Düzenlemeyi Geri Al (Eski haline dön)",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = CircleShape,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = { onDeleteAttachment(path) },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Görseli Sil", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
