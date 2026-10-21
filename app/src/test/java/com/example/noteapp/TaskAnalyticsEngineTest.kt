package com.example.noteapp

import com.example.noteapp.domain.util.TaskAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAnalyticsEngineTest {

    @Test
    fun parseTasks_emptyAndNonChecklist_returnsZero() {
        val emptySummary = TaskAnalyticsEngine.parseTasks("")
        assertEquals(0, emptySummary.totalTasks)
        assertEquals(0, emptySummary.percentCompleted)

        val nonChecklist = TaskAnalyticsEngine.parseTasks("Düz metin paragrafı.\nİkinci satır.")
        assertEquals(0, nonChecklist.totalTasks)
    }

    @Test
    fun parseTasks_mixedChecklist_calculatesCorrectStatistics() {
        val content = """
            # Görevlerim
            - [ ] Alışveriş yap
            - [x] Faturayı öde
            * [ ] Kitap oku
            + [x] Egzersiz tamamlandı
        """.trimIndent()

        val summary = TaskAnalyticsEngine.parseTasks(content)

        assertEquals(4, summary.totalTasks)
        assertEquals(2, summary.completedTasks)
        assertEquals(2, summary.openTasks)
        assertEquals(50, summary.percentCompleted)
        assertEquals("Alışveriş yap", summary.items[0].text)
        assertFalse(summary.items[0].isChecked)
        assertTrue(summary.items[1].isChecked)
    }

    @Test
    fun sortCompletedToBottom_movesCheckedTasksDown() {
        val content = """
            Giriş Notu
            - [x] Görev A (Bitti)
            - [ ] Görev B (Kalan)
            - [x] Görev C (Bitti)
            - [ ] Görev D (Kalan)
            Son Not
        """.trimIndent()

        val sorted = TaskAnalyticsEngine.sortCompletedToBottom(content)
        val lines = sorted.lines()

        assertEquals("Giriş Notu", lines[0])
        assertTrue(lines[1].contains("Görev B"))
        assertTrue(lines[2].contains("Görev D"))
        assertTrue(lines[3].contains("Görev A"))
        assertTrue(lines[4].contains("Görev C"))
        assertEquals("Son Not", lines[5])
    }

    @Test
    fun setAllTasksState_updatesAllCheckboxes() {
        val content = """
            - [ ] Madde 1
            - [x] Madde 2
        """.trimIndent()

        // Hepsini tamamla
        val allChecked = TaskAnalyticsEngine.setAllTasksState(content, checked = true)
        assertTrue(allChecked.lines().all { it.contains("- [x]") })

        // Hepsini sıfırla
        val allUnchecked = TaskAnalyticsEngine.setAllTasksState(allChecked, checked = false)
        assertTrue(allUnchecked.lines().all { it.contains("- [ ]") })
    }

    @Test
    fun removeCompletedTasks_removesOnlyCheckedLines() {
        val content = """
            Başlık
            - [ ] Açık Görev 1
            - [x] Tamamlanan Görev 2
            - [ ] Açık Görev 3
            - [x] Tamamlanan Görev 4
            Kapanış metni
        """.trimIndent()

        val cleaned = TaskAnalyticsEngine.removeCompletedTasks(content)

        assertTrue(cleaned.contains("Başlık"))
        assertTrue(cleaned.contains("Açık Görev 1"))
        assertTrue(cleaned.contains("Açık Görev 3"))
        assertTrue(cleaned.contains("Kapanış metni"))
        assertFalse(cleaned.contains("Tamamlanan Görev 2"))
        assertFalse(cleaned.contains("Tamamlanan Görev 4"))
    }

    @Test
    fun extractOpenTasksAsPlainText_formatsCleanBulletPoints() {
        val content = """
            - [ ] Süt al
            - [x] Ekmek al
            - [ ] Yumurta al
        """.trimIndent()

        val plain = TaskAnalyticsEngine.extractOpenTasksAsPlainText(content)

        assertEquals("• Süt al\n• Yumurta al", plain)
    }
}
