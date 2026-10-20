package com.example.noteapp.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.WaveformHelper
import java.io.File

/**
 * Ses notları için interaktif, dokunarak sarılabilen dalga formu (Waveform Scrubber).
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
    barCount: Int = 32
) {
    // Dosya boyutuna ve adına göre deterministik dalga formu profili
    val waveform = remember(path) {
        val file = File(path)
        val seed = if (file.exists() && file.length() > 0) file.length() else path.hashCode().toLong()
        WaveformHelper.generateConsistentWaveform(seed, barCount)
    }

    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val activeBars = if (isPlaying) {
        WaveformHelper.calculateActiveBarCount(progressFraction, barCount)
    } else 0

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
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / componentSize.width).coerceIn(0f, 1f)
                        onSeekFraction(fraction)
                    }
                }
            }
            .padding(vertical = 4.dp)
    ) {
        // Dalga Formu Çubukları
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveform.forEachIndexed { index, amp ->
                val isActive = index < activeBars
                val barColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    animationSpec = tween(durationMillis = 150),
                    label = "barColor"
                )

                val targetHeight = (amp * 26).dp.coerceIn(4.dp, 28.dp)
                val animatedHeight by animateDpAsState(
                    targetValue = targetHeight,
                    label = "barHeight"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(animatedHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Süre Göstergesi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPlaying) WaveformHelper.formatTime(currentPositionMs) else "00:00",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (durationMs > 0) WaveformHelper.formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
