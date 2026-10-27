package com.example.noteapp.biometric

/**
 * Biyometrik & Master PIN oturum durumu ve kilitlenme sonuçları.
 */
sealed interface LockoutStatus {
    data object Unlocked : LockoutStatus
    data class AttemptsRemaining(val remaining: Int) : LockoutStatus
    data class Locked(val remainingMs: Long, val isPermanent: Boolean = false) : LockoutStatus
}

/**
 * Akıllı Oturum Süresi & Kilitlenme Yöneticisi (Smart Session & Lockout Manager).
 *
 * 1. Akıllı Oturum (Grace Period): Uygulama arka plana gidip kısa süre içinde geri döndüğünde
 *    (ör. 30 saniyelik grace period) her defasında tekrar PIN/biyometrik sormasını önler.
 * 2. Kilitlenme Yönetimi (Lockout Management): Art arda 5 hatalı girişte sistemi 30 saniye süreyle
 *    kilitler ve brute-force saldırılarını engeller. Kalıcı kilitlenme (ERROR_LOCKOUT_PERMANENT)
 *    durumlarını da yönetir.
 */
class BiometricSessionManager(
    private val defaultGracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    private val lockoutDurationMs: Long = DEFAULT_LOCKOUT_DURATION_MS,
    private val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS
) {
    companion object {
        const val DEFAULT_GRACE_PERIOD_MS = 30_000L // 30 saniye akıllı tolerans süresi
        const val DEFAULT_LOCKOUT_DURATION_MS = 30_000L // 30 saniye geçici kilitlenme
        const val DEFAULT_MAX_FAILED_ATTEMPTS = 5 // 5 hatalı deneme hakkı
    }

    private var lastUnlockTimestamp: Long = 0L
    private var backgroundTimestamp: Long = 0L
    private var failedAttemptsCount: Int = 0
    private var lockoutEndTimestamp: Long = 0L
    private var isPermanentLockout: Boolean = false

    /**
     * Başarılı bir kilit açma işleminde oturumu yeniler ve hatalı giriş sayacını sıfırlar.
     */
    @Synchronized
    fun recordSuccessfulUnlock(timestamp: Long = System.currentTimeMillis()) {
        lastUnlockTimestamp = timestamp
        backgroundTimestamp = 0L
        failedAttemptsCount = 0
        lockoutEndTimestamp = 0L
        isPermanentLockout = false
    }

    /**
     * Uygulama arka plana geçtiğinde (onStop) çağrılır.
     */
    @Synchronized
    fun onAppBackgrounded(timestamp: Long = System.currentTimeMillis()) {
        backgroundTimestamp = timestamp
    }

    /**
     * Uygulama ön plana döndüğünde (onResume) kilitli notun kapatılıp kapatılmayacağını belirler.
     * @param policyTimeoutMs Kullanıcının ayarlardaki kilit politikası süresi (ms).
     * @param now Mevcut zaman damgası.
     * @return true ise oturum süresi aşılmıştır, kilitli not kapatılmalı / yeniden doğrulanmalıdır.
     */
    @Synchronized
    fun shouldLockOnResume(
        policyTimeoutMs: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (backgroundTimestamp == 0L) return false
        if (lastUnlockTimestamp == 0L) return true

        val elapsedInBackground = now - backgroundTimestamp
        // Eğer politika Anında (IMMEDIATE = 0L) olsa dahi akıllı tolerans (grace period) süresince
        // hemen kapatılmaz; sadece grace period aşıldıysa kapatılır.
        val effectiveAllowedDuration = if (policyTimeoutMs <= 0L) {
            defaultGracePeriodMs
        } else {
            maxOf(defaultGracePeriodMs, policyTimeoutMs)
        }

        return elapsedInBackground > effectiveAllowedDuration
    }

    /**
     * Not veya kategoriye tıklanırken mevcut oturumun hâlâ geçerli (açık) olup olmadığını sorgular.
     * Geçerliyse kullanıcıya tekrar PIN/biyometrik sormaz.
     */
    @Synchronized
    fun isSessionValid(
        policyTimeoutMs: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastUnlockTimestamp == 0L) return false

        // Eğer uygulama yakın zamanda arka plana gittiyse arka planda geçirilen süreyi de kontrol et
        if (backgroundTimestamp > 0L) {
            val elapsedInBackground = now - backgroundTimestamp
            val allowedBackground = if (policyTimeoutMs <= 0L) defaultGracePeriodMs else maxOf(defaultGracePeriodMs, policyTimeoutMs)
            if (elapsedInBackground > allowedBackground) {
                return false
            }
        }

        val elapsedSinceUnlock = now - lastUnlockTimestamp
        val effectiveTimeout = if (policyTimeoutMs <= 0L) {
            defaultGracePeriodMs
        } else {
            maxOf(defaultGracePeriodMs, policyTimeoutMs)
        }

        return elapsedSinceUnlock <= effectiveTimeout
    }

    /**
     * Oturumu manuel olarak kilitler / sonlandırır.
     */
    @Synchronized
    fun clearSession() {
        lastUnlockTimestamp = 0L
        backgroundTimestamp = 0L
    }

    /**
     * Hatalı bir PIN veya biyometrik giriş yapıldığında çağrılır.
     * 5 ardışık hatalı girişte 30 saniye kilitlenme başlatır.
     */
    @Synchronized
    fun recordFailedAttempt(now: Long = System.currentTimeMillis()): LockoutStatus {
        if (isPermanentLockout) {
            return LockoutStatus.Locked(remainingMs = Long.MAX_VALUE, isPermanent = true)
        }

        // Eğer mevcut bir kilitlenme süresi henüz bitmediyse
        if (now < lockoutEndTimestamp) {
            return LockoutStatus.Locked(remainingMs = lockoutEndTimestamp - now, isPermanent = false)
        }

        failedAttemptsCount++
        return if (failedAttemptsCount >= maxFailedAttempts) {
            lockoutEndTimestamp = now + lockoutDurationMs
            LockoutStatus.Locked(remainingMs = lockoutDurationMs, isPermanent = false)
        } else {
            LockoutStatus.AttemptsRemaining(remaining = maxFailedAttempts - failedAttemptsCount)
        }
    }

    /**
     * Biyometrik sistemden gelen donanım kilitlenmesini (ERROR_LOCKOUT veya ERROR_LOCKOUT_PERMANENT) işler.
     */
    @Synchronized
    fun recordBiometricLockout(isPermanent: Boolean, now: Long = System.currentTimeMillis()): LockoutStatus {
        if (isPermanent) {
            isPermanentLockout = true
            return LockoutStatus.Locked(remainingMs = Long.MAX_VALUE, isPermanent = true)
        } else {
            lockoutEndTimestamp = now + lockoutDurationMs
            return LockoutStatus.Locked(remainingMs = lockoutDurationMs, isPermanent = false)
        }
    }

    /**
     * Güncel kilitlenme durumunu sorgular.
     */
    @Synchronized
    fun getLockoutStatus(now: Long = System.currentTimeMillis()): LockoutStatus {
        if (isPermanentLockout) {
            return LockoutStatus.Locked(remainingMs = Long.MAX_VALUE, isPermanent = true)
        }
        return if (now < lockoutEndTimestamp) {
            LockoutStatus.Locked(remainingMs = lockoutEndTimestamp - now, isPermanent = false)
        } else {
            LockoutStatus.Unlocked
        }
    }

    /**
     * Kilitlenme durumunu ve sayacı sıfırlar.
     */
    @Synchronized
    fun resetLockout() {
        failedAttemptsCount = 0
        lockoutEndTimestamp = 0L
        isPermanentLockout = false
    }

    fun getLastUnlockTimestamp(): Long = lastUnlockTimestamp
    fun getBackgroundTimestamp(): Long = backgroundTimestamp
    fun getFailedAttemptsCount(): Int = failedAttemptsCount
}
