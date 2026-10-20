package com.example.noteapp

import com.example.noteapp.data.settings.LockTimeoutPolicy
import org.junit.Assert.*
import org.junit.Test

class LockTimeoutPolicyTest {

    @Test
    fun `all lock timeout policies have valid positive or zero timeouts`() {
        val policies = LockTimeoutPolicy.values()
        assertEquals(4, policies.size)

        assertEquals(0L, LockTimeoutPolicy.IMMEDIATE.timeoutMs)
        assertEquals(60_000L, LockTimeoutPolicy.ONE_MINUTE.timeoutMs)
        assertEquals(300_000L, LockTimeoutPolicy.FIVE_MINUTES.timeoutMs)
        assertEquals(900_000L, LockTimeoutPolicy.FIFTEEN_MINUTES.timeoutMs)
    }

    @Test
    fun `isLockExpired returns true immediately for immediate policy`() {
        val policy = LockTimeoutPolicy.IMMEDIATE
        val isExpired = isLockExpired(policy, elapsedMs = 500L)
        assertTrue("Immediate policy should always expire immediately", isExpired)
    }

    @Test
    fun `isLockExpired respects one minute grace period`() {
        val policy = LockTimeoutPolicy.ONE_MINUTE

        assertFalse("30 seconds should be within grace period", isLockExpired(policy, 30_000L))
        assertFalse("59 seconds should be within grace period", isLockExpired(policy, 59_000L))
        assertTrue("61 seconds should exceed grace period", isLockExpired(policy, 61_000L))
    }

    @Test
    fun `isLockExpired respects five minutes grace period`() {
        val policy = LockTimeoutPolicy.FIVE_MINUTES

        assertFalse("4 minutes should be within grace period", isLockExpired(policy, 240_000L))
        assertTrue("5 minutes and 1 second should exceed grace period", isLockExpired(policy, 301_000L))
    }

    @Test
    fun `policy descriptions are non-empty and descriptive`() {
        LockTimeoutPolicy.values().forEach { policy ->
            assertTrue(policy.title.isNotBlank())
            assertTrue(policy.description.isNotBlank())
        }
    }

    private fun isLockExpired(policy: LockTimeoutPolicy, elapsedMs: Long): Boolean {
        if (policy == LockTimeoutPolicy.IMMEDIATE) return true
        return elapsedMs > policy.timeoutMs
    }
}
