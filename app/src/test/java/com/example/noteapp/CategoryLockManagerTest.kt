package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.CategoryLockManager
import com.example.noteapp.domain.util.InMemoryCategoryLockStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryLockManagerTest {

    private lateinit var storage: InMemoryCategoryLockStorage
    private lateinit var manager: CategoryLockManager

    @Before
    fun setup() {
        storage = InMemoryCategoryLockStorage()
        manager = CategoryLockManager(storage)
    }

    @Test
    fun lockAndUnlockCategory_togglesStateCorrectly() {
        assertFalse(manager.isCategoryLocked(5L))

        manager.setCategoryLocked(5L, true)
        assertTrue(manager.isCategoryLocked(5L))
        assertEquals(setOf(5L), manager.getLockedCategoryIds())

        manager.setCategoryLocked(5L, false)
        assertFalse(manager.isCategoryLocked(5L))
        assertTrue(manager.getLockedCategoryIds().isEmpty())
    }

    @Test
    fun isCategoryUnlockedInSession_respectsTimeouts() {
        val catId = 12L
        manager.setCategoryLocked(catId, true)

        val timeoutMs = 30_000L
        val unlockTime = 100_000L

        // Henüz açılmadı -> kilitli
        assertFalse(manager.isCategoryUnlockedInSession(catId, timeoutMs, now = unlockTime))

        // Kilidi aç
        manager.markCategoryUnlockedInSession(catId, timestamp = unlockTime)

        // 10 saniye sonra -> açık
        assertTrue(manager.isCategoryUnlockedInSession(catId, timeoutMs, now = unlockTime + 10_000L))

        // 35 saniye sonra -> süre doldu, kilitli
        assertFalse(manager.isCategoryUnlockedInSession(catId, timeoutMs, now = unlockTime + 35_000L))
    }

    @Test
    fun lockAllSessionCategories_relocksInstantly() {
        val catId = 20L
        manager.setCategoryLocked(catId, true)
        manager.markCategoryUnlockedInSession(catId, timestamp = 1000L)

        assertTrue(manager.isCategoryUnlockedInSession(catId, 60_000L, now = 2000L))

        manager.lockAllSessionCategories()

        assertFalse(manager.isCategoryUnlockedInSession(catId, 60_000L, now = 2000L))
    }

    @Test
    fun isNoteConcealed_concealsOnlyWhenCategoryLockedAndSessionClosed() {
        val catId = 7L
        manager.setCategoryLocked(catId, true)

        val note = Note(
            id = 1L,
            title = "Gizli Not",
            content = "Gizli Bilgi",
            categoryId = catId
        )

        // Kategori kilitli ve oturum açılmamış -> gizlenmeli
        assertTrue(manager.isNoteConcealed(note, timeoutMs = 60_000L, now = 1000L))

        // Oturum açıldı
        manager.markCategoryUnlockedInSession(catId, timestamp = 1000L)

        // Artık gizlenmemeli
        assertFalse(manager.isNoteConcealed(note, timeoutMs = 60_000L, now = 2000L))

        // Kategorisiz not gizlenmemeli
        val noteNoCat = Note(id = 2L, title = "Genel", content = "Normal", categoryId = null)
        assertFalse(manager.isNoteConcealed(noteNoCat, timeoutMs = 60_000L, now = 2000L))
    }

    @Test
    fun removeCategory_clearsLockRecord() {
        manager.setCategoryLocked(88L, true)
        assertTrue(manager.isCategoryLocked(88L))

        manager.removeCategory(88L)
        assertFalse(manager.isCategoryLocked(88L))
    }
}
