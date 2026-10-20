package com.example.noteapp

import com.example.noteapp.data.settings.TrashRetentionPolicy
import com.example.noteapp.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashCleanupWorkerTest {

    @Test
    fun `retention policy computes exact threshold timestamp`() {
        val now = 1_700_000_000_000L

        val policy7 = TrashRetentionPolicy.DAYS_7
        val threshold7 = now - (policy7.days.toLong() * 24L * 60L * 60L * 1000L)
        assertEquals(now - 604_800_000L, threshold7)

        val policy14 = TrashRetentionPolicy.DAYS_14
        val threshold14 = now - (policy14.days.toLong() * 24L * 60L * 60L * 1000L)
        assertEquals(now - 1_209_600_000L, threshold14)

        val policy30 = TrashRetentionPolicy.DAYS_30
        val threshold30 = now - (policy30.days.toLong() * 24L * 60L * 60L * 1000L)
        assertEquals(now - 2_592_000_000L, threshold30)
    }

    @Test
    fun `expired trash filtering identifies notes past retention period`() {
        val now = 1_700_000_000_000L
        val policy = TrashRetentionPolicy.DAYS_30
        val threshold = now - (policy.days.toLong() * 24L * 60L * 60L * 1000L)

        val oldDeletedNote = Note(
            id = 1L,
            title = "Old Deleted Note",
            content = "Old trash",
            isDeleted = true,
            deletedAt = threshold - 1000L // 1 second older than 30 days
        )

        val recentlyDeletedNote = Note(
            id = 2L,
            title = "Recent Deleted Note",
            content = "Recently placed in trash",
            isDeleted = true,
            deletedAt = now - 86_400_000L // Deleted 1 day ago
        )

        val activeOldNote = Note(
            id = 3L,
            title = "Active Note",
            content = "Not in trash",
            isDeleted = false,
            timestamp = threshold - 10_000_000L
        )

        val notes = listOf(oldDeletedNote, recentlyDeletedNote, activeOldNote)

        val isExpired: (Note) -> Boolean = { note ->
            note.isDeleted && (
                (note.deletedAt != null && note.deletedAt <= threshold) ||
                (note.deletedAt == null && note.timestamp <= threshold)
            )
        }

        assertTrue(isExpired(oldDeletedNote))
        assertFalse(isExpired(recentlyDeletedNote))
        assertFalse(isExpired(activeOldNote))
    }

    @Test
    fun `legacy deleted note without deletedAt uses timestamp`() {
        val now = 1_700_000_000_000L
        val threshold = now - (7L * 24L * 60L * 60L * 1000L)

        val legacyDeletedNote = Note(
            id = 10L,
            title = "Legacy Note",
            content = "Deleted before deletedAt was introduced",
            isDeleted = true,
            deletedAt = null,
            timestamp = threshold - 5000L
        )

        val isExpired = legacyDeletedNote.isDeleted && (
            (legacyDeletedNote.deletedAt != null && legacyDeletedNote.deletedAt <= threshold) ||
            (legacyDeletedNote.deletedAt == null && legacyDeletedNote.timestamp <= threshold)
        )

        assertTrue(isExpired)
    }
}
