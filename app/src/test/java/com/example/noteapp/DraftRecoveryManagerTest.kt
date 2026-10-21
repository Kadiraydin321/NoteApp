package com.example.noteapp

import com.example.noteapp.domain.util.DraftRecoveryManager
import com.example.noteapp.domain.util.InMemoryDraftStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftRecoveryManagerTest {

    private lateinit var storage: InMemoryDraftStorage
    private lateinit var manager: DraftRecoveryManager

    @Before
    fun setup() {
        storage = InMemoryDraftStorage()
        manager = DraftRecoveryManager(storage)
    }

    @Test
    fun saveAndGetDraft_persistsAndRetrievesAccurately() {
        manager.saveDraft(
            noteId = 42L,
            title = "Taslak Başlık",
            content = "Taslak İçerik",
            timestamp = 1000L
        )

        val draft = manager.getDraft(42L)
        assertNotNull(draft)
        assertEquals(42L, draft!!.noteId)
        assertEquals("Taslak Başlık", draft.title)
        assertEquals("Taslak İçerik", draft.content)
        assertEquals(1000L, draft.timestamp)
    }

    @Test
    fun saveDraft_forNewNote_usesNewNoteKey() {
        manager.saveDraft(
            noteId = null,
            title = "Yeni Not Başlığı",
            content = "Yeni Not İçeriği",
            timestamp = 2000L
        )

        val draftNull = manager.getDraft(null)
        val draftZero = manager.getDraft(0L)

        assertNotNull(draftNull)
        assertEquals("Yeni Not Başlığı", draftNull!!.title)
        assertEquals(draftNull, draftZero)
    }

    @Test
    fun hasRecoverableDraft_detectsMeaningfulChanges() {
        val noteId = 10L
        manager.saveDraft(noteId, "Değişen Başlık", "Eski İçerik", timestamp = 5000L)

        // Title differs -> recoverable
        assertTrue(
            manager.hasRecoverableDraft(
                noteId = noteId,
                currentDbTitle = "Orijinal Başlık",
                currentDbContent = "Eski İçerik",
                currentDbTimestamp = 4000L
            )
        )

        // Content differs -> recoverable
        manager.saveDraft(noteId, "Aynı Başlık", "Yeni Farklı İçerik", timestamp = 5000L)
        assertTrue(
            manager.hasRecoverableDraft(
                noteId = noteId,
                currentDbTitle = "Aynı Başlık",
                currentDbContent = "Orijinal İçerik",
                currentDbTimestamp = 4000L
            )
        )

        // Exactly identical -> NOT recoverable
        manager.saveDraft(noteId, "Aynı Başlık", "Aynı İçerik", timestamp = 5000L)
        assertFalse(
            manager.hasRecoverableDraft(
                noteId = noteId,
                currentDbTitle = "Aynı Başlık",
                currentDbContent = "Aynı İçerik",
                currentDbTimestamp = 4000L
            )
        )
    }

    @Test
    fun hasRecoverableDraft_returnsFalseIfDraftOlderThanDb() {
        val noteId = 15L
        manager.saveDraft(noteId, "Farklı Başlık", "Farklı İçerik", timestamp = 3000L)

        // DB was saved at 5000L (newer than draft at 3000L)
        assertFalse(
            manager.hasRecoverableDraft(
                noteId = noteId,
                currentDbTitle = "Güncel Başlık",
                currentDbContent = "Güncel İçerik",
                currentDbTimestamp = 5000L
            )
        )
    }

    @Test
    fun clearDraft_removesTargetDraftOnly() {
        manager.saveDraft(1L, "N1", "C1", 1000L)
        manager.saveDraft(2L, "N2", "C2", 1000L)

        manager.clearDraft(1L)

        assertNull(manager.getDraft(1L))
        assertNotNull(manager.getDraft(2L))
    }

    @Test
    fun saveDraft_emptyTextClearsDraft() {
        manager.saveDraft(5L, "Başlık", "İçerik", 1000L)
        assertNotNull(manager.getDraft(5L))

        // Saving empty text clears the draft
        manager.saveDraft(5L, "   ", "", 2000L)
        assertNull(manager.getDraft(5L))
    }

    @Test
    fun getDraftAgeDescription_formatsNaturalTurkishDuration() {
        val now = 1000_000_000L

        assertEquals("Az önce", manager.getDraftAgeDescription(now - 15_000L, now))
        assertEquals("12 dakika önce", manager.getDraftAgeDescription(now - 12 * 60_000L, now))
        assertEquals("3 saat önce", manager.getDraftAgeDescription(now - 3 * 3600_000L, now))
        assertEquals("Dün", manager.getDraftAgeDescription(now - 25 * 3600_000L, now))
        assertEquals("3 gün önce", manager.getDraftAgeDescription(now - 3 * 86400_000L, now))
    }
}
