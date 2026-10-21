package com.example.noteapp

import com.example.noteapp.domain.util.TaskBoardEngine
import com.example.noteapp.domain.util.TaskPriority
import org.junit.Assert.*
import org.junit.Test

class TaskBoardEngineTest {

    @Test
    fun parseContentToBoard_separatesPendingAndCompletedTasks() {
        val markdown = """
            # Haftalık Plan
            Aşağıdaki maddeler incelenecek:
            
            - [ ] Veritabanı şemasını güncelle #backend 🔴
            - [x] UI renk paleti tasarlandı #tasarım
            - [ ] API endpoint testlerini yaz
            - [x] Gradle kütüphanelerini güncelle
            
            Not: Yarın toplantı var.
        """.trimIndent()

        val board = TaskBoardEngine.parseContentToBoard(markdown)

        assertEquals(2, board.pendingTasks.size)
        assertEquals(2, board.completedTasks.size)
        assertEquals(4, board.totalTaskCount)
        assertEquals(50, board.completionPercentage)

        // Öncelik ve etiket kontrolü
        val highPriorityTask = board.pendingTasks.first { it.text.contains("Veritabanı") }
        assertEquals(TaskPriority.HIGH, highPriorityTask.priority)
        assertTrue(highPriorityTask.tags.contains("backend"))
    }

    @Test
    fun toggleTaskStatus_updatesCheckboxMarkAccurately() {
        val markdown = "- [ ] Görev 1\n- [x] Görev 2"
        val board = TaskBoardEngine.parseContentToBoard(markdown)

        val task1 = board.pendingTasks[0]
        val updated1 = TaskBoardEngine.toggleTaskStatus(markdown, task1)
        assertTrue(updated1.contains("- [x] Görev 1"))

        val board2 = TaskBoardEngine.parseContentToBoard(updated1)
        val task2 = board2.completedTasks.first { it.originalLineIndex == 1 }
        val updated2 = TaskBoardEngine.toggleTaskStatus(updated1, task2)
        assertTrue(updated2.contains("- [ ] Görev 2"))
    }

    @Test
    fun addNewTask_appendsTaskWithPriority() {
        val initial = "# Başlık\nParagraf metni"
        val withNew = TaskBoardEngine.addNewTask(initial, "Yeni acil iş", TaskPriority.HIGH)

        assertTrue(withNew.contains("- [ ] Yeni acil iş 🔴"))
        assertTrue(withNew.startsWith("# Başlık"))
    }

    @Test
    fun pushCompletedTasksToBottom_movesCompletedItemsBelowPending() {
        val markdown = """
            - [x] Tamamlandı 1
            - [ ] Bekliyor 1
            - [x] Tamamlandı 2
            - [ ] Bekliyor 2
        """.trimIndent()

        val reordered = TaskBoardEngine.pushCompletedTasksToBottom(markdown)
        val lines = reordered.lines()

        assertEquals("- [ ] Bekliyor 1", lines[0])
        assertEquals("- [ ] Bekliyor 2", lines[1])
        assertEquals("- [x] Tamamlandı 1", lines[2])
        assertEquals("- [x] Tamamlandı 2", lines[3])
    }
}
