package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.Note
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Takvim ve Tarih Bazlı Not Ajandası Motoru.
 * Notları tarihlere göre gruplar, haftalık/aylık takvim günlerini oluşturur ve etkinlik yoğunluk haritasını hesaplar.
 */
object CalendarNoteGrouper {

    enum class DateFilterMode {
        CREATED_DATE,
        UPDATED_DATE,
        REMINDER_DATE
    }

    data class CalendarDay(
        val date: LocalDate,
        val isCurrentMonth: Boolean,
        val isToday: Boolean,
        val isSelected: Boolean,
        val noteCount: Int
    )

    data class DateGroup(
        val date: LocalDate,
        val label: String,
        val notes: List<Note>
    )

    /**
     * Epoch milisaniyeyi belirtilen zaman diliminde [LocalDate] nesnesine çevirir.
     */
    fun toLocalDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    /**
     * Belirtilen filtre moduna göre notun tarihini döndürür.
     */
    fun getNoteDate(note: Note, mode: DateFilterMode, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
        return when (mode) {
            DateFilterMode.CREATED_DATE -> toLocalDate(note.timestamp, zoneId)
            DateFilterMode.UPDATED_DATE -> toLocalDate(
                if (note.updatedAt > 0) note.updatedAt else note.timestamp,
                zoneId
            )
            DateFilterMode.REMINDER_DATE -> note.reminderTime?.let { toLocalDate(it, zoneId) }
        }
    }

    /**
     * Notları tarihlerine göre gruplar ve en yeniden eskiye sıralar.
     */
    fun groupNotesByDate(
        notes: List<Note>,
        mode: DateFilterMode = DateFilterMode.CREATED_DATE,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId)
    ): List<DateGroup> {
        return notes
            .mapNotNull { note ->
                val date = getNoteDate(note, mode, zoneId)
                if (date != null) date to note else null
            }
            .groupBy({ it.first }, { it.second })
            .map { (date, noteList) ->
                DateGroup(
                    date = date,
                    label = formatDateLabel(date, today),
                    notes = noteList.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.date }
    }

    /**
     * Belirli bir güne ait notları filtreler.
     */
    fun filterNotesByDate(
        notes: List<Note>,
        targetDate: LocalDate,
        mode: DateFilterMode = DateFilterMode.CREATED_DATE,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Note> {
        return notes.filter { note ->
            getNoteDate(note, mode, zoneId) == targetDate
        }
    }

    /**
     * Belirtilen aydaki tüm günler için not aktivite/yoğunluk sayısını hesaplar.
     */
    fun calculateMonthActivity(
        notes: List<Note>,
        yearMonth: YearMonth,
        mode: DateFilterMode = DateFilterMode.CREATED_DATE,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, Int> {
        val activity = mutableMapOf<LocalDate, Int>()
        for (note in notes) {
            val date = getNoteDate(note, mode, zoneId)
            if (date != null && YearMonth.from(date) == yearMonth) {
                activity[date] = (activity[date] ?: 0) + 1
            }
        }
        return activity
    }

    /**
     * [centerDate] gününü içeren ve Pazartesi ile başlayan 7 günlük haftayı döndürür.
     */
    fun getWeekDates(centerDate: LocalDate): List<LocalDate> {
        val startOfWeek = centerDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { startOfWeek.plusDays(it) }
    }

    /**
     * Aylık takvim görünümü için Pazartesi'den Pazar'a tamamlanmış tam ızgara günlerini üretir.
     */
    fun getMonthCalendarDays(
        yearMonth: YearMonth,
        selectedDate: LocalDate?,
        activityMap: Map<LocalDate, Int>,
        today: LocalDate = LocalDate.now()
    ): List<CalendarDay> {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()

        val calendarStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val calendarEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        val days = mutableListOf<CalendarDay>()
        var curr = calendarStart
        while (!curr.isAfter(calendarEnd)) {
            days.add(
                CalendarDay(
                    date = curr,
                    isCurrentMonth = curr.month == yearMonth.month && curr.year == yearMonth.year,
                    isToday = curr == today,
                    isSelected = curr == selectedDate,
                    noteCount = activityMap[curr] ?: 0
                )
            )
            curr = curr.plusDays(1)
        }
        return days
    }

    /**
     * Kullanıcı dostu tarih etiketi oluşturur ("Bugün", "Dün", "Yarın", "21 Eki, Çar").
     */
    fun formatDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        return when {
            date == today -> "Bugün"
            date == today.minusDays(1) -> "Dün"
            date == today.plusDays(1) -> "Yarın"
            date.year == today.year -> {
                val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("tr"))
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("tr"))
                "${date.dayOfMonth} $monthName, $dayOfWeek"
            }
            else -> {
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("tr"))
                "${date.dayOfMonth} $monthName ${date.year}"
            }
        }
    }
}
