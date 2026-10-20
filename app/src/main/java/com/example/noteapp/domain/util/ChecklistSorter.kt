package com.example.noteapp.domain.util

/**
 * Not içeriğindeki Markdown kontrol listesi maddelerini (- [ ] / - [x])
 * analiz eden, tamamlananları sona taşıyan ve toplu durum güncellemeleri yapan motor.
 */
object ChecklistSorter {

    private fun isCheckedItem(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") ||
                trimmed.startsWith("* [x]") || trimmed.startsWith("* [X]") ||
                trimmed.startsWith("[x]") || trimmed.startsWith("[X]")
    }

    private fun isUncheckedItem(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") ||
                trimmed.startsWith("[ ]")
    }

    private fun isChecklistItem(line: String): Boolean {
        return isCheckedItem(line) || isUncheckedItem(line)
    }

    /**
     * Not içerisindeki bitişik kontrol listesi bloklarını bulur ve
     * blok içerisindeki tamamlanmış (- [x]) maddeleri bloğun en sonuna taşır.
     * Paragraflar, başlıklar ve boşluklar yerini korur.
     */
    fun moveCheckedToBottom(content: String): String {
        if (!content.contains("[ ]") && !content.contains("[x]") && !content.contains("[X]")) {
            return content
        }

        val lines = content.lines()
        val result = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (isChecklistItem(line)) {
                // Bitişik checklist bloğunu topla
                val blockUnchecked = mutableListOf<String>()
                val blockChecked = mutableListOf<String>()

                while (i < lines.size && isChecklistItem(lines[i])) {
                    val current = lines[i]
                    if (isCheckedItem(current)) {
                        blockChecked.add(current)
                    } else {
                        blockUnchecked.add(current)
                    }
                    i++
                }

                // Önce tamamlanmamışlar, sonra tamamlananlar
                result.addAll(blockUnchecked)
                result.addAll(blockChecked)
            } else {
                result.add(line)
                i++
            }
        }

        return result.joinToString("\n")
    }

    /**
     * Tüm kontrol listesi maddelerini topluca işaretler (checkAll=true)
     * veya tüm işaretleri kaldırır (checkAll=false).
     */
    fun toggleAllItems(content: String, checkAll: Boolean): String {
        val lines = content.lines()
        val updated = lines.map { line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)

            when {
                checkAll && isUncheckedItem(line) -> {
                    val rest = when {
                        trimmed.startsWith("- [ ]") -> trimmed.removePrefix("- [ ]")
                        trimmed.startsWith("* [ ]") -> trimmed.removePrefix("* [ ]")
                        else -> trimmed.removePrefix("[ ]")
                    }
                    "$indent- [x]$rest"
                }
                !checkAll && isCheckedItem(line) -> {
                    val rest = when {
                        trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> trimmed.substring(5)
                        trimmed.startsWith("* [x]") || trimmed.startsWith("* [X]") -> trimmed.substring(5)
                        else -> trimmed.substring(3)
                    }
                    "$indent- [ ]$rest"
                }
                else -> line
            }
        }
        return updated.joinToString("\n")
    }

    /**
     * Tamamlanmış olan (- [x]) maddeleri metinden temizler/siler.
     */
    fun removeCompletedItems(content: String): String {
        val lines = content.lines()
        val filtered = lines.filterNot { isCheckedItem(it) }
        return filtered.joinToString("\n")
    }
}
