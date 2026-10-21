package com.example.noteapp.domain.util

import android.content.Context
import android.content.SharedPreferences
import com.example.noteapp.domain.model.Note

/**
 * Kategori kilit depolama soyutlaması (Birim testleri için).
 */
interface CategoryLockStorage {
    fun getLockedCategoryIds(): Set<Long>
    fun setLockedCategoryIds(ids: Set<Long>)
}

/**
 * Android SharedPreferences tabanlı depolama gerçekleştirimi.
 */
class SharedPreferencesCategoryLockStorage(
    private val prefs: SharedPreferences
) : CategoryLockStorage {
    companion object {
        private const val KEY_LOCKED_IDS = "locked_category_ids"
    }

    override fun getLockedCategoryIds(): Set<Long> {
        val stringSet = prefs.getStringSet(KEY_LOCKED_IDS, emptySet()) ?: emptySet()
        return stringSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    override fun setLockedCategoryIds(ids: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_LOCKED_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }
}

/**
 * Bellek içi depolama (Birim testleri için).
 */
class InMemoryCategoryLockStorage : CategoryLockStorage {
    private var lockedIds: Set<Long> = emptySet()

    override fun getLockedCategoryIds(): Set<Long> = lockedIds

    override fun setLockedCategoryIds(ids: Set<Long>) {
        lockedIds = ids
    }
}

/**
 * Kategori Bazlı Kilit & Gizlilik Yöneticisi.
 * Belirli kategorileri şifre/biyometrik kilit altına alır, oturum bazlı kilit açma sürelerini yönetir
 * ve kilitli kategorilere ait notların gizliliğini korur.
 */
class CategoryLockManager(
    private val storage: CategoryLockStorage
) {
    companion object {
        const val PREFS_NAME = "category_locks_prefs"

        fun create(context: Context): CategoryLockManager {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return CategoryLockManager(SharedPreferencesCategoryLockStorage(prefs))
        }
    }

    // Çalışma zamanı oturumunda kilidi açılmış kategoriler ve açılma zaman damgaları
    private val sessionUnlockedTimestamps = mutableMapOf<Long, Long>()

    /**
     * Kilitli tüm kategori ID'lerini döndürür.
     */
    fun getLockedCategoryIds(): Set<Long> {
        return storage.getLockedCategoryIds()
    }

    /**
     * Bir kategorinin kilitli olup olmadığını sorgular.
     */
    fun isCategoryLocked(categoryId: Long?): Boolean {
        if (categoryId == null || categoryId <= 0L) return false
        return storage.getLockedCategoryIds().contains(categoryId)
    }

    /**
     * Kategori kilidini açar veya kapatır.
     */
    fun setCategoryLocked(categoryId: Long, locked: Boolean) {
        if (categoryId <= 0L) return
        val current = storage.getLockedCategoryIds().toMutableSet()
        if (locked) {
            current.add(categoryId)
        } else {
            current.remove(categoryId)
            sessionUnlockedTimestamps.remove(categoryId)
        }
        storage.setLockedCategoryIds(current)
    }

    /**
     * Kategorinin kilidini geçerli oturum için açılmış olarak işaretler.
     */
    fun markCategoryUnlockedInSession(categoryId: Long, timestamp: Long = System.currentTimeMillis()) {
        sessionUnlockedTimestamps[categoryId] = timestamp
    }

    /**
     * Belirtilen kategorinin oturumda kilidinin açık kalıp kalmadığını (zaman aşımı dahil) kontrol eder.
     */
    fun isCategoryUnlockedInSession(
        categoryId: Long,
        timeoutMs: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isCategoryLocked(categoryId)) return true
        val unlockTime = sessionUnlockedTimestamps[categoryId] ?: return false
        if (timeoutMs <= 0L) {
            // Anında kilitlenme (Immediate) politikası
            return false
        }
        return (now - unlockTime) <= timeoutMs
    }

    /**
     * Oturumdaki tüm kilit açma izinlerini iptal eder.
     */
    fun lockAllSessionCategories() {
        sessionUnlockedTimestamps.clear()
    }

    /**
     * Kategori silindiğinde kilit kaydını temizler.
     */
    fun removeCategory(categoryId: Long) {
        setCategoryLocked(categoryId, false)
    }

    /**
     * Bir notun gizlenmesi (içeriğinin maskelenmesi) gerekip gerekmediğini kontrol eder.
     */
    fun isNoteConcealed(
        note: Note,
        timeoutMs: Long = 60_000L,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val catId = note.categoryId
        if (catId != null && isCategoryLocked(catId)) {
            val isUnlocked = isCategoryUnlockedInSession(catId, timeoutMs, now)
            if (!isUnlocked) return true
        }
        return false
    }
}
