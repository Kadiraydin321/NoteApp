package com.example.noteapp

import com.example.noteapp.biometric.BiometricSessionManager
import com.example.noteapp.biometric.LockoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BiometricSessionManagerTest {

    private lateinit var sessionManager: BiometricSessionManager

    @Before
    fun setup() {
        sessionManager = BiometricSessionManager(
            defaultGracePeriodMs = 30_000L,
            lockoutDurationMs = 30_000L,
            maxFailedAttempts = 5
        )
    }

    @Test
    fun `isSessionValid returns false when no unlock has occurred`() {
        assertFalse(sessionManager.isSessionValid(policyTimeoutMs = 0L, now = 100_000L))
        assertFalse(sessionManager.isSessionValid(policyTimeoutMs = 60_000L, now = 100_000L))
    }

    @Test
    fun `isSessionValid respects 30-second grace period even for immediate policy`() {
        val unlockTime = 100_000L
        sessionManager.recordSuccessfulUnlock(timestamp = unlockTime)

        // 10 saniye sonra -> Oturum hâlâ geçerli (Grace period aktif)
        assertTrue(
            "10 seconds elapsed should be valid under 30s grace period",
            sessionManager.isSessionValid(policyTimeoutMs = 0L, now = unlockTime + 10_000L)
        )

        // 29 saniye sonra -> Oturum hâlâ geçerli
        assertTrue(
            "29 seconds elapsed should be valid under 30s grace period",
            sessionManager.isSessionValid(policyTimeoutMs = 0L, now = unlockTime + 29_000L)
        )

        // 31 saniye sonra -> Süre doldu
        assertFalse(
            "31 seconds elapsed should expire for immediate policy with 30s grace period",
            sessionManager.isSessionValid(policyTimeoutMs = 0L, now = unlockTime + 31_000L)
        )
    }

    @Test
    fun `isSessionValid respects longer policy timeouts`() {
        val unlockTime = 100_000L
        val policyTimeoutMs = 300_000L // 5 dakika
        sessionManager.recordSuccessfulUnlock(timestamp = unlockTime)

        // 4 dakika sonra -> Geçerli
        assertTrue(sessionManager.isSessionValid(policyTimeoutMs = policyTimeoutMs, now = unlockTime + 240_000L))

        // 5 dakika 1 saniye sonra -> Süre doldu
        assertFalse(sessionManager.isSessionValid(policyTimeoutMs = policyTimeoutMs, now = unlockTime + 301_000L))
    }

    @Test
    fun `shouldLockOnResume does not lock if app returns within 30-second grace period`() {
        val unlockTime = 50_000L
        val backgroundTime = 100_000L
        sessionManager.recordSuccessfulUnlock(timestamp = unlockTime)
        sessionManager.onAppBackgrounded(timestamp = backgroundTime)

        // 15 saniye arka planda kalıp döndü -> Kilitlenmemeli
        assertFalse(
            "Returning within 15 seconds should not lock",
            sessionManager.shouldLockOnResume(policyTimeoutMs = 0L, now = backgroundTime + 15_000L)
        )

        // 31 saniye arka planda kalıp döndü -> Kilitlenmeli
        assertTrue(
            "Returning after 31 seconds should trigger lock",
            sessionManager.shouldLockOnResume(policyTimeoutMs = 0L, now = backgroundTime + 31_000L)
        )
    }

    @Test
    fun `clearSession instantly invalidates session`() {
        val unlockTime = 100_000L
        sessionManager.recordSuccessfulUnlock(timestamp = unlockTime)
        assertTrue(sessionManager.isSessionValid(policyTimeoutMs = 60_000L, now = unlockTime + 10_000L))

        sessionManager.clearSession()

        assertFalse(sessionManager.isSessionValid(policyTimeoutMs = 60_000L, now = unlockTime + 10_000L))
    }

    @Test
    fun `consecutive failed attempts trigger lockout on 5th attempt`() {
        val now = 10_000L

        // 1. - 4. denemeler: Kalan deneme bildirilir
        for (i in 1..4) {
            val status = sessionManager.recordFailedAttempt(now = now)
            assertTrue("Attempt $i should report remaining attempts", status is LockoutStatus.AttemptsRemaining)
            assertEquals(5 - i, (status as LockoutStatus.AttemptsRemaining).remaining)
        }

        // 5. deneme: 30 saniye kilitlenme devreye girer
        val fifthStatus = sessionManager.recordFailedAttempt(now = now)
        assertTrue("5th attempt must trigger Lockout", fifthStatus is LockoutStatus.Locked)
        val lockedStatus = fifthStatus as LockoutStatus.Locked
        assertEquals(30_000L, lockedStatus.remainingMs)
        assertFalse(lockedStatus.isPermanent)

        // Kilitlenme süresi bitmeden (15 sn sonra): Kilitli kalmalı
        val checkStatus = sessionManager.getLockoutStatus(now = now + 15_000L)
        assertTrue("Should remain locked at 15s", checkStatus is LockoutStatus.Locked)
        assertEquals(15_000L, (checkStatus as LockoutStatus.Locked).remainingMs)

        // Kilitlenme süresi bittiğinde (31 sn sonra): Kilit kalkmalı
        val expiredStatus = sessionManager.getLockoutStatus(now = now + 31_000L)
        assertTrue("Should be unlocked after 30s cooldown", expiredStatus is LockoutStatus.Unlocked)
    }

    @Test
    fun `recordSuccessfulUnlock resets failed attempts and lockout`() {
        val now = 10_000L
        repeat(3) { sessionManager.recordFailedAttempt(now = now) }
        assertEquals(3, sessionManager.getFailedAttemptsCount())

        sessionManager.recordSuccessfulUnlock(timestamp = now)
        assertEquals(0, sessionManager.getFailedAttemptsCount())
        assertTrue(sessionManager.getLockoutStatus(now = now) is LockoutStatus.Unlocked)
    }

    @Test
    fun `permanent biometric lockout remains locked until reset`() {
        val now = 20_000L
        val status = sessionManager.recordBiometricLockout(isPermanent = true, now = now)

        assertTrue(status is LockoutStatus.Locked)
        assertTrue((status as LockoutStatus.Locked).isPermanent)

        // Uzun süre sonra bile kilitli kalmalı
        val checkLater = sessionManager.getLockoutStatus(now = now + 1_000_000L)
        assertTrue(checkLater is LockoutStatus.Locked)
        assertTrue((checkLater as LockoutStatus.Locked).isPermanent)

        sessionManager.resetLockout()
        assertTrue(sessionManager.getLockoutStatus(now = now + 1_000_000L) is LockoutStatus.Unlocked)
    }
}
