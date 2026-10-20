package com.example.noteapp

import com.example.noteapp.domain.util.ChecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistParserTest {

    @Test
    fun parse_withNoChecklist_returnsNull() {
        val content = "Just a normal note with text\nAnother line here."
        val stats = ChecklistParser.parse(content)
        assertNull(stats)
    }

    @Test
    fun parse_withMixedChecklistItems_calculatesCorrectCounts() {
        val content = """
            # Alışveriş Listesi
            - [x] Süt al
            - [ ] Ekmek al
            - [X] Yumurta al
            - [ ] Kahve al
            * [x] Çay al
            Düz metin satırı
        """.trimIndent()

        val stats = ChecklistParser.parse(content)
        assertNotNull(stats)
        stats?.let {
            assertEquals(5, it.totalCount)
            assertEquals(3, it.completedCount)
            assertEquals(0.6f, it.progress, 0.001f)
            assertEquals(60, it.percent)
            assertEquals(false, it.isAllCompleted)
        }
    }

    @Test
    fun parse_allCompleted_returnsIsAllCompletedTrue() {
        val content = """
            - [x] Görev 1
            - [x] Görev 2
        """.trimIndent()

        val stats = ChecklistParser.parse(content)
        assertNotNull(stats)
        assertTrue(stats!!.isAllCompleted)
        assertEquals(100, stats.percent)
    }

    @Test
    fun parse_noneCompleted_returnsZeroPercent() {
        val content = """
            [ ] Yapılacak 1
            [ ] Yapılacak 2
            [ ] Yapılacak 3
        """.trimIndent()

        val stats = ChecklistParser.parse(content)
        assertNotNull(stats)
        assertEquals(0, stats!!.completedCount)
        assertEquals(3, stats.totalCount)
        assertEquals(0, stats.percent)
    }
}
