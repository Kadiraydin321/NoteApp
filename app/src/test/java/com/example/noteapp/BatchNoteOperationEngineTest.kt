package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.BatchNoteOperationEngine
import org.junit.Assert.*
import org.junit.Test

class BatchNoteOperationEngineTest {

    private fun createTestNote(
        id: Long,
        title: String,
        content: String,
        isPinned: Boolean = false,
        categoryId: Long? = null,
        attachments: List<String> = emptyList()
    ) = Note(
        id = id,
        title = title,
        content = content,
        isPinned = isPinned,
        categoryId = categoryId,
        attachments = attachments,
        timestamp = 1000L
    )

    @Test
    fun calculateTargetPinState_pinsIfAnyUnpinned() {
        val notes = listOf(
            createTestNote(1, "Not 1", "İçerik 1", isPinned = true),
            createTestNote(2, "Not 2", "İçerik 2", isPinned = false)
        )
        // En az biri unpinned olduğu için hedef: hepsini pinle (true)
        assertTrue(BatchNoteOperationEngine.calculateTargetPinState(notes))

        val allPinned = listOf(
            createTestNote(1, "Not 1", "İçerik 1", isPinned = true),
            createTestNote(2, "Not 2", "İçerik 2", isPinned = true)
        )
        // Hepsi zaten pinli olduğu için hedef: pinleri kaldır (false)
        assertFalse(BatchNoteOperationEngine.calculateTargetPinState(allPinned))
    }

    @Test
    fun applyBulkTag_appendsTagCleanlyWithoutDuplicates() {
        val content = "Bugün yapılacak işler"
        val tagged = BatchNoteOperationEngine.applyBulkTag(content, "proje")
        assertEquals("Bugün yapılacak işler #proje", tagged)

        // Zaten aynı etiket varsa tekrar eklememeli
        val secondTag = BatchNoteOperationEngine.applyBulkTag(tagged, "#proje")
        assertEquals("Bugün yapılacak işler #proje", secondTag)
    }

    @Test
    fun removeBulkTag_removesTargetTagCleanly() {
        val content = "Toplantı notları #proje ve #acil"
        val cleaned = BatchNoteOperationEngine.removeBulkTag(content, "proje")
        assertEquals("Toplantı notları ve #acil", cleaned)
    }

    @Test
    fun mergeNotes_combinesTitlesContentAndAttachments() {
        val n1 = createTestNote(1, "Alışveriş", "Süt ve yumurta al", attachments = listOf("img1.png"))
        val n2 = createTestNote(2, "Eczane", "Vitaminleri unutma", isPinned = true, attachments = listOf("img2.png"))

        val merged = BatchNoteOperationEngine.mergeNotes(listOf(n1, n2))

        assertEquals("Alışveriş (+1 Not)", merged.title)
        assertTrue(merged.content.contains("### Alışveriş\n\nSüt ve yumurta al"))
        assertTrue(merged.content.contains("---"))
        assertTrue(merged.content.contains("### Eczane\n\nVitaminleri unutma"))
        assertEquals(2, merged.attachments.size)
        assertTrue(merged.isPinned) // Biri pinli olduğu için birleşen de pinli olmalı
    }

    @Test
    fun calculateStatistics_computesTotalsAccurately() {
        val notes = listOf(
            createTestNote(1, "Başlık 1", "Bir iki üç", categoryId = 10L, attachments = listOf("a.jpg")),
            createTestNote(2, "Başlık 2", "Dört beş - [ ] Görev", categoryId = 20L)
        )

        val stats = BatchNoteOperationEngine.calculateStatistics(notes)
        assertEquals(2, stats.noteCount)
        assertEquals(2, stats.uniqueCategoryCount)
        assertEquals(1, stats.totalAttachments)
        assertTrue(stats.hasChecklists)
    }

    @Test
    fun formatBatchSummary_producesConciseSummaryString() {
        val notes = listOf(
            createTestNote(1, "A", "Kelime bir iki", attachments = listOf("x.png")),
            createTestNote(2, "B", "Kelime üç dört")
        )

        val summary = BatchNoteOperationEngine.formatBatchSummary(notes)
        assertTrue(summary.contains("2 not"))
        assertTrue(summary.contains("ek"))
    }
}
