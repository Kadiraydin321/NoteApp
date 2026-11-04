package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun FocusHudOverlay(
    wordCount: Int,
    characterCount: Int,
    onExitZenMode: () -> Unit,
    isTtsSpeaking: Boolean = false,
    onToggleTts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pomodoro State
    var isTimerRunning by remember { mutableStateOf(false) }
    var isBreakMode by remember { mutableStateOf(false) } // false: 25m focus, true: 5m break
    var remainingSeconds by remember { mutableStateOf(25 * 60) }

    // Pomodoro Timer Loop
    LaunchedEffect(isTimerRunning, remainingSeconds) {
        if (isTimerRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isTimerRunning = false
                // Switch mode
                isBreakMode = !isBreakMode
                remainingSeconds = if (isBreakMode) 5 * 60 else 25 * 60
            }
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ana Kapsül (Floating HUD)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.clip(RoundedCornerShape(28.dp))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pomodoro Bölümü
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBreakMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        isTimerRunning = !isTimerRunning
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTimerRunning) "Duraklat" else "Başlat",
                            tint = if (isBreakMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isBreakMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Pomodoro Sıfırla
                IconButton(
                    onClick = {
                        isTimerRunning = false
                        remainingSeconds = if (isBreakMode) 5 * 60 else 25 * 60
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sıfırla",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                )

                // Sesli Okuma (TTS Hoparlör) Butonu
                Surface(
                    shape = CircleShape,
                    color = if (isTtsSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = if (isTtsSpeaking) "Sesli Okumayı Durdur" else "Sesli Oku",
                            onClick = onToggleTts
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTtsSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isTtsSpeaking) "Sesli Okumayı Durdur" else "Sesli Oku",
                            tint = if (isTtsSpeaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                )

                // Canlı Kelime Sayacı
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$wordCount kelime",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$characterCount kark.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                )

                // Çıkış Butonu
                IconButton(
                    onClick = onExitZenMode,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Odaklanma Modundan Çık",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
