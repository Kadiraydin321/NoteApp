package com.example.noteapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
                // Görsel Kartı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Not Görseli",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { onDeleteAttachment(path) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Görseli Sil")
                    }
                }
            }
        }
    }
}
