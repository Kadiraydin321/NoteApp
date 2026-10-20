package com.example.noteapp.domain.util

data class ChecklistStats(
    val completedCount: Int,
    val totalCount: Int
) {
    val progress: Float
        get() = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    val percent: Int
        get() = (progress * 100).toInt()

    val isAllCompleted: Boolean
        get() = totalCount > 0 && completedCount == totalCount
}

object ChecklistParser {
    fun parse(content: String): ChecklistStats? {
        if (!content.contains("[ ]") && !content.contains("[x]") && !content.contains("[X]")) {
            return null
        }
        var total = 0
        var done = 0
        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            if (trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") ||
                trimmed.startsWith("* [x]") || trimmed.startsWith("* [X]") ||
                trimmed.startsWith("[x]") || trimmed.startsWith("[X]")
            ) {
                total++
                done++
            } else if (
                trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") ||
                trimmed.startsWith("[ ]")
            ) {
                total++
            }
        }
        return if (total > 0) ChecklistStats(completedCount = done, totalCount = total) else null
    }
}
