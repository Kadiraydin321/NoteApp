package com.example.noteapp.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.WaveformHelper

/**
 * Ses kaydı sırasında ekranda beliren, mikrofon genliğine göre canlı dalgalanan
 * ve geçen süreyi gösteren görselleştirici bar (Live Audio Recording Visualizer).
 */
@Composable
fun AudioRecordingVisualizerBar(
    durationSec: Int,
    amplitudes: List<Float>,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 20
) {
    // Kayıt esnasında yanıp sönen kırmızı nokta animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Son 'barCount' adet genliği al ve eksikse taban değerle tamamla
    val displayAmplitudes = remember(amplitudes) {
        val lastAmps = amplitudes.takeLast(barCount)
        val padding = List((barCount - lastAmps.size).coerceAtLeast(0)) { 0.08f }
        padding + lastAmps
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        ),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol Bölüm: Kırmızı REC Noktası ve Sayaç
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = pulseAlpha))
                )

                Text(
                    text = "REC",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                )

                Text(
                    text = WaveformHelper.formatTime(durationSec * 1000),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )
            }

            // Orta Bölüm: Canlı Mikrofon Dalga Çubukları
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayAmplitudes.forEach { amp ->
                    val barHeight = (amp * 28f).coerceIn(3f, 28f).dp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }

            // Sağ Bölüm: İptal ve Durdur/Kaydet Butonları
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kaydı İptal Et",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Kaydı Tamamla",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}
