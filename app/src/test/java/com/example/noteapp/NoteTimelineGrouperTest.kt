package com.example.noteapp

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.NoteTimelineGrouper
import com.example.noteapp.domain.util.TimelineBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class NoteTimelineGrouperTest {

    private fun createNote(
        id: Long,
        title: String,
        timestamp: Long,
        isPinned: Boolean = false,
        categoryId: Long? = null
    ): Note {
        return Note(
            id = id,
            title = title,
            content = "İçerik $id",
            timestamp = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp,
            color = 0,
            isPinned = isPinned,
            categoryId = categoryId
        )
    }

    @Test
    fun groupNotesByTimeline_empty_list_returns_empty_sections() {
        val sections = NoteTimelineGrouper.groupNotesByTimeline(emptyList())
        assertTrue(sections.isEmpty())
    }

    @Test
    fun groupNotesByTimeline_places_pinned_notes_into_pinned_section() {
        val now = System.currentTimeMillis()
        val pinnedNote1 = createNote(1, "Sabit 1", now, isPinned = true)
        val normalNote1 = createNote(2, "Normal 1", now, isPinned = false)

        val sections = NoteTimelineGrouper.groupNotesByTimeline(listOf(pinnedNote1, normalNote1), now)

        assertEquals(2, sections.size)
        assertEquals(TimelineBucket.PINNED.name, sections[0].sectionKey)
        assertEquals(1, sections[0].items.size)
        assertEquals(1L, sections[0].items[0].id)

        assertEquals(TimelineBucket.TODAY.name, sections[1].sectionKey)
        assertEquals(1, sections[1].items.size)
        assertEquals(2L, sections[1].items[0].id)
    }

    @Test
    fun getTimelineBucketForTimestamp_correctly_categorizes_today_and_yesterday() {
        // Referans: 15 Ekim 2026 14:00
        val cal = Calendar.getInstance(Locale("tr", "TR")).apply {
            set(2026, Calendar.OCTOBER, 15, 14, 0, 0)
        }
        val refTime = cal.timeInMillis

        // Bugün 10:00
        cal.set(2026, Calendar.OCTOBER, 15, 10, 0, 0)
        assertEquals(TimelineBucket.TODAY, NoteTimelineGrouper.getTimelineBucketForTimestamp(cal.timeInMillis, refTime))

        // Dün 22:00
        cal.set(2026, Calendar.OCTOBER, 14, 22, 0, 0)
        assertEquals(TimelineBucket.YESTERDAY, NoteTimelineGrouper.getTimelineBucketForTimestamp(cal.timeInMillis, refTime))

        // 3 ay önce (Temmuz 2026)
        cal.set(2026, Calendar.JULY, 1, 10, 0, 0)
        assertEquals(TimelineBucket.OLDER, NoteTimelineGrouper.getTimelineBucketForTimestamp(cal.timeInMillis, refTime))
    }

    @Test
    fun groupNotesByCategory_groups_by_category_and_handles_uncategorized() {
        val catWork = Category(id = 10L, name = "İş", color = 0xFF00FF.toInt())
        val catPersonal = Category(id = 20L, name = "Kişisel", color = 0x00FF00.toInt())

        val note1 = createNote(1, "İş Notu", 1000L, categoryId = 10L)
        val note2 = createNote(2, "Kişisel Not", 2000L, categoryId = 20L)
        val note3 = createNote(3, "Kategorisiz Not", 3000L, categoryId = null)
        val note4 = createNote(4, "Bilinmeyen Kategori Not", 4000L, categoryId = 999L)

        val sections = NoteTimelineGrouper.groupNotesByCategory(
            notes = listOf(note1, note2, note3, note4),
            categories = listOf(catWork, catPersonal)
        )

        assertEquals(3, sections.size)

        // İş kategorisi
        val workSec = sections.first { it.sectionKey == "cat_10" }
        assertEquals("İş", workSec.title)
        assertEquals(1, workSec.items.size)

        // Kişisel kategorisi
        val persSec = sections.first { it.sectionKey == "cat_20" }
        assertEquals("Kişisel", persSec.title)
        assertEquals(1, persSec.items.size)

        // Kategorisiz (hem null hem de 999 id'li kategori)
        val uncatSec = sections.first { it.sectionKey == "uncategorized" }
        assertEquals("Kategorisiz", uncatSec.title)
        assertEquals(2, uncatSec.items.size)
    }

    @Test
    fun getEffectiveTimestamp_prefers_updatedAt_over_timestamp() {
        val note = Note(
            id = 1L,
            title = "Test",
            content = "İçerik",
            timestamp = 1000L,
            createdAt = 500L,
            updatedAt = 2500L,
            color = 0
        )
        assertEquals(2500L, NoteTimelineGrouper.getEffectiveTimestamp(note))
    }
}
