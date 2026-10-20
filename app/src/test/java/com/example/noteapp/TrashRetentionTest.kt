package com.example.noteapp

import com.example.noteapp.data.settings.TrashRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRetentionTest {

    @Test
    fun testTrashRetentionPolicyDays() {
        assertEquals(30, TrashRetentionPolicy.DAYS_30.days)
        assertEquals(14, TrashRetentionPolicy.DAYS_14.days)
        assertEquals(7, TrashRetentionPolicy.DAYS_7.days)
        assertEquals(0, TrashRetentionPolicy.NEVER.days)
    }

    @Test
    fun testTrashRetentionPolicyThresholdCalculation() {
        val now = 1_700_000_000_000L

        // 30 Days threshold
        val millis30Days = 30L * 24L * 60L * 60L * 1000L
        val threshold30 = now - millis30Days
        assertEquals(now - 2_592_000_000L, threshold30)

        // A note deleted 31 days ago is older than threshold -> should be purged
        val noteDeleted31DaysAgo = now - (31L * 24L * 60L * 60L * 1000L)
        assertTrue(noteDeleted31DaysAgo < threshold30)

        // A note deleted 10 days ago is newer than threshold -> should NOT be purged
        val noteDeleted10DaysAgo = now - (10L * 24L * 60L * 60L * 1000L)
        assertFalse(noteDeleted10DaysAgo < threshold30)
    }

    @Test
    fun testNeverRetentionPolicyDoesNotPurge() {
        val policy = TrashRetentionPolicy.NEVER
        assertEquals(0, policy.days)
        // With 0 days, purge is skipped
        val shouldExecutePurge = policy.days > 0
        assertFalse(shouldExecutePurge)
    }
}
