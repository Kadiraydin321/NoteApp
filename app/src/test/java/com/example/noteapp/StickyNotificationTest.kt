package com.example.noteapp

import com.example.noteapp.notification.NotificationHelper
import com.example.noteapp.notification.ReminderActionReceiver
import org.junit.Assert.*
import org.junit.Test

class StickyNotificationTest {

    @Test
    fun `sticky notification id does not collide with regular reminder id`() {
        val noteId = 42L
        val reminderId = noteId.toInt()
        val stickyId = NotificationHelper.getStickyNotificationId(noteId)

        assertNotEquals(reminderId, stickyId)
        assertTrue(stickyId >= 200_000)
        assertEquals(200_042, stickyId)
    }

    @Test
    fun `sticky channel id is distinct from reminder channel id`() {
        assertNotEquals(NotificationHelper.CHANNEL_ID, NotificationHelper.CHANNEL_STICKY_ID)
        assertEquals("note_sticky_channel", NotificationHelper.CHANNEL_STICKY_ID)
    }

    @Test
    fun `unpin action constant is correctly defined`() {
        assertEquals("com.example.noteapp.ACTION_UNPIN_STICKY", ReminderActionReceiver.ACTION_UNPIN_STICKY)
    }
}
