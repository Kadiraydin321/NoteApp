package com.example.noteapp.domain.util

/**
 * Kontrol listesindeki tek bir görev maddesi.
 */
data class TaskItem(
    val rawLine: String,
    val text: String,
    val isChecked: Boolean,
    val originalIndex: Int
)

/**
 * Görev listesi özet istatistikleri.
 */
data class TaskSummary(
    val totalTasks: Int,
    val completedTasks: Int,
    val openTasks: Int,
    val percentCompleted: Int,
    val items: List<TaskItem>
)

/**
 * Akıllı Görev ve Yapılacaklar Listesi Analitiği & Düzenleme Motoru.
 * Not içeriğindeki checklist (`- [ ]`, `- [x]`) maddelerini tespit eder,
 * tamamlananları sona taşıma, toplu durum değiştirme ve temizleme işlemlerini yönetir.
 */
object TaskAnalyticsEngine {

    // Desteklenen liste işaretleri: - [ ], - [x], * [ ], * [x], + [ ], + [x]
    private val TASK_REGEX = Regex("^[ \\t]*([-*+]\\s*\\[([ xX])\\])\\s*(.*)$")

    /**
     * Metindeki tüm görevleri ayrıştırır ve özet istatistikleri çıkarır.
     */
    fun parseTasks(content: String): TaskSummary {
        if (content.isBlank()) {
            return TaskSummary(0, 0, 0, 0, emptyList())
        }

        val lines = content.lines()
        val items = mutableListOf<TaskItem>()

        lines.forEachIndexed { index, line ->
            val match = TASK_REGEX.find(line)
            if (match != null) {
                val mark = match.groupValues[2]
                val text = match.groupValues[3]
                val isChecked = mark.equals("x", ignoreCase = true)
                items.add(
                    TaskItem(
                        rawLine = line,
                        text = text,
                        isChecked = isChecked,
                        originalIndex = index
                    )
                )
            }
        }

        val total = items.size
        val completed = items.count { it.isChecked }
        val open = total - completed
        val percent = if (total > 0) ((completed.toFloat() / total.toFloat()) * 100f).toInt() else 0

        return TaskSummary(
            totalTasks = total,
            completedTasks = completed,
            openTasks = open,
            percentCompleted = percent,
            items = items
        )
    }

    /**
     * Tamamlanan maddeleri (`- [x]`) ilgili görev bloğunun veya notun en altına taşır,
     * açık maddeleri (`- [ ]`) üstte tutar. Blok dışı paragrafların yerini korur.
     */
    fun sortCompletedToBottom(content: String): String {
        if (content.isBlank()) return content

        val lines = content.lines()
        val result = mutableListOf<String>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (TASK_REGEX.matches(line)) {
                // Görev bloğunun başlangıcı
                val taskBlock = mutableListOf<String>()
                while (index < lines.size && (TASK_REGEX.matches(lines[index]) || lines[index].isBlank())) {
                    taskBlock.add(lines[index])
                    index++
                }

                // Görev bloğunu sırala: Açık olanlar önce, tamamlananlar sonra
                val openTasks = mutableListOf<String>()
                val completedTasks = mutableListOf<String>()
                val emptyLines = mutableListOf<String>()

                for (blockLine in taskBlock) {
                    val match = TASK_REGEX.find(blockLine)
                    if (match != null) {
                        val isChecked = match.groupValues[2].equals("x", ignoreCase = true)
                        if (isChecked) {
                            completedTasks.add(blockLine)
                        } else {
                            openTasks.add(blockLine)
                        }
                    } else if (blockLine.isBlank()) {
                        emptyLines.add(blockLine)
                    }
                }

                result.addAll(openTasks)
                result.addAll(completedTasks)
                result.addAll(emptyLines)
            } else {
                result.add(line)
                index++
            }
        }

        return result.joinToString("\n")
    }

    /**
     * Metindeki tüm görev maddelerini topluca tamamlandı (`- [x]`) veya açık (`- [ ]`) yapar.
     */
    fun setAllTasksState(content: String, checked: Boolean): String {
        if (content.isBlank()) return content
        val newMark = if (checked) "x" else " "

        return content.lines().joinToString("\n") { line ->
            val match = TASK_REGEX.find(line)
            if (match != null) {
                line.replaceFirst(Regex("\\[([ xX])\\]"), "[$newMark]")
            } else {
                line
            }
        }
    }

    /**
     * Tamamlanan (`- [x]`) tüm görev satırlarını metinden temizler.
     */
    fun removeCompletedTasks(content: String): String {
        if (content.isBlank()) return content

        val filteredLines = content.lines().filterNot { line ->
            val match = TASK_REGEX.find(line)
            match != null && match.groupValues[2].equals("x", ignoreCase = true)
        }
        return filteredLines.joinToString("\n")
    }

    /**
     * Kalan (açık) görevleri temiz liste metni olarak döndürür (Panoya kopyalama için).
     */
    fun extractOpenTasksAsPlainText(content: String): String {
        val summary = parseTasks(content)
        val openItems = summary.items.filterNot { it.isChecked }
        if (openItems.isEmpty()) return ""

        return openItems.joinToString("\n") { "• ${it.text.trim()}" }
    }
}
