package com.example.noteapp.desktop.presentation.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReminderDialog(
    initialReminderTime: Long?,
    onSaveReminder: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = remember {
        Calendar.getInstance().apply {
            if (initialReminderTime != null && initialReminderTime > System.currentTimeMillis()) {
                timeInMillis = initialReminderTime
            } else {
                add(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        }
    }

    var selectedYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(cal.get(Calendar.MINUTE)) }

    fun calculateMillis(): Long {
        val target = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis
    }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr", "TR"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Hatırlatıcı Belirle")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Aktif Zaman Özeti
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        Column {
                            Text("Hatırlatma Zamanı", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text(
                                dateFormat.format(Date(calculateMillis())),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                            )
                        }
                    }
                }

                // Hızlı Seçimler
                Text("HIZLI SEÇENEKLER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = Color(0xFF94A3B8))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Bugün Akşam (19:00)
                    QuickOptionChip(
                        label = "Bugün 19:00",
                        onClick = {
                            val now = Calendar.getInstance()
                            selectedYear = now.get(Calendar.YEAR)
                            selectedMonth = now.get(Calendar.MONTH)
                            selectedDay = now.get(Calendar.DAY_OF_MONTH)
                            selectedHour = 19
                            selectedMinute = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // Yarın Sabah (09:00)
                    QuickOptionChip(
                        label = "Yarın 09:00",
                        onClick = {
                            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                            selectedYear = tomorrow.get(Calendar.YEAR)
                            selectedMonth = tomorrow.get(Calendar.MONTH)
                            selectedDay = tomorrow.get(Calendar.DAY_OF_MONTH)
                            selectedHour = 9
                            selectedMinute = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // Yarın Akşam (19:00)
                    QuickOptionChip(
                        label = "Yarın 19:00",
                        onClick = {
                            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                            selectedYear = tomorrow.get(Calendar.YEAR)
                            selectedMonth = tomorrow.get(Calendar.MONTH)
                            selectedDay = tomorrow.get(Calendar.DAY_OF_MONTH)
                            selectedHour = 19
                            selectedMinute = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Saat ve Dakika Seçimi
                Text("SAAT VE DAKİKA AYARLA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    NumberPicker(
                        value = selectedHour,
                        range = 0..23,
                        onValueChange = { selectedHour = it },
                        label = "Saat"
                    )
                    Text(" : ", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp))
                    NumberPicker(
                        value = selectedMinute,
                        range = 0..59,
                        step = 5,
                        onValueChange = { selectedMinute = it },
                        label = "Dakika"
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialReminderTime != null) {
                    OutlinedButton(
                        onClick = {
                            onSaveReminder(null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kaldır")
                    }
                }
                Button(
                    onClick = {
                        val millis = calculateMillis()
                        onSaveReminder(millis)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Kaydet")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun QuickOptionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Color(0xFFCBD5E1))
        }
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = {
                    val next = if (value - step < range.first) range.last else value - step
                    onValueChange(next)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Text("-", style = MaterialTheme.typography.titleMedium, color = Color(0xFF94A3B8))
            }
            Text(
                text = String.format("%02d", value),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = {
                    val next = if (value + step > range.last) range.first else value + step
                    onValueChange(next)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = Color(0xFF94A3B8))
            }
        }
    }
}
