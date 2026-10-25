package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.noteapp.domain.util.GalleryGridCalculator
import java.io.File

@Composable
fun AttachmentList(
    attachments: List<String>,
    onDeleteAttachment: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    isPlayingAudio: Boolean,
    currentPlayingPath: String?,
    playbackSpeed: Float = 1.0f,
    onSeekAudio: ((Int) -> Unit)? = null,
    onCycleSpeed: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    onAnnotateImage: ((String) -> Unit)? = null,
    onRevertImage: ((String) -> Unit)? = null,
    canRevertImage: ((String) -> Boolean)? = null,
    onExtractText: ((String) -> Unit)? = null,
    isExtractingText: Boolean = false,
    audioProgressFraction: Float = 0f,
    audioCurrentPositionMs: Int = 0,
    audioDurationMs: Int = 0,
    onSeekAudioFraction: ((Float) -> Unit)? = null,
    onTrimAudio: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val audioAttachments = remember(attachments) {
        attachments.filter { GalleryGridCalculator.isAudioAttachment(it) }
    }
    val imageAttachments = remember(attachments) {
        attachments.filter { GalleryGridCalculator.isImageAttachment(it) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Ses Kaydı Oynatıcı Kartları
        audioAttachments.forEach { path ->
            val isCurrentPlaying = isPlayingAudio && currentPlayingPath == path
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(onClick = { onPlayAudio(path) }) {
                                Icon(
                                    imageVector = if (isCurrentPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Oynat / Durdur",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Ses (${File(path).name.take(14)}...)",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (isCurrentPlaying) {
                                // -5s Geri Sar
                                TextButton(
                                    onClick = { onSeekAudio?.invoke(-5) },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        "-5s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // +5s İleri Sar
                                TextButton(
                                    onClick = { onSeekAudio?.invoke(5) },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        "+5s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Oynatma Hızı Butonu
                                FilledTonalButton(
                                    onClick = { onCycleSpeed?.invoke() },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(2.dp))
                            }

                            if (onTrimAudio != null) {
                                IconButton(
                                    onClick = { onTrimAudio(path) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Sesi Kırp",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { onDeleteAttachment(path) }) {
                                Icon(Icons.Default.Close, contentDescription = "Sil")
                            }
                        }
                    }

                    // İnteraktif Dalga Formu (Waveform Scrubber)
                    AudioWaveformScrubber(
                        path = path,
                        isPlaying = isCurrentPlaying,
                        progressFraction = if (isCurrentPlaying) audioProgressFraction else 0f,
                        currentPositionMs = if (isCurrentPlaying) audioCurrentPositionMs else 0,
                        durationMs = if (isCurrentPlaying) audioDurationMs else 0,
                        onSeekFraction = { fraction ->
                            onSeekAudioFraction?.invoke(fraction)
                        }
                    )
                }
            }
        }

        // 2. Çoklu Görsel Galeri Izgarası (Collage / Photo Grid)
        if (imageAttachments.isNotEmpty()) {
            when (imageAttachments.size) {
                1 -> {
                    val path = imageAttachments[0]
                    ImageAttachmentCard(
                        path = path,
                        height = if (path.contains("drawing", ignoreCase = true)) 240.dp else 220.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onImageClick = onImageClick,
                        onAnnotateImage = onAnnotateImage,
                        onExtractText = onExtractText,
                        isExtractingText = isExtractingText,
                        onRevertImage = onRevertImage,
                        canRevertImage = canRevertImage,
                        onDeleteAttachment = onDeleteAttachment
                    )
                }
                2 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageAttachmentCard(
                            path = imageAttachments[0],
                            height = 150.dp,
                            modifier = Modifier.weight(1f),
                            onImageClick = onImageClick,
                            onAnnotateImage = onAnnotateImage,
                            onExtractText = onExtractText,
                            isExtractingText = isExtractingText,
                            onRevertImage = onRevertImage,
                            canRevertImage = canRevertImage,
                            onDeleteAttachment = onDeleteAttachment
                        )
                        ImageAttachmentCard(
                            path = imageAttachments[1],
                            height = 150.dp,
                            modifier = Modifier.weight(1f),
                            onImageClick = onImageClick,
                            onAnnotateImage = onAnnotateImage,
                            onExtractText = onExtractText,
                            isExtractingText = isExtractingText,
                            onRevertImage = onRevertImage,
                            canRevertImage = canRevertImage,
                            onDeleteAttachment = onDeleteAttachment
                        )
                    }
                }
                3 -> {
                    // 1 büyük üstte, 2 küçük altta
                    ImageAttachmentCard(
                        path = imageAttachments[0],
                        height = 180.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onImageClick = onImageClick,
                        onAnnotateImage = onAnnotateImage,
                        onExtractText = onExtractText,
                        isExtractingText = isExtractingText,
                        onRevertImage = onRevertImage,
                        canRevertImage = canRevertImage,
                        onDeleteAttachment = onDeleteAttachment
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageAttachmentCard(
                            path = imageAttachments[1],
                            height = 130.dp,
                            modifier = Modifier.weight(1f),
                            onImageClick = onImageClick,
                            onAnnotateImage = onAnnotateImage,
                            onExtractText = onExtractText,
                            isExtractingText = isExtractingText,
                            onRevertImage = onRevertImage,
                            canRevertImage = canRevertImage,
                            onDeleteAttachment = onDeleteAttachment
                        )
                        ImageAttachmentCard(
                            path = imageAttachments[2],
                            height = 130.dp,
                            modifier = Modifier.weight(1f),
                            onImageClick = onImageClick,
                            onAnnotateImage = onAnnotateImage,
                            onExtractText = onExtractText,
                            isExtractingText = isExtractingText,
                            onRevertImage = onRevertImage,
                            canRevertImage = canRevertImage,
                            onDeleteAttachment = onDeleteAttachment
                        )
                    }
                }
                else -> {
                    // 4 veya daha fazla görsel: 2x2 grid ve 4. kart üzerinde "+X" rozeti
                    val remaining = if (imageAttachments.size > 4) imageAttachments.size - 3 else 0
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageAttachmentCard(
                                path = imageAttachments[0],
                                height = 135.dp,
                                modifier = Modifier.weight(1f),
                                onImageClick = onImageClick,
                                onAnnotateImage = onAnnotateImage,
                                onExtractText = onExtractText,
                                isExtractingText = isExtractingText,
                                onRevertImage = onRevertImage,
                                canRevertImage = canRevertImage,
                                onDeleteAttachment = onDeleteAttachment
                            )
                            ImageAttachmentCard(
                                path = imageAttachments[1],
                                height = 135.dp,
                                modifier = Modifier.weight(1f),
                                onImageClick = onImageClick,
                                onAnnotateImage = onAnnotateImage,
                                onExtractText = onExtractText,
                                isExtractingText = isExtractingText,
                                onRevertImage = onRevertImage,
                                canRevertImage = canRevertImage,
                                onDeleteAttachment = onDeleteAttachment
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageAttachmentCard(
                                path = imageAttachments[2],
                                height = 135.dp,
                                modifier = Modifier.weight(1f),
                                onImageClick = onImageClick,
                                onAnnotateImage = onAnnotateImage,
                                onExtractText = onExtractText,
                                isExtractingText = isExtractingText,
                                onRevertImage = onRevertImage,
                                canRevertImage = canRevertImage,
                                onDeleteAttachment = onDeleteAttachment
                            )
                            ImageAttachmentCard(
                                path = imageAttachments[3],
                                height = 135.dp,
                                modifier = Modifier.weight(1f),
                                overflowCount = remaining,
                                onImageClick = onImageClick,
                                onAnnotateImage = onAnnotateImage,
                                onExtractText = onExtractText,
                                isExtractingText = isExtractingText,
                                onRevertImage = onRevertImage,
                                canRevertImage = canRevertImage,
                                onDeleteAttachment = onDeleteAttachment
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    path: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    overflowCount: Int = 0,
    onImageClick: (String) -> Unit,
    onAnnotateImage: ((String) -> Unit)?,
    onExtractText: ((String) -> Unit)?,
    isExtractingText: Boolean,
    onRevertImage: ((String) -> Unit)?,
    canRevertImage: ((String) -> Boolean)?,
    onDeleteAttachment: (String) -> Unit
) {
    val isDrawing = path.contains("drawing", ignoreCase = true)

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isDrawing) Modifier
                    .background(Color.White.copy(alpha = 0.96f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onImageClick(path) }
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = if (isDrawing) "El Çizimi" else "Not Görseli",
            contentScale = if (isDrawing) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Taşma Rozeti (+X Görsel)
        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflowCount",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
            }
        }

        // Sol Alt Düzenleme İpucu Rozeti
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                val isEdited = canRevertImage?.invoke(path) == true
                Icon(
                    imageVector = if (isDrawing) Icons.Default.Brush else if (isEdited) Icons.Default.Edit else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isDrawing) "Çizim" else if (isEdited) "Düzenlendi" else "Görüntüle",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Üst Butonlar (Sil, Geri Al, OCR, Çizim)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onAnnotateImage != null && !isDrawing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = { onAnnotateImage(path) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = "Üzerine Çiz & İşaretle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (onExtractText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = { onExtractText(path) },
                        modifier = Modifier.fillMaxSize(),
                        enabled = !isExtractingText
                    ) {
                        if (isExtractingText) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Metni Çıkar (OCR)",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (canRevertImage?.invoke(path) == true && onRevertImage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = { onRevertImage(path) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Düzenlemeyi Geri Al",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = CircleShape,
                shadowElevation = 2.dp,
                modifier = Modifier.size(32.dp)
            ) {
                IconButton(
                    onClick = { onDeleteAttachment(path) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Görseli Sil", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
