package com.example.noteapp.domain.util

import java.util.UUID

/**
 * Görev öncelik derecesi.
 */
enum class TaskPriority(val title: String, val badge: String) {
    HIGH("Yüksek", "🔴"),
    MEDIUM("Orta", "🟡"),
    NORMAL("Normal", "⚪")
}

/**
 * Görev panosu görevi modeli.
 */
data class BoardTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean,
    val originalLineIndex: Int,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val tags: List<String> = emptyList()
)

/**
 * Görev panosu durum modeli.
 */
data class TaskBoardState(
    val pendingTasks: List<BoardTask>,
    val completedTasks: List<BoardTask>,
    val totalTaskCount: Int,
    val completionPercentage: Int
)

/**
 * Not İçi Etkileşimli Görev & Kontrol Listesi Panosu Motoru.
 * Markdown içerisindeki kontrol listesi satırlarını (- [ ] ve - [x]) ayrıştırır,
 * öncelik ve etiketleri tespit eder, görev durumlarını güncellerken notun geri kalan
 * içeriğini (başlıklar, kod blokları, paragraflar) eksiksiz muhafaza eder.
 */
object TaskBoardEngine {

    private val CHECKBOX_REGEX = Regex("^(\\s*)[-*+]\\s*\\[([ xX])\\]\\s*(.*)$")
    private val TAG_REGEX = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")

    /**
     * Markdown metnini iki sütunlu görev panosu durumuna dönüştürür.
     */
    fun parseContentToBoard(content: String): TaskBoardState {
        if (content.isBlank()) {
            return TaskBoardState(emptyList(), emptyList(), 0, 0)
        }

        val lines = content.lines()
        val pending = mutableListOf<BoardTask>()
        val completed = mutableListOf<BoardTask>()

        lines.forEachIndexed { index, line ->
            val match = CHECKBOX_REGEX.find(line)
            if (match != null) {
                val mark = match.groupValues[2]
                val taskContent = match.groupValues[3].trim()
                val isDone = mark.equals("x", ignoreCase = true)
                val priority = detectPriority(taskContent)
                val tags = TAG_REGEX.findAll(taskContent).map { it.groupValues[1] }.toList()

                val task = BoardTask(
                    id = "line_$index",
                    text = cleanTaskText(taskContent),
                    isCompleted = isDone,
                    originalLineIndex = index,
                    priority = priority,
                    tags = tags
                )

                if (isDone) {
                    completed.add(task)
                } else {
                    pending.add(task)
                }
            }
        }

        val total = pending.size + completed.size
        val percentage = if (total > 0) ((completed.size.toFloat() / total.toFloat()) * 100).toInt() else 0

        return TaskBoardState(
            pendingTasks = pending,
            completedTasks = completed,
            totalTaskCount = total,
            completionPercentage = percentage
        )
    }

    /**
     * Görev metnindeki öncelik belirteçlerini tespit eder.
     */
    fun detectPriority(text: String): TaskPriority {
        val lower = text.lowercase()
        return when {
            lower.contains("[!high]") || lower.contains("#p1") || lower.contains("#önemli") || lower.contains("🔴") || lower.contains("acil") -> TaskPriority.HIGH
            lower.contains("[!med]") || lower.contains("#p2") || lower.contains("#orta") || lower.contains("🟡") -> TaskPriority.MEDIUM
            else -> TaskPriority.NORMAL
        }
    }

    /**
     * Görev metninden teknik etiketleri ve işaretleri temizleyerek gösterim metni oluşturur.
     */
    fun cleanTaskText(text: String): String {
        return text
            .replace("[!high]", "")
            .replace("[!med]", "")
            .replace("[!low]", "")
            .replace("🔴", "")
            .replace("🟡", "")
            .replace("⚪", "")
            .trim()
    }

    /**
     * Belirli bir görevin tamamlanma durumunu değiştirir ve güncellenmiş Markdown metnini döndürür.
     */
    fun toggleTaskStatus(content: String, targetTask: BoardTask): String {
        val lines = content.lines().toMutableList()
        val lineIdx = targetTask.originalLineIndex

        if (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val match = CHECKBOX_REGEX.find(line)
            if (match != null) {
                val indent = match.groupValues[1]
                val taskContent = match.groupValues[3]
                val newMark = if (targetTask.isCompleted) " " else "x"
                lines[lineIdx] = "$indent- [$newMark] $taskContent"
                return lines.joinToString("\n")
            }
        }
        return content
    }

    /**
     * Notun sonuna yeni bir görev maddesi ekler.
     */
    fun addNewTask(content: String, taskTitle: String, priority: TaskPriority = TaskPriority.NORMAL): String {
        val cleanTitle = taskTitle.trim()
        if (cleanTitle.isBlank()) return content

        val prioritySuffix = when (priority) {
            TaskPriority.HIGH -> " 🔴"
            TaskPriority.MEDIUM -> " 🟡"
            TaskPriority.NORMAL -> ""
        }
        val newLine = "- [ ] $cleanTitle$prioritySuffix"

        return if (content.isBlank()) {
            newLine
        } else if (content.endsWith("\n\n")) {
            "$content$newLine\n"
        } else if (content.endsWith("\n")) {
            "$content$newLine\n"
        } else {
            "$content\n$newLine\n"
        }
    }

    /**
     * Tamamlanmış tüm görevleri listenin en altına taşır (Markdown içeriğinde).
     */
    fun pushCompletedTasksToBottom(content: String): String {
        val lines = content.lines()
        val pendingTaskLines = mutableListOf<String>()
        val completedTaskLines = mutableListOf<String>()
        val otherLines = mutableListOf<Pair<Int, String>>()

        lines.forEachIndexed { index, line ->
            val match = CHECKBOX_REGEX.find(line)
            if (match != null) {
                val mark = match.groupValues[2]
                if (mark.equals("x", ignoreCase = true)) {
                    completedTaskLines.add(line)
                } else {
                    pendingTaskLines.add(line)
                }
            } else {
                otherLines.add(index to line)
            }
        }

        if (completedTaskLines.isEmpty()) return content

        val resultLines = mutableListOf<String>()
        var taskInserted = false

        lines.forEach { line ->
            val isCheckbox = CHECKBOX_REGEX.containsMatchIn(line)
            if (isCheckbox) {
                if (!taskInserted) {
                    resultLines.addAll(pendingTaskLines)
                    resultLines.addAll(completedTaskLines)
                    taskInserted = true
                }
            } else {
                resultLines.add(line)
            }
        }

        return resultLines.joinToString("\n")
    }
}
