package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.CalendarNoteGrouper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Not Ajandası & İnteraktif Takvim Çubuğu.
 * Notların günlere göre dağılımını gösterir ve seçilen güne göre not listesini anında filtreler.
 */
@Composable
fun CalendarAgendaBar(
    notes: List<Note>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    filterMode: CalendarNoteGrouper.DateFilterMode = CalendarNoteGrouper.DateFilterMode.CREATED_DATE
) {
    val today = remember { LocalDate.now() }
    var currentMonth by remember {
        mutableStateOf(selectedDate?.let { YearMonth.from(it) } ?: YearMonth.now())
    }
    var isMonthGridExpanded by remember { mutableStateOf(false) }

    val monthActivity = remember(notes, currentMonth, filterMode) {
        CalendarNoteGrouper.calculateMonthActivity(notes, currentMonth, filterMode)
    }

    val weekDays = remember(selectedDate, today) {
        CalendarNoteGrouper.getWeekDates(selectedDate ?: today)
    }

    val monthCalendarDays = remember(currentMonth, selectedDate, monthActivity, today) {
        CalendarNoteGrouper.getMonthCalendarDays(currentMonth, selectedDate, monthActivity, today)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Başlık Satırı: Ay & Yıl + Gezinme Butonları + Genişletme
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    val monthTitle = currentMonth.month.getDisplayName(TextStyle.FULL, Locale("tr"))
                        .replaceFirstChar { it.uppercase() }
                    Text(
                        text = "$monthTitle ${currentMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Bugün Çipi
                    if (selectedDate != today || currentMonth != YearMonth.now()) {
                        AssistChip(
                            onClick = {
                                currentMonth = YearMonth.now()
                                onDateSelected(today)
                            },
                            label = { Text("Bugün", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentMonth = currentMonth.minusMonths(1)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Önceki Ay",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentMonth = currentMonth.plusMonths(1)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Sonraki Ay",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { isMonthGridExpanded = !isMonthGridExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMonthGridExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isMonthGridExpanded) "Haftalık Görünüme Dön" else "Aylık Takvimi Aç",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Görünüm 1: Kompakt Haftalık Şerit (Varsayılan)
            AnimatedVisibility(
                visible = !isMonthGridExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEach { date ->
                        val isSelected = date == selectedDate
                        val isDayToday = date == today
                        val noteCount = monthActivity[date] ?: 0

                        DayItem(
                            date = date,
                            isSelected = isSelected,
                            isToday = isDayToday,
                            noteCount = noteCount,
                            isDimmed = false,
                            onClick = {
                                if (isSelected) {
                                    onDateSelected(null) // İkinci dokunuş filtreyi kaldırır
                                } else {
                                    onDateSelected(date)
                                }
                            }
                        )
                    }
                }
            }

            // Görünüm 2: Genişletilmiş Aylık Takvim Izgarası
            AnimatedVisibility(
                visible = isMonthGridExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Hafta günleri başlığı (Pzt, Sal, Çar, Per, Cum, Cmt, Paz)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY,
                            DayOfWeek.SATURDAY,
                            DayOfWeek.SUNDAY
                        ).forEach { dayOfWeek ->
                            val label = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("tr"))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Günler Matrisi (7 sütun)
                    val chunkedWeeks = monthCalendarDays.chunked(7)
                    chunkedWeeks.forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { calDay ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DayItem(
                                        date = calDay.date,
                                        isSelected = calDay.isSelected,
                                        isToday = calDay.isToday,
                                        noteCount = calDay.noteCount,
                                        isDimmed = !calDay.isCurrentMonth,
                                        onClick = {
                                            if (calDay.isSelected) {
                                                onDateSelected(null)
                                            } else {
                                                onDateSelected(calDay.date)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Seçilen Gün Filtresi Aktif Rozeti
            if (selectedDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val filteredCount = notes.count { note ->
                    CalendarNoteGrouper.getNoteDate(note, filterMode) == selectedDate
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 ${CalendarNoteGrouper.formatDateLabel(selectedDate, today)}: $filteredCount Not",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "Filtreyi Temizle ✕",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onDateSelected(null) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    noteCount: Int,
    isDimmed: Boolean,
    onClick: () -> Unit
) {
    val dayOfWeekStr = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("tr"))
    val dayNumberStr = date.dayOfMonth.toString()

    val containerBg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isDimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .size(width = 38.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayOfWeekStr.take(3),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else textColor.copy(alpha = 0.7f)
        )

        Text(
            text = dayNumberStr,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor
        )

        // Etkinlik Noktası (Not varsa)
        if (noteCount > 0) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        } else {
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
