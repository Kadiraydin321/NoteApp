package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.CalendarNoteGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarNoteGrouperTest {

    private val fixedZone = ZoneId.of("UTC")

    private fun createNote(
        id: Long = 1,
        title: String = "Test",
        content: String = "",
        timestamp: Long = 1700000000000L,
        updatedAt: Long = 0,
        reminderTime: Long? = null
    ): Note {
        return Note(
            id = id,
            title = title,
            content = content,
            timestamp = timestamp,
            updatedAt = updatedAt,
            reminderTime = reminderTime
        )
    }

    @Test
    fun toLocalDate_convertsEpochMillisAccurately() {
        // 2023-11-14T22:13:20Z -> 1700000000000L
        val date = CalendarNoteGrouper.toLocalDate(1700000000000L, fixedZone)
        assertEquals(2023, date.year)
        assertEquals(11, date.monthValue)
        assertEquals(14, date.dayOfMonth)
    }

    @Test
    fun getNoteDate_respectsModes() {
        val created = 1700000000000L // 2023-11-14
        val updated = 1700086400000L // 2023-11-15
        val reminder = 1700172800000L // 2023-11-16

        val note = createNote(
            timestamp = created,
            updatedAt = updated,
            reminderTime = reminder
        )

        assertEquals(
            LocalDate.of(2023, 11, 14),
            CalendarNoteGrouper.getNoteDate(note, CalendarNoteGrouper.DateFilterMode.CREATED_DATE, fixedZone)
        )
        assertEquals(
            LocalDate.of(2023, 11, 15),
            CalendarNoteGrouper.getNoteDate(note, CalendarNoteGrouper.DateFilterMode.UPDATED_DATE, fixedZone)
        )
        assertEquals(
            LocalDate.of(2023, 11, 16),
            CalendarNoteGrouper.getNoteDate(note, CalendarNoteGrouper.DateFilterMode.REMINDER_DATE, fixedZone)
        )

        val noteNoReminder = createNote(reminderTime = null)
        assertNull(
            CalendarNoteGrouper.getNoteDate(noteNoReminder, CalendarNoteGrouper.DateFilterMode.REMINDER_DATE, fixedZone)
        )
    }

    @Test
    fun groupNotesByDate_groupsAndOrdersDescending() {
        val date1 = 1700000000000L // 2023-11-14
        val date2 = 1700086400000L // 2023-11-15

        val n1 = createNote(id = 1, title = "N1", timestamp = date1)
        val n2 = createNote(id = 2, title = "N2", timestamp = date1 + 1000)
        val n3 = createNote(id = 3, title = "N3", timestamp = date2)

        val groups = CalendarNoteGrouper.groupNotesByDate(
            notes = listOf(n1, n2, n3),
            zoneId = fixedZone,
            today = LocalDate.of(2023, 11, 15)
        )

        assertEquals(2, groups.size)
        // Date2 (15 Nov) should come before Date1 (14 Nov)
        assertEquals(LocalDate.of(2023, 11, 15), groups[0].date)
        assertEquals("Bugün", groups[0].label)
        assertEquals(1, groups[0].notes.size)

        assertEquals(LocalDate.of(2023, 11, 14), groups[1].date)
        assertEquals("Dün", groups[1].label)
        assertEquals(2, groups[1].notes.size)
        // Within group, n2 is newer than n1
        assertEquals(2L, groups[1].notes[0].id)
    }

    @Test
    fun filterNotesByDate_filtersCorrectly() {
        val date1 = 1700000000000L // 2023-11-14
        val date2 = 1700086400000L // 2023-11-15

        val n1 = createNote(id = 1, timestamp = date1)
        val n2 = createNote(id = 2, timestamp = date2)

        val filtered = CalendarNoteGrouper.filterNotesByDate(
            notes = listOf(n1, n2),
            targetDate = LocalDate.of(2023, 11, 14),
            zoneId = fixedZone
        )

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun calculateMonthActivity_countsAccuratelyForTargetMonth() {
        val ym = YearMonth.of(2023, 11)
        val n1 = createNote(id = 1, timestamp = 1700000000000L) // 2023-11-14
        val n2 = createNote(id = 2, timestamp = 1700000050000L) // 2023-11-14
        val n3 = createNote(id = 3, timestamp = 1700086400000L) // 2023-11-15
        val n4 = createNote(id = 4, timestamp = 1704067200000L) // 2024-01-01 (Different month!)

        val activity = CalendarNoteGrouper.calculateMonthActivity(
            notes = listOf(n1, n2, n3, n4),
            yearMonth = ym,
            zoneId = fixedZone
        )

        assertEquals(2, activity[LocalDate.of(2023, 11, 14)])
        assertEquals(1, activity[LocalDate.of(2023, 11, 15)])
        assertNull(activity[LocalDate.of(2024, 1, 1)])
    }

    @Test
    fun getWeekDates_startsOnMondayEndsOnSunday() {
        val center = LocalDate.of(2023, 11, 15) // Wednesday
        val week = CalendarNoteGrouper.getWeekDates(center)

        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        assertEquals(LocalDate.of(2023, 11, 13), week.first())
        assertEquals(LocalDate.of(2023, 11, 19), week.last())
        assertTrue(week.contains(center))
    }

    @Test
    fun getMonthCalendarDays_constructsCompletePaddedWeeks() {
        val ym = YearMonth.of(2023, 11)
        val selected = LocalDate.of(2023, 11, 15)
        val today = LocalDate.of(2023, 11, 14)
        val activity = mapOf(selected to 3)

        val days = CalendarNoteGrouper.getMonthCalendarDays(
            yearMonth = ym,
            selectedDate = selected,
            activityMap = activity,
            today = today
        )

        // Must be multiple of 7
        assertEquals(0, days.size % 7)
        assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().date.dayOfWeek)

        val selectedDay = days.find { it.date == selected }
        assertTrue(selectedDay!!.isSelected)
        assertEquals(3, selectedDay.noteCount)

        val todayDay = days.find { it.date == today }
        assertTrue(todayDay!!.isToday)

        val outsideMonthDay = days.first() // Oct 30, 2023
        assertFalse(outsideMonthDay.isCurrentMonth)
    }

    @Test
    fun formatDateLabel_returnsFriendlyLabels() {
        val today = LocalDate.of(2026, 10, 21)
        val yesterday = LocalDate.of(2026, 10, 20)
        val tomorrow = LocalDate.of(2026, 10, 22)
        val pastInYear = LocalDate.of(2026, 5, 10)
        val otherYear = LocalDate.of(2024, 1, 15)

        assertEquals("Bugün", CalendarNoteGrouper.formatDateLabel(today, today))
        assertEquals("Dün", CalendarNoteGrouper.formatDateLabel(yesterday, today))
        assertEquals("Yarın", CalendarNoteGrouper.formatDateLabel(tomorrow, today))
        assertTrue(CalendarNoteGrouper.formatDateLabel(pastInYear, today).contains("10"))
        assertTrue(CalendarNoteGrouper.formatDateLabel(otherYear, today).contains("2024"))
    }
}
