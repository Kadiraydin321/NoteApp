package com.example.noteapp.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.AudioWaveformTrimmer
import com.example.noteapp.domain.util.TrimWindow

/**
 * Ses Kaydı Dalga Formu Hassas Kırpıcı Alt Sayfası.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerBottomSheet(
    audioPath: String,
    totalDurationMs: Long,
    amplitudes: List<Int> = emptyList(),
    isPlayingPreview: Boolean = false,
    onDismissRequest: () -> Unit,
    onTogglePreview: (startMs: Long, endMs: Long) -> Unit,
    onConfirmTrim: (TrimWindow) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderRange by remember { mutableStateOf(0f..1f) }

    val trimWindow = remember(sliderRange, totalDurationMs) {
        AudioWaveformTrimmer.calculateTrimWindow(
            totalDurationMs = totalDurationMs,
            rawStartFraction = sliderRange.start,
            rawEndFraction = sliderRange.endInclusive
        )
    }

    // Görüntülenecek genlik dizisi (yoksa varsayılan dalga formu oluştur)
    val displayAmplitudes = remember(amplitudes) {
        if (amplitudes.isNotEmpty()) amplitudes
        else listOf(20, 45, 60, 30, 80, 95, 70, 40, 60, 85, 100, 75, 50, 65, 90, 80, 35, 55, 70, 40, 60, 85, 95, 70, 50, 40, 30, 20)
    }

    val fileName = remember(audioPath) {
        audioPath.substringAfterLast("/").substringAfterLast("\\").ifBlank { "Ses Kaydı" }
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
            // Başlık & Kapatma Butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                        Text(text = "✂️", fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = "Ses Kaydı Kırpıcı",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            // Süre İstatistik Rozetleri
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Başlangıç",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AudioWaveformTrimmer.formatDuration(trimWindow.startMs),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Kırpılan Süre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = AudioWaveformTrimmer.formatDuration(trimWindow.durationMs),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Bitiş",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AudioWaveformTrimmer.formatDuration(trimWindow.endMs),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Görsel Dalga Formu & Kırpma Aralığı Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val count = displayAmplitudes.size
                    if (count == 0) return@Canvas

                    val barWidth = (size.width / count) * 0.7f
                    val spacing = (size.width / count) * 0.3f
                    val maxAmp = displayAmplitudes.maxOrNull()?.coerceAtLeast(1) ?: 100

                    displayAmplitudes.forEachIndexed { i, amp ->
                        val fraction = i.toFloat() / count.toFloat()
                        val isInsideTrim = fraction >= trimWindow.startFraction && fraction <= trimWindow.endFraction

                        val barHeight = ((amp.toFloat() / maxAmp.toFloat()) * size.height).coerceAtLeast(6f)
                        val x = i * (barWidth + spacing)
                        val y = (size.height - barHeight) / 2f

                        drawRoundRect(
                            color = if (isInsideTrim) primaryColor else outlineColor.copy(alpha = 0.4f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }

                    // Başlangıç ve bitiş dikey çizgileri
                    val startX = trimWindow.startFraction * size.width
                    val endX = trimWindow.endFraction * size.width
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, 0f),
                        end = Offset(startX, size.height),
                        strokeWidth = 4f
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(endX, 0f),
                        end = Offset(endX, size.height),
                        strokeWidth = 4f
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Çift Uçlu Hassas Aralık Kaydırıcısı (RangeSlider)
            RangeSlider(
                value = sliderRange,
                onValueChange = { sliderRange = it },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Eylem Butonları (Önizle & Onayla)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onTogglePreview(trimWindow.startMs, trimWindow.endMs) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPlayingPreview) "Durdur" else "Aralığı Dinle")
                }

                Button(
                    onClick = {
                        onConfirmTrim(trimWindow)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aralığı Kırp")
                }
            }
        }
    }
}
