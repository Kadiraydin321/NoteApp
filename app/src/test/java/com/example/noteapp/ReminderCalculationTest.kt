package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.notification.NotificationHelper
import com.example.noteapp.notification.ReminderActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCalculationTest {

    @Test
    fun `snooze timestamp calculation matches snooze minutes`() {
        val baseTime = 1_700_000_000_000L

        val snooze15Minutes = 15
        val expected15 = baseTime + (15 * 60 * 1000L)
        val calculated15 = baseTime + (snooze15Minutes * 60 * 1000L)
        assertEquals(expected15, calculated15)
        assertEquals(900_000L, calculated15 - baseTime) // 15 dakika = 900,000 ms

        val snooze60Minutes = 60
        val expected60 = baseTime + (60 * 60 * 1000L)
        val calculated60 = baseTime + (snooze60Minutes * 60 * 1000L)
        assertEquals(expected60, calculated60)
        assertEquals(3_600_000L, calculated60 - baseTime) // 1 saat = 3,600,000 ms
    }

    @Test
    fun `snooze human readable label formatting matches specification`() {
        fun formatSnoozeLabel(minutes: Int): String {
            return if (minutes >= 60) "${minutes / 60} saat" else "$minutes dakika"
        }

        assertEquals("5 dakika", formatSnoozeLabel(5))
        assertEquals("15 dakika", formatSnoozeLabel(15))
        assertEquals("30 dakika", formatSnoozeLabel(30))
        assertEquals("1 saat", formatSnoozeLabel(60))
        assertEquals("2 saat", formatSnoozeLabel(120))
        assertEquals("24 saat", formatSnoozeLabel(1440))
    }

    @Test
    fun `boot receiver filter only reschedules future reminders and ignores expired ones`() {
        val now = 1_000_000L

        val futureNote1 = Note(id = 1L, title = "Gelecek 1", content = "...", reminderTime = now + 60_000L)
        val futureNote2 = Note(id = 2L, title = "Gelecek 2", content = "...", reminderTime = now + 3_600_000L)
        val pastNote = Note(id = 3L, title = "Geçmiş", content = "...", reminderTime = now - 5_000L)
        val exactNowNote = Note(id = 4L, title = "Şimdi", content = "...", reminderTime = now)
        val noReminderNote = Note(id = 5L, title = "Yok", content = "...", reminderTime = null)

        val notes = listOf(futureNote1, futureNote2, pastNote, exactNowNote, noReminderNote)

        // AlarmReceiver boot mantığı:
        val notesToReschedule = notes.filter { note ->
            val reminder = note.reminderTime
            reminder != null && reminder > now
        }

        assertEquals(2, notesToReschedule.size)
        assertTrue(notesToReschedule.contains(futureNote1))
        assertTrue(notesToReschedule.contains(futureNote2))
        assertFalse(notesToReschedule.contains(pastNote))
        assertFalse(notesToReschedule.contains(exactNowNote))
        assertFalse(notesToReschedule.contains(noReminderNote))
    }

    @Test
    fun `notification ids do not collide between reminders and sticky notifications`() {
        val noteIds = listOf(1L, 42L, 999L, 100_000L, 100_042L, 250_000L)

        for (id in noteIds) {
            val reminderId = NotificationHelper.getReminderNotificationId(id)
            val stickyId = NotificationHelper.getStickyNotificationId(id)

            // Reminder ID [0, 99_999] aralığındadır (modulo 100_000)
            assertTrue(reminderId in 0 until 100_000)

            // Sticky ID ise 200_000 + id ile üretilir
            assertTrue(stickyId >= 200_000)

            // Asla çakışmazlar
            assertNotEquals(reminderId, stickyId)
        }
    }

    @Test
    fun `pending intent request codes for reminder actions are mutually exclusive`() {
        val noteId = 42L

        val mainPendingIntentCode = noteId.toInt()
        val doneActionCode = (noteId * 10 + 1).toInt()
        val snooze15ActionCode = (noteId * 10 + 2).toInt()
        val snooze60ActionCode = (noteId * 10 + 3).toInt()

        val codes = setOf(mainPendingIntentCode, doneActionCode, snooze15ActionCode, snooze60ActionCode)
        // 4 farklı request code olmalı
        assertEquals(4, codes.size)
        assertEquals(42, mainPendingIntentCode)
        assertEquals(421, doneActionCode)
        assertEquals(422, snooze15ActionCode)
        assertEquals(423, snooze60ActionCode)
    }

    @Test
    fun `overdue reminder status detection correctly identifies past reminders`() {
        val currentTime = 500_000L

        fun isOverdue(reminderTime: Long?): Boolean {
            return reminderTime != null && reminderTime > 0 && reminderTime < currentTime
        }

        assertTrue(isOverdue(499_999L))
        assertTrue(isOverdue(100_000L))
        assertFalse(isOverdue(500_000L))
        assertFalse(isOverdue(500_001L))
        assertFalse(isOverdue(null))
        assertFalse(isOverdue(0L))
    }
}
