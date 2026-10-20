package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.NoteMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMergerTest {

    @Test
    fun testMergeNotesContentAndHeaders() {
        val note1 = Note(
            id = 1,
            title = "Toplantı Gündemi",
            content = "- [ ] Bütçe analizi\n- [ ] Yol haritası",
            timestamp = 1000L,
            categoryId = 10L,
            attachments = listOf("path/img1.png")
        )
        val note2 = Note(
            id = 2,
            title = "Toplantı Kararları",
            content = "Yol haritası onaylandı.",
            timestamp = 2000L,
            categoryId = 10L,
            attachments = listOf("path/img1.png", "path/audio.mp3")
        )

        val merged = NoteMerger.mergeNotes(listOf(note2, note1)) // pass out of order to check sorting

        // Title should be derived from first chronological note
        assertEquals("Toplantı Gündemi (Birleştirildi)", merged.title)

        // Content check
        val expected = "## Toplantı Gündemi\n\n- [ ] Bütçe analizi\n- [ ] Yol haritası\n\n---\n\n## Toplantı Kararları\n\nYol haritası onaylandı."
        assertEquals(expected, merged.content)

        // Deduplicated attachments
        assertEquals(2, merged.attachments.size)
        assertTrue(merged.attachments.contains("path/img1.png"))
        assertTrue(merged.attachments.contains("path/audio.mp3"))

        // Common category retained
        assertEquals(10L, merged.categoryId)
    }

    @Test
    fun testMergeNotesWithCustomTitleAndDifferentCategories() {
        val note1 = Note(id = 1, title = "A", content = "İçerik A", categoryId = 5L)
        val note2 = Note(id = 2, title = "B", content = "İçerik B", categoryId = 8L)

        val merged = NoteMerger.mergeNotes(listOf(note1, note2), customTitle = "Ana Rapor")

        assertEquals("Ana Rapor", merged.title)
        assertNull(merged.categoryId) // Different categories -> null
        assertTrue(merged.content.contains("## A"))
        assertTrue(merged.content.contains("---"))
        assertTrue(merged.content.contains("## B"))
    }
}
