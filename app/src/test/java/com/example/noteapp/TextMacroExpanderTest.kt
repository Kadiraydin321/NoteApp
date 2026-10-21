package com.example.noteapp

import com.example.noteapp.domain.util.SnippetCategory
import com.example.noteapp.domain.util.TextMacroExpander
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TextMacroExpanderTest {

    private fun createFixedDate(year: Int, monthZeroIndexed: Int, day: Int, hour: Int, minute: Int): Date {
        val cal = Calendar.getInstance(Locale("tr", "TR"))
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroIndexed)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    @Test
    fun expandTemplate_replaces_all_date_time_and_title_placeholders() {
        // 15 Ekim 2026, 14:30
        val testDate = createFixedDate(2026, Calendar.OCTOBER, 15, 14, 30)
        val template = "Tarih: {date}, ISO: {iso_date}, Saat: {time}, Gün: {weekday}, Başlık: {title}"

        val result = TextMacroExpander.expandTemplate(
            template = template,
            currentDate = testDate,
            noteTitle = "Sprint Planlama",
            locale = Locale("tr", "TR")
        )

        assertTrue(result.contains("Tarih: 15.10.2026"))
        assertTrue(result.contains("ISO: 2026-10-15"))
        assertTrue(result.contains("Saat: 14:30"))
        assertTrue(result.contains("Başlık: Sprint Planlama"))
        assertTrue(result.contains("Gün: Perşembe"))
    }

    @Test
    fun detectAndExpandTrigger_recognizes_valid_trigger_at_end_of_string() {
        val testDate = createFixedDate(2026, Calendar.MAY, 10, 9, 15)
        val input = "Bugün toplantı yapıldı: :bugun"

        val result = TextMacroExpander.detectAndExpandTrigger(
            textBeforeCursor = input,
            currentDate = testDate
        )

        assertNotNull(result)
        assertEquals(":bugun", result!!.trigger)
        assertEquals(6, result.triggerLength)
        assertEquals("10.05.2026", result.expandedText)
    }

    @Test
    fun detectAndExpandTrigger_returns_null_when_trigger_does_not_exist() {
        val input = "Normal metin burası :bilinmeyen"
        val result = TextMacroExpander.detectAndExpandTrigger(textBeforeCursor = input)
        assertNull(result)
    }

    @Test
    fun detectAndExpandTrigger_returns_null_for_empty_or_whitespace() {
        assertNull(TextMacroExpander.detectAndExpandTrigger(""))
        assertNull(TextMacroExpander.detectAndExpandTrigger("   "))
        assertNull(TextMacroExpander.detectAndExpandTrigger(":"))
    }

    @Test
    fun detectAndExpandTrigger_expands_callout_blocks_properly() {
        val input = "Not detayları aşağıdadır:\n:not"
        val result = TextMacroExpander.detectAndExpandTrigger(textBeforeCursor = input)

        assertNotNull(result)
        assertEquals(":not", result!!.trigger)
        assertTrue(result.expandedText.startsWith("> [!NOTE]"))
    }

    @Test
    fun applyTriggerExpansionAtCursor_inserts_expanded_text_and_updates_cursor() {
        val testDate = createFixedDate(2026, Calendar.JANUARY, 1, 12, 0)
        val text = "Toplantı tarihi: :bugun ve kararlar alındı."
        // Cursor is right after :bugun (index 23)
        val cursorIndex = text.indexOf(":bugun") + ":bugun".length

        val result = TextMacroExpander.applyTriggerExpansionAtCursor(
            fullText = text,
            cursorPosition = cursorIndex,
            currentDate = testDate
        )

        assertNotNull(result)
        val (newText, newCursor) = result!!
        assertEquals("Toplantı tarihi: 01.01.2026 ve kararlar alındı.", newText)
        assertEquals("Toplantı tarihi: 01.01.2026".length, newCursor)
    }

    @Test
    fun filterSnippets_filters_by_category_and_query() {
        val all = TextMacroExpander.getAllSnippets()
        assertTrue(all.isNotEmpty())

        // Filtre: Kategori DATE_TIME
        val dateSnippets = TextMacroExpander.filterSnippets(category = SnippetCategory.DATE_TIME)
        assertTrue(dateSnippets.all { it.category == SnippetCategory.DATE_TIME })
        assertTrue(dateSnippets.any { it.trigger == ":bugun" })

        // Filtre: Arama sorgusu "toplantı"
        val meetingSnippets = TextMacroExpander.filterSnippets(query = "toplantı")
        assertTrue(meetingSnippets.any { it.trigger == ":toplanti" })

        // Filtre: Çağrı kutuları
        val callouts = TextMacroExpander.filterSnippets(category = SnippetCategory.CALLOUT)
        assertTrue(callouts.size >= 4)
    }
}
