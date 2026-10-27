package com.example.noteapp.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as DrawBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
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
    onSetPlaybackSpeed: ((Float) -> Unit)? = null,
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Ses Kaydı Oynatıcı Kartları (Modern Audio Player Cards)
        audioAttachments.forEach { path ->
            val isCurrentPlaying = isPlayingAudio && currentPlayingPath == path
            ModernAudioPlayerCard(
                path = path,
                isPlaying = isCurrentPlaying,
                playbackSpeed = playbackSpeed,
                audioProgressFraction = if (isCurrentPlaying) audioProgressFraction else 0f,
                audioCurrentPositionMs = if (isCurrentPlaying) audioCurrentPositionMs else 0,
                audioDurationMs = if (isCurrentPlaying) audioDurationMs else 0,
                onPlayToggle = { onPlayAudio(path) },
                onSeekBy = onSeekAudio,
                onSeekFraction = onSeekAudioFraction,
                onCycleSpeed = onCycleSpeed,
                onSetPlaybackSpeed = onSetPlaybackSpeed,
                onTrim = onTrimAudio?.let { { it(path) } },
                onDelete = { onDeleteAttachment(path) }
            )
        }

        // 2. Çoklu Görsel ve Çizim Galeri Izgarası (Collage Grid)
        if (imageAttachments.isNotEmpty()) {
            when (imageAttachments.size) {
                1 -> {
                    val path = imageAttachments[0]
                    ImageAttachmentCard(
                        path = path,
                        height = if (path.contains("drawing", ignoreCase = true) || path.contains("DRAW_")) 240.dp else 220.dp,
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
                            height = 160.dp,
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
                            height = 160.dp,
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        }
                    }
                }
                else -> {
                    // 4 veya daha fazla görsel: 2x2 grid ve 4. kart üzerinde "+X" cam efekti
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
                                height = 140.dp,
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
                                height = 140.dp,
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
                                height = 140.dp,
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
                                height = 140.dp,
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

/**
 * Modern Material Design 3 Ses Oynatıcı Kartı (Modern Audio Player Card)
 * Oynat / Duraklat butonu, dalga formu seekbar'ı, 1.0x / 1.5x / 2.0x hız seçicisi,
 * -5s / +5s sarma butonları ve kırp / sil kontrolleri.
 */
@Composable
private fun ModernAudioPlayerCard(
    path: String,
    isPlaying: Boolean,
    playbackSpeed: Float,
    audioProgressFraction: Float,
    audioCurrentPositionMs: Int,
    audioDurationMs: Int,
    onPlayToggle: () -> Unit,
    onSeekBy: ((Int) -> Unit)?,
    onSeekFraction: ((Float) -> Unit)?,
    onCycleSpeed: (() -> Unit)?,
    onSetPlaybackSpeed: ((Float) -> Unit)?,
    onTrim: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speedOptions = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    val file = remember(path) { File(path) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isPlaying) 4.dp else 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Üst Satır: Oynat/Duraklat Butonu, Dosya Bilgisi ve Aksiyonlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol: Oynat/Duraklat + Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    FilledIconButton(
                        onClick = onPlayToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Ses Kaydı",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = file.name.take(18) + (if (file.name.length > 18) "..." else ""),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sağ: Hız Seçici, Sarma ve Eylemler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isPlaying) {
                        // -5s Geri Sar
                        IconButton(
                            onClick = { onSeekBy?.invoke(-5) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay5,
                                contentDescription = "5 saniye geri",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // +5s İleri Sar
                        IconButton(
                            onClick = { onSeekBy?.invoke(5) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward5,
                                contentDescription = "5 saniye ileri",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Oynatma Hızı Seçici Menü (Pill / Chip)
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .height(30.dp)
                                    .clickable {
                                        if (onSetPlaybackSpeed != null) {
                                            showSpeedMenu = true
                                        } else {
                                            onCycleSpeed?.invoke()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                speedOptions.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${speed}x",
                                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                                color = if (playbackSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSetPlaybackSpeed?.invoke(speed) ?: onCycleSpeed?.invoke()
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sesi Kırp (Trim)
                    if (onTrim != null) {
                        IconButton(
                            onClick = onTrim,
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

                    // Kaydı Sil
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Kaydı Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Alt Kısım: Dalga Formu ve Seekbar
            AudioWaveformScrubber(
                path = path,
                isPlaying = isPlaying,
                progressFraction = audioProgressFraction,
                currentPositionMs = audioCurrentPositionMs,
                durationMs = audioDurationMs,
                onSeekFraction = { fraction ->
                    onSeekFraction?.invoke(fraction)
                }
            )
        }
    }
}

/**
 * Modern Görsel & Çizim Önizleme Kartı (Image & Drawing Thumbnail Card)
 */
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
    val isDrawing = path.contains("drawing", ignoreCase = true) || path.contains("DRAW_")

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDrawing) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                width = 1.dp,
                color = if (isDrawing) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onImageClick(path) }
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = if (isDrawing) "El Çizimi" else "Not Görseli",
            contentScale = if (isDrawing) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Karartma / Cam Görünümlü Taşma Rozeti (+X Görsel)
        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflowCount",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        // Sol Alt Tür Rozeti (Çizim / Düzenlendi / Önizleme)
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
            ) {
                val isEdited = canRevertImage?.invoke(path) == true
                Icon(
                    imageVector = if (isDrawing) Icons.Default.Brush else if (isEdited) Icons.Default.Edit else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isDrawing) "Çizim" else if (isEdited) "Düzenlendi" else "Görsel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Sağ Üst Eylem Butonları Kapsülü (Çakışmayan, min 36dp - 44dp dokunma alanı)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. Üzerine Çiz / İşaretle (Çizim olmayan görseller için)
            if (onAnnotateImage != null && !isDrawing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = CircleShape,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(
                        onClick = { onAnnotateImage(path) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Üzerine Çiz & İşaretle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. Metni Çıkar (OCR)
            if (onExtractText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = CircleShape,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(
                        onClick = { onExtractText(path) },
                        modifier = Modifier.fillMaxSize(),
                        enabled = !isExtractingText
                    ) {
                        if (isExtractingText) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Metni Çıkar (OCR)",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Düzenlemeyi Geri Al
            if (canRevertImage?.invoke(path) == true && onRevertImage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = CircleShape,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(
                        onClick = { onRevertImage(path) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Düzenlemeyi Geri Al",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 4. Görseli Sil
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                shadowElevation = 3.dp,
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(
                    onClick = { onDeleteAttachment(path) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Görseli Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
