package com.example.noteapp.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.WaveformHelper

/**
 * Modern Material Design 3 ses kayıt kontrol ve canlı görselleştirici çubuğu (Live Audio Recording Bar).
 * Canlı nabız animasyonu, çift katmanlı kayıt göstergesi, ortalanmış dinamik dalga formu,
 * hassas süre sayacı ve duraklat / devam ettir / tamamla / iptal kontrolleri sunar.
 */
@Composable
fun AudioRecordingVisualizerBar(
    durationSec: Int,
    amplitudes: List<Float>,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onPauseToggle: (() -> Unit)? = null,
    isPaused: Boolean = false,
    barCount: Int = 26
) {
    // Kayıt nabız animasyonları (Pulse Glow Animation)
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Son 'barCount' adet genliği hazırla
    val displayAmplitudes = remember(amplitudes, barCount) {
        val lastAmps = amplitudes.takeLast(barCount)
        val fillCount = (barCount - lastAmps.size).coerceAtLeast(0)
        val padding = List(fillCount) { 0.08f }
        padding + lastAmps
    }

    val recordColor = if (isPaused) Color(0xFFF59E0B) else MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = recordColor.copy(alpha = if (isPaused) 0.35f else 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol Bölüm: Nabız Göstergesi + Sayaç
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (!isPaused) {
                        // Dış parıltı çemberi
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(recordColor.copy(alpha = pulseAlpha))
                        )
                    }
                    // İç çekirdek nokta
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(recordColor)
                    )
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = if (isPaused) "DURAKLATILDI" else "KAYDEDİLİYOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp
                        ),
                        color = recordColor
                    )

                    Text(
                        text = WaveformHelper.formatTime(durationSec * 1000),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Orta Bölüm: Simetrik Ortalanmış Canlı Dalga Formu
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayAmplitudes.forEach { rawAmp ->
                    val animatedAmp by animateFloatAsState(
                        targetValue = if (isPaused) 0.12f else rawAmp.coerceIn(0.08f, 1.0f),
                        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                        label = "ampBar"
                    )
                    val barHeight = (animatedAmp * 34f).coerceIn(4f, 34f).dp

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isPaused) {
                                        listOf(Color(0xFFF59E0B).copy(alpha = 0.5f), Color(0xFFF59E0B).copy(alpha = 0.8f))
                                    } else {
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error)
                                    }
                                )
                            )
                    )
                }
            }

            // Sağ Bölüm: İptal, Duraklat/Devam ve Tamamla Butonları (Min 48dp Touch Targets)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. İptal / Sil Butonu
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Kaydı İptal Et",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. Duraklat / Devam Et Butonu (Opsiyonel)
                if (onPauseToggle != null) {
                    FilledTonalIconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isPaused) Color(0xFFF59E0B).copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isPaused) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Kayda Devam Et" else "Kaydı Duraklat",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Tamamla / Kaydet Butonu
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Kaydı Tamamla",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
