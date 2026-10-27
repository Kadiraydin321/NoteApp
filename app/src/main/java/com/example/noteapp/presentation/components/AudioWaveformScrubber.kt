package com.example.noteapp.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.WaveformHelper
import java.io.File

/**
 * Ses notları için interaktif, hassas dokunarak ve sürükleyerek sarılabilen dalga formu (Modern Waveform Scrubber).
 * Aktif oynatma ilerleme rengi, zarif çubuk yuvarlatmaları, sürükleme anında görsel geri bildirim ve süre sayacı sunar.
 */
@Composable
fun AudioWaveformScrubber(
    path: String,
    isPlaying: Boolean,
    progressFraction: Float,
    currentPositionMs: Int,
    durationMs: Int,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 36
) {
    // Dosya boyutu ve adına göre deterministik dalga formu profili
    val waveform = remember(path, barCount) {
        val file = File(path)
        val seed = if (file.exists() && file.length() > 0) file.length() else path.hashCode().toLong()
        WaveformHelper.generateConsistentWaveform(seed, barCount)
    }

    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val effectiveFraction = if (isDragging) dragFraction else progressFraction
    val activeBars = WaveformHelper.calculateActiveBarCount(effectiveFraction, barCount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { componentSize = it }
            .pointerInput(componentSize) {
                if (componentSize.width > 0) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / componentSize.width).coerceIn(0f, 1f)
                        onSeekFraction(fraction)
                    }
                }
            }
            .pointerInput(componentSize) {
                if (componentSize.width > 0) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / componentSize.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / componentSize.width).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onSeekFraction(fraction)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeekFraction(dragFraction)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
            }
            .padding(vertical = 4.dp)
    ) {
        // Dalga Formu Çubukları & İlerleme Çizgisi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                waveform.forEachIndexed { index, amp ->
                    val isActive = index < activeBars
                    val barColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                        animationSpec = tween(durationMillis = 120),
                        label = "barColor"
                    )

                    val targetHeight = (amp * 30).dp.coerceIn(5.dp, 32.dp)
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = tween(durationMillis = 120),
                        label = "barHeight"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(animatedHeight)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(barColor)
                    )
                }
            }

            // Sürükleme veya aktif oynatma scrubber kılavuz çizgisi
            if (componentSize.width > 0 && effectiveFraction > 0.01f) {
                val scrubberX = (componentSize.width * effectiveFraction).coerceIn(0f, componentSize.width.toFloat())
                Box(
                    modifier = Modifier
                        .offset(x = (scrubberX / (componentSize.width.toFloat() / (componentSize.width / 2.5f))).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Süre Göstergesi (Geçen / Toplam)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayCurrent = if (isDragging) {
                (durationMs * dragFraction).toInt()
            } else currentPositionMs

            Text(
                text = WaveformHelper.formatTime(displayCurrent),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isPlaying || isDragging) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isPlaying || isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (durationMs > 0) WaveformHelper.formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
