package com.example.noteapp

import com.example.noteapp.domain.model.NoteTemplate
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class TemplateInterpolationTest {

    @Test
    fun testVariableInterpolation() {
        val template = NoteTemplate(
            id = "test_1",
            title = "Toplantı",
            description = "Test açıklaması",
            iconEmoji = "📅",
            defaultTitle = "Toplantı: {TITLE} ({DATE})",
            contentTemplate = "# {TITLE}\nTarih: {DATE} {TIME} ({DAY})\n- [ ] Aksiyon"
        )

        val now = Date()
        val trLocale = Locale("tr", "TR")
        val dateFormatted = SimpleDateFormat("d MMMM yyyy", trLocale).format(now)
        val timeFormatted = SimpleDateFormat("HH:mm", trLocale).format(now)
        val dayFormatted = SimpleDateFormat("EEEE", trLocale).format(now)

        val userTitleInput = "Proje Kickoff"
        val finalTitle = template.defaultTitle
            .replace("{DATE}", dateFormatted)
            .replace("{TIME}", timeFormatted)
            .replace("{DAY}", dayFormatted)
            .replace("{TITLE}", userTitleInput)

        val finalContent = template.contentTemplate
            .replace("{DATE}", dateFormatted)
            .replace("{TIME}", timeFormatted)
            .replace("{DAY}", dayFormatted)
            .replace("{TITLE}", userTitleInput)

        assertTrue(finalTitle.contains("Proje Kickoff"))
        assertTrue(finalTitle.contains(dateFormatted))
        assertTrue(finalContent.contains("# Proje Kickoff"))
        assertTrue(finalContent.contains(timeFormatted))
        assertTrue(finalContent.contains(dayFormatted))
        assertTrue(finalContent.contains("- [ ] Aksiyon"))
    }

    @Test
    fun testDefaultTitleFallbackWhenEmpty() {
        val template = NoteTemplate(
            id = "test_2",
            title = "Alışveriş",
            description = "Market listesi",
            iconEmoji = "🛒",
            defaultTitle = "Alışveriş - {DATE}",
            contentTemplate = "Liste:\n- [ ] Süt"
        )

        val dateFormatted = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR")).format(Date())
        val finalTitle = template.defaultTitle.replace("{DATE}", dateFormatted)

        assertEquals("Alışveriş - $dateFormatted", finalTitle)
    }
}
